package art.arcane.react.mod;

import art.arcane.react.content.feature.SharePieRenderer;
import art.arcane.react.mod.mixin.ChunkMapAccess;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.platform.PlatformLog;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The biome of every loaded chunk, sampled a few chunks at a time at sea level, as the Bukkit biome pie does. Iris
 * worlds report Iris biome names through the engine; other worlds report the vanilla biome.
 */
final class ModIrisBiomes {
    private static final int REFRESH_TICKS = 20;
    private static final int MAX_SAMPLES_PER_PASS = 32;
    private final MinecraftServer server;
    private final Map<UUID, Map<Long, String>> labels = new ConcurrentHashMap<>();
    private boolean failureLogged;

    ModIrisBiomes(MinecraftServer server) {
        this.server = server;
    }

    /** Server thread. */
    void sampleNow() {
        sample();
    }

    void clear() {
        labels.clear();
    }

    /** Chunk count by biome label for the world the map shows. */
    Map<String, Long> buckets(MapRenderTarget target) {
        UUID worldId = worldOf(target);
        Map<Long, String> chunks = worldId == null ? null : labels.get(worldId);
        Map<String, Long> counts = new LinkedHashMap<>();
        if (chunks != null) {
            for (String label : chunks.values()) {
                counts.merge(label, 1L, Long::sum);
            }
        }
        return counts;
    }

    private UUID worldOf(MapRenderTarget target) {
        if (target == null) {
            return null;
        }
        if (target.worldId() != null) {
            return target.worldId();
        }
        ServerPlayer viewer = target.viewerId() == null ? null : server.getPlayerList().getPlayer(target.viewerId());
        return viewer == null ? null : ModReactWorld.idOf(VanillaApi.level(viewer));
    }

    private void sample() {
        Map<String, Object> engines = engines();
        Set<UUID> present = new HashSet<>();
        int budget = MAX_SAMPLES_PER_PASS;
        for (ServerLevel level : server.getAllLevels()) {
            UUID worldId = ModReactWorld.idOf(level);
            present.add(worldId);
            Map<Long, String> chunks = labels.computeIfAbsent(worldId, (UUID ignored) -> new ConcurrentHashMap<>());
            Set<Long> loaded = loadedChunks(level);
            chunks.keySet().retainAll(loaded);
            Object engine = engines.get(ModReactWorld.nameOf(level));
            for (long key : loaded) {
                if (budget <= 0) {
                    break;
                }
                if (!chunks.containsKey(key)) {
                    String label = label(level, engine, ChunkPos.getX(key), ChunkPos.getZ(key));
                    if (label != null) {
                        chunks.put(key, label);
                    }
                    budget--;
                }
            }
        }
        labels.keySet().retainAll(present);
    }

    private static Set<Long> loadedChunks(ServerLevel level) {
        Set<Long> loaded = new HashSet<>();
        for (ChunkHolder holder : ((ChunkMapAccess) level.getChunkSource().chunkMap).react$visibleChunkMap().values()) {
            if (VanillaForms.fullChunk(holder) != null) {
                loaded.add(VanillaApi.pack(holder.getPos()));
            }
        }
        return loaded;
    }

    /** Null while Iris is still loading the chunk's saved biome data, so the chunk is sampled again next pass. */
    private String label(ServerLevel level, Object engine, int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        int y = level.getSeaLevel();
        if (engine != null) {
            String irisBiome = irisBiome(engine, x, y, z);
            return irisBiome == null || irisBiome.isBlank() ? null : SharePieRenderer.displayName(irisBiome);
        }
        return SharePieRenderer.displayName(level.getBiome(new BlockPos(x, y, z)).unwrapKey()
                .map((ResourceKey<Biome> key) -> lastSegment(VanillaApi.keyPath(key)))
                .orElse(null));
    }

    private static String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private String irisBiome(Object engine, int x, int y, int z) {
        try {
            Object biome = ModReflection.call(engine, "getBiome", x, y, z);
            if (biome == null) {
                return null;
            }
            String name = ModReflection.string(biome, "getName");
            return name != null && !name.isBlank() ? name : ModReflection.string(biome, "getLoadKey");
        } catch (InvocationTargetException failure) {
            if (failure.getCause() == null || !failure.getCause().getClass().getSimpleName().endsWith("UnavailableException")) {
                logOnce(failure);
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            logOnce(failure);
            return null;
        }
    }

    /** Active Iris engines by the dimension id of the level each one generates. */
    private Map<String, Object> engines() {
        Map<String, Object> engines = new HashMap<>();
        try {
            Collection<?> active = (Collection<?>) ModReflection.callStatic(
                    ModReflection.require(ModIntegrationSources.IRIS_ENGINES), "activeEngines");
            for (Object engine : new ArrayList<>(active)) {
                Object world = ModReflection.call(engine, "getWorld");
                engines.put(String.valueOf(ModReflection.call(world, "identity")), engine);
                engines.putIfAbsent(String.valueOf(ModReflection.call(world, "name")), engine);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            logOnce(failure);
        }
        return engines;
    }

    private void logOnce(Throwable failure) {
        if (!failureLogged) {
            failureLogged = true;
            PlatformLog.warn("Could not read Iris biomes", failure);
        }
    }
}
