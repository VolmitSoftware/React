package art.arcane.react.mod;

import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.mod.mixin.ChunkMapAccess;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.HostEvent;
import art.arcane.react.platform.HostListeners;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.PlatformLog;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Chunk access and chunk lifecycle on mods. Loaded chunks come from the chunk map's visible holders, read on the
 * server thread, and from an index the chunk mixins keep for reads from other threads.
 */
public final class ModChunkHost implements ChunkHost {
    private static final long SERVER_WAIT_MS = 2_000L;
    private static final HostListeners<ChunkEvent> CHUNK_LOADS = new HostListeners<>();
    private static final HostListeners<ChunkEvent> CHUNK_UNLOADS = new HostListeners<>();
    private static final HostListeners<WorldUnload> WORLD_UNLOADS = new HostListeners<>();
    private static final Map<UUID, LongOpenHashSet> LOADED = new ConcurrentHashMap<>();
    private final MinecraftServer server;
    private final ModChunkSamples samples;
    private final Map<UUID, Cursor> cursors = new HashMap<>();
    private int worldRotation;

    ModChunkHost(MinecraftServer server, ModChunkSamples samples) {
        this.server = server;
        this.samples = samples;
    }

    @Override
    public Optional<ReactWorld> world(String worldKey) {
        if (worldKey == null || worldKey.isBlank()) {
            return Optional.empty();
        }
        String wanted = worldKey.trim();
        for (ServerLevel level : server.getAllLevels()) {
            if (ModReactWorld.nameOf(level).equals(wanted)) {
                return Optional.of(new ModReactWorld(level));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ReactWorld> world(UUID worldId) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        return level == null ? Optional.empty() : Optional.of(new ModReactWorld(level));
    }

    @Override
    public String worldKey(ReactWorld world) {
        return ModReactWorld.nameOf(level(world));
    }

    @Override
    public boolean isLoaded(ReactWorld world, int chunkX, int chunkZ) {
        return level(world).getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    @Override
    public boolean isGenerated(ReactWorld world, int chunkX, int chunkZ) {
        return ModChunkGeneration.generated(level(world), chunkX, chunkZ);
    }

    @Override
    public boolean unload(ReactWorld world, int chunkX, int chunkZ) {
        return unloadNow(level(world), chunkX, chunkZ);
    }

    /**
     * Saves and unloads the chunk now when no ticket keeps it at full status, the way Paper's unload request purges
     * pending unloads. False while something, such as a nearby player, still holds it, or while saving is off.
     */
    static boolean unloadNow(ServerLevel level, int chunkX, int chunkZ) {
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
            return true;
        }
        if (level.noSave()) {
            return false;
        }
        ChunkHolder holder = holders(level).get(VanillaApi.pack(chunkX, chunkZ));
        if (holder != null && holder.getTicketLevel() <= ChunkLevel.byStatus(FullChunkStatus.FULL)) {
            return false;
        }
        ((ChunkMapAccess) level.getChunkSource().chunkMap).react$processUnloads(() -> true);
        return true;
    }

    @Override
    public Object loadedChunk(ReactWorld world, int chunkX, int chunkZ) {
        return level(world).getChunkSource().getChunkNow(chunkX, chunkZ);
    }

    @Override
    public CompletableFuture<Object> load(ReactWorld world, int chunkX, int chunkZ, boolean generate) {
        ServerLevel level = level(world);
        if (generate) {
            return loadNow(level, chunkX, chunkZ);
        }
        CompletableFuture<Object> loading = new CompletableFuture<>();
        ModChunkGeneration.read(level, chunkX, chunkZ).whenComplete((Boolean generated, Throwable failure) -> {
            if (failure != null) {
                loading.completeExceptionally(failure);
            } else if (!Boolean.TRUE.equals(generated)) {
                loading.complete(null);
            } else {
                level.getServer().execute(() -> loadNow(level, chunkX, chunkZ).whenComplete((Object chunk, Throwable loadFailure) -> {
                    if (loadFailure != null) {
                        loading.completeExceptionally(loadFailure);
                    } else {
                        loading.complete(chunk);
                    }
                }));
            }
        });
        return loading;
    }

    private static CompletableFuture<Object> loadNow(ServerLevel level, int chunkX, int chunkZ) {
        return VanillaForms.loadFullChunk(level, chunkX, chunkZ).thenApply((LevelChunk chunk) -> chunk);
    }

    @Override
    public void warm(Object chunk, boolean touchSnapshot) {
        LevelChunk level = (LevelChunk) chunk;
        if (touchSnapshot) {
            for (LevelChunkSection section : level.getSections()) {
                if (!section.hasOnlyAir()) {
                    section.getStates().copy();
                }
            }
        }
        if (level.getLevel() instanceof ServerLevel serverLevel) {
            ChunkPos pos = level.getPos();
            int minX = VanillaApi.chunkX(pos) << 4;
            int minZ = VanillaApi.chunkZ(pos) << 4;
            serverLevel.getEntities((Entity) null, new AABB(minX, VanillaApi.minY(serverLevel), minZ, minX + 16,
                    VanillaApi.maxY(serverLevel) + 1, minZ + 16), (Entity entity) -> true);
        }
    }

    @Override
    public boolean ownsChunk(ReactWorld world, int chunkX, int chunkZ) {
        return server.isSameThread();
    }

    @Override
    public boolean isLoadedIndexReady() {
        return true;
    }

    @Override
    public LoadedChunkCursor openLoadedCursor() {
        List<ServerLevel> levels = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            levels.add(level);
        }
        levels.sort(Comparator.comparing((ServerLevel level) -> ModReactWorld.idOf(level).toString()));
        return cursor(levels);
    }

    @Override
    public LoadedChunkCursor openLoadedCursor(UUID worldId) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        return level == null ? LoadedChunkCursor.EMPTY : cursor(List.of(level));
    }

    @Override
    public List<LoadedChunkCoordinate> loadedInRadius(UUID worldId, int centerX, int centerZ, int radius) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        if (level == null) {
            return List.of();
        }
        long safeRadius = Math.max(0, radius);
        long radiusSquared = safeRadius * safeRadius;
        List<LoadedChunkCoordinate> result = new ArrayList<>();
        for (long key : loaded(level)) {
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            long dx = (long) chunkX - centerX;
            long dz = (long) chunkZ - centerZ;
            if ((dx * dx) + (dz * dz) <= radiusSquared) {
                result.add(new LoadedChunkCoordinate(chunkX, chunkZ));
            }
        }
        return result;
    }

    @Override
    public List<SampledChunk> sampledChunks() {
        if (!samples.entityCountsFresh(System.currentTimeMillis())) {
            countEntities();
        }
        return samples.chunks();
    }

    /** Fills the per-chunk entity counts on the server thread, as Bukkit's observer always has them. */
    private void countEntities() {
        Runnable count = () -> {
            long now = System.currentTimeMillis();
            for (ServerLevel level : server.getAllLevels()) {
                samples.countEntities(level, now);
            }
        };
        if (server.isSameThread()) {
            count.run();
            return;
        }
        try {
            server.submit(count).get(SERVER_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException failure) {
            PlatformLog.warn("React could not count chunk entities: " + failure.getMessage());
        }
    }

    @Override
    public boolean mayHavePlayerNear(ReactWorld world, double x, double z, double radius) {
        double radiusSquared = radius * radius;
        for (ServerPlayer player : List.copyOf(level(world).players())) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if ((dx * dx) + (dz * dz) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private LoadedChunkCursor cursor(List<ServerLevel> levels) {
        List<ChunkRef> chunks = new ArrayList<>();
        for (ServerLevel level : levels) {
            UUID worldId = ModReactWorld.idOf(level);
            for (long key : loaded(level)) {
                chunks.add(new ChunkRef(worldId, ChunkPos.getX(key), ChunkPos.getZ(key)));
            }
        }
        return new SnapshotCursor(chunks);
    }

    /** The full chunks of a level, read on the server thread. */
    private List<Long> loaded(ServerLevel level) {
        if (server.isSameThread()) {
            return loadedNow(level);
        }
        try {
            return server.submit(() -> loadedNow(level)).get(SERVER_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException | TimeoutException unavailable) {
            return List.of();
        }
    }

    @Override
    public boolean isLoaded(UUID worldId, int chunkX, int chunkZ) {
        LongOpenHashSet chunks = worldId == null ? null : LOADED.get(worldId);
        if (chunks == null) {
            return false;
        }
        synchronized (chunks) {
            return chunks.contains(VanillaApi.pack(chunkX, chunkZ));
        }
    }

    @Override
    public synchronized List<ChunkRef> nextLoaded(int maximum) {
        if (maximum <= 0 || LOADED.isEmpty()) {
            return List.of();
        }
        List<UUID> worlds = new ArrayList<>(LOADED.keySet());
        cursors.keySet().retainAll(worlds);
        List<ChunkRef> result = new ArrayList<>(Math.min(maximum, 256));
        int worldBudget = Math.min(maximum, worlds.size());
        for (int inspected = 0; inspected < worldBudget && result.size() < maximum; inspected++) {
            UUID worldId = worlds.get(Math.floorMod(worldRotation++, worlds.size()));
            LongOpenHashSet chunks = LOADED.get(worldId);
            if (chunks != null) {
                cursors.computeIfAbsent(worldId, ignored -> new Cursor()).append(worldId, chunks, maximum - result.size(), result);
            }
        }
        return result;
    }

    @Override
    public Subscription onChunkLoad(ListenerPriority priority, Consumer<ChunkRef> listener) {
        return CHUNK_LOADS.subscribe(priority, false, event -> listener.accept(event.chunk));
    }

    @Override
    public Subscription onChunkUnload(ListenerPriority priority, Consumer<ChunkRef> listener) {
        return CHUNK_UNLOADS.subscribe(priority, false, event -> listener.accept(event.chunk));
    }

    @Override
    public Subscription onWorldUnload(ListenerPriority priority, boolean ignoreCancelled, Consumer<WorldUnload> listener) {
        return WORLD_UNLOADS.subscribe(priority, ignoreCancelled, listener);
    }

    static void clear() {
        CHUNK_LOADS.clear();
        CHUNK_UNLOADS.clear();
        WORLD_UNLOADS.clear();
    }

    public static void chunkLoaded(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }
        UUID worldId = ModReactWorld.idOf(level);
        int chunkX = VanillaApi.chunkX(chunk.getPos());
        int chunkZ = VanillaApi.chunkZ(chunk.getPos());
        LongOpenHashSet chunks = LOADED.computeIfAbsent(worldId, ignored -> new LongOpenHashSet());
        synchronized (chunks) {
            chunks.add(VanillaApi.pack(chunkX, chunkZ));
        }
        if (!CHUNK_LOADS.isEmpty()) {
            CHUNK_LOADS.dispatch(new ChunkEvent(new ChunkRef(worldId, chunkX, chunkZ)));
        }
    }

    public static void chunkUnloading(ServerLevel level, LevelChunk chunk) {
        UUID worldId = ModReactWorld.idOf(level);
        int chunkX = VanillaApi.chunkX(chunk.getPos());
        int chunkZ = VanillaApi.chunkZ(chunk.getPos());
        if (!CHUNK_UNLOADS.isEmpty()) {
            CHUNK_UNLOADS.dispatch(new ChunkEvent(new ChunkRef(worldId, chunkX, chunkZ)));
        }
        LongOpenHashSet chunks = LOADED.get(worldId);
        if (chunks != null) {
            synchronized (chunks) {
                chunks.remove(VanillaApi.pack(chunkX, chunkZ));
            }
        }
    }

    /**
     * A level closing, on server stop or when a mod removes a dimension. Listeners cannot keep it open.
     */
    public static void worldUnloading(ServerLevel level) {
        UUID worldId = ModReactWorld.idOf(level);
        if (!WORLD_UNLOADS.isEmpty()) {
            WORLD_UNLOADS.dispatch(new ModWorldUnload(worldId));
        }
        LOADED.remove(worldId);
    }

    private static List<Long> loadedNow(ServerLevel level) {
        List<Long> loaded = new ArrayList<>();
        for (ChunkHolder holder : holders(level).values()) {
            if (VanillaForms.fullChunk(holder) != null) {
                loaded.add(VanillaApi.pack(holder.getPos()));
            }
        }
        return loaded;
    }

    private static Long2ObjectLinkedOpenHashMap<ChunkHolder> holders(ServerLevel level) {
        return ((ChunkMapAccess) level.getChunkSource().chunkMap).react$visibleChunkMap();
    }

    private static ServerLevel level(ReactWorld world) {
        return (ServerLevel) world.handle();
    }

    private static final class SnapshotCursor implements LoadedChunkCursor {
        private static final int MAX_BATCH = 256;
        private final List<ChunkRef> chunks;
        private int index;

        private SnapshotCursor(List<ChunkRef> chunks) {
            this.chunks = chunks;
        }

        @Override
        public synchronized List<ChunkRef> next(int maximum) {
            int limit = Math.max(0, Math.min(maximum, MAX_BATCH));
            int end = Math.min(chunks.size(), index + limit);
            List<ChunkRef> batch = List.copyOf(chunks.subList(index, end));
            index = end;
            return batch;
        }

        @Override
        public int estimatedTotal() {
            return chunks.size();
        }
    }

    private static final class Cursor {
        private long[] snapshot = new long[0];
        private int index;

        private void append(UUID worldId, LongOpenHashSet chunks, int budget, List<ChunkRef> result) {
            if (index >= snapshot.length) {
                synchronized (chunks) {
                    snapshot = chunks.toLongArray();
                }
                index = 0;
            }
            int added = 0;
            while (index < snapshot.length && added < budget) {
                long key = snapshot[index++];
                result.add(new ChunkRef(worldId, (int) key, (int) (key >> 32)));
                added++;
            }
        }
    }

    private static final class ChunkEvent implements HostEvent {
        private final ChunkRef chunk;

        private ChunkEvent(ChunkRef chunk) {
            this.chunk = chunk;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void cancel() {
        }
    }

    private static final class ModWorldUnload implements WorldUnload {
        private final UUID worldId;
        private boolean cancelled;

        private ModWorldUnload(UUID worldId) {
            this.worldId = worldId;
        }

        @Override
        public UUID worldId() {
            return worldId;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
