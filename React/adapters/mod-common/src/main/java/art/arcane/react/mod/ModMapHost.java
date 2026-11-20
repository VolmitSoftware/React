package art.arcane.react.mod;

import art.arcane.react.mod.mixin.ChunkMapAccess;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.DirtyRect;
import art.arcane.react.platform.Facing;
import art.arcane.react.platform.FrameSnapshot;
import art.arcane.react.platform.MapHost;
import art.arcane.react.platform.MapHostListener;
import art.arcane.react.platform.MapItemTags;
import art.arcane.react.platform.MapItemView;
import art.arcane.react.platform.ViewerSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * React maps on a mod server. Map ids live in the server's map storage as locked data, so vanilla never paints or
 * delivers them; React writes the colours and sends patches itself. Item tags use the same PublicBukkitValues keys as
 * Bukkit, so a map item reads the same on both. Everything here runs on the server thread except {@link #send}.
 */
final class ModMapHost implements MapHost<ItemStack>, ModMapHooks.Listener {
    static final String BUKKIT_VALUES = "PublicBukkitValues";
    static final String KEY_REACT = "react:react";
    static final String KEY_RENDERER = "react:react-renderer";
    static final String KEY_TOKEN = "react:react-map-token";
    private static final int SIZE = DirtyRect.SIZE;

    private final MinecraftServer server;
    private final ModTicks ticks;
    private final Map<UUID, UUID> viewerWorlds = new ConcurrentHashMap<>();
    private final Map<UUID, ItemFrame> loadedFrames = new LinkedHashMap<>();
    private final List<ServerPlayer> arrivedViewers = new ArrayList<>();
    private volatile MapHostListener<ItemStack> listener;
    private boolean drainQueued;
    private boolean quiet;

    ModMapHost(MinecraftServer server, ModTicks ticks) {
        this.server = server;
        this.ticks = ticks;
    }

    // Map ids

    @Override
    public int allocateMapId(UUID worldId) {
        ServerLevel level = level(worldId);
        if (level == null) {
            return -1;
        }
        return VanillaItems.allocateMap(server.overworld(), freshData(level));
    }

    @Override
    public void ensureMapData(int mapId, UUID worldId) {
        ServerLevel level = level(worldId);
        if (level != null && mapId >= 0 && VanillaItems.map(server.overworld(), mapId) == null) {
            VanillaItems.map(server.overworld(), mapId, freshData(level));
        }
    }

    private static MapItemSavedData freshData(ServerLevel level) {
        return MapItemSavedData.createFresh(0.0D, 0.0D, (byte) 0, false, false, level.dimension()).locked();
    }

    @Override
    public boolean mapIdsAreWorldBound() {
        return false;
    }

    @Override
    public UUID mapWorld(int mapId) {
        MapItemSavedData data = data(mapId);
        return data == null ? null : ModReactWorld.idOf(data.dimension);
    }

    private MapItemSavedData data(int mapId) {
        return mapId < 0 ? null : VanillaItems.map(server.overworld(), mapId);
    }

    @Override
    public void claim(int mapId) {
        MapItemSavedData data = data(mapId);
        if (data != null && !data.locked) {
            VanillaItems.map(server.overworld(), mapId, data.locked());
        }
        ModMapHooks.claim(mapId);
    }

    @Override
    public void release(int mapId) {
        ModMapHooks.release(mapId);
    }

    @Override
    public boolean isClaimed(int mapId) {
        return ModMapHooks.isClaimed(mapId);
    }

    @Override
    public void publish(int mapId, byte[] surface, DirtyRect changed) {
        MapItemSavedData data = data(mapId);
        if (data == null || surface == null || changed.isEmpty()) {
            return;
        }
        byte[] colors = data.colors;
        for (int row = changed.y0(); row < changed.y1(); row++) {
            int base = row * SIZE;
            for (int column = changed.x0(); column < changed.x1(); column++) {
                colors[base + column] = painted(surface[base + column]);
            }
        }
    }

    @Override
    public void send(UUID viewerId, int mapId, byte[] surface, DirtyRect rect) {
        ServerPlayer player = player(viewerId);
        if (player == null || rect.isEmpty()) {
            return;
        }
        byte[] source = surface;
        if (source == null) {
            MapItemSavedData data = data(mapId);
            if (data == null) {
                return;
            }
            source = data.colors;
        }
        player.connection.send(packet(mapId, source, rect));
    }

    static Packet<?> packet(int mapId, byte[] surface, DirtyRect rect) {
        int width = rect.width();
        int height = rect.height();
        byte[] patch = new byte[width * height];
        for (int row = 0; row < height; row++) {
            int from = (rect.y0() + row) * SIZE + rect.x0();
            int to = row * width;
            for (int column = 0; column < width; column++) {
                patch[to + column] = painted(surface[from + column]);
            }
        }
        return VanillaItems.mapPatch(mapId, rect.x0(), rect.y0(), width, height, patch);
    }

    private static byte painted(byte value) {
        return value == -1 ? 0 : value;
    }

    @Override
    public boolean pullsHeldMaps() {
        return false;
    }

    // Items

    @Override
    public MapItemView readMapItem(ItemStack item) {
        if (item == null || !item.is(Items.FILLED_MAP)) {
            return null;
        }
        CompoundTag values = VanillaApi.compound(VanillaItems.customData(item), BUKKIT_VALUES);
        MapItemTags tags = new MapItemTags(
                VanillaApi.byteOr(values, KEY_REACT, (byte) 0) == 1,
                VanillaApi.stringOr(values, KEY_RENDERER, null),
                VanillaApi.stringOr(values, KEY_TOKEN, null));
        List<String> lines = new ArrayList<>();
        for (Component line : VanillaItems.lore(item)) {
            lines.add(line.getString());
        }
        return new MapItemView(VanillaItems.mapId(item), tags, lines, VanillaItems.hasName(item));
    }

    @Override
    public ItemStack writeMapItem(ItemStack item, int mapId, MapItemTags tags, List<String> lore) {
        if (item == null || !item.is(Items.FILLED_MAP)) {
            return item;
        }
        if (mapId >= 0) {
            VanillaItems.mapId(item, mapId);
        }
        VanillaItems.clearName(item);
        List<Component> lines = new ArrayList<>(lore.size());
        for (String line : lore) {
            lines.add(Component.literal(line));
        }
        VanillaItems.lore(item, lines);
        VanillaItems.updateCustomData(item, (CompoundTag tag) -> {
            CompoundTag values = VanillaApi.compound(tag, BUKKIT_VALUES);
            if (tags.reactFlag()) {
                values.putByte(KEY_REACT, (byte) 1);
            }
            if (tags.rendererId() != null) {
                values.putString(KEY_RENDERER, tags.rendererId());
            }
            if (tags.token() != null) {
                values.putString(KEY_TOKEN, tags.token());
            }
            tag.put(BUKKIT_VALUES, values);
        });
        return item;
    }

    @Override
    public ItemStack newMapItem(int mapId, MapItemTags tags, List<String> lore) {
        return writeMapItem(new ItemStack(Items.FILLED_MAP), mapId, tags, lore);
    }

    // Inventories

    @Override
    public void giveToMainHand(UUID viewerId, ItemStack item) {
        ServerPlayer player = player(viewerId);
        if (player == null || item == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack displaced = VanillaApi.selectedItem(inventory);
        VanillaApi.selectItem(inventory, item);
        if (!displaced.isEmpty()) {
            addOrDrop(player, displaced);
        }
    }

    @Override
    public void giveToInventory(UUID viewerId, ItemStack item) {
        ServerPlayer player = player(viewerId);
        if (player != null && item != null) {
            addOrDrop(player, item);
        }
    }

    private static void addOrDrop(ServerPlayer player, ItemStack item) {
        if (!player.getInventory().add(item) && !item.isEmpty()) {
            VanillaCompat.dropFromPlayer(player, item);
        }
    }

    @Override
    public void rewriteInventoryMaps(UUID viewerId, Function<ItemStack, ItemStack> rewrite) {
        ServerPlayer player = player(viewerId);
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item.isEmpty()) {
                continue;
            }
            ItemStack replacement = rewrite.apply(item);
            if (replacement != null) {
                inventory.setItem(slot, replacement);
            }
        }
    }

    @Override
    public boolean moveToMainHand(UUID viewerId, Predicate<ItemStack> match) {
        ServerPlayer player = player(viewerId);
        if (player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (match.test(VanillaApi.selectedItem(inventory))) {
            return true;
        }
        int found = -1;
        if (match.test(player.getOffhandItem())) {
            found = Inventory.SLOT_OFFHAND;
        } else {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (match.test(inventory.getItem(slot))) {
                    found = slot;
                    break;
                }
            }
        }
        if (found < 0) {
            return false;
        }
        ItemStack moved = inventory.removeItemNoUpdate(found);
        giveToMainHand(viewerId, moved);
        return true;
    }

    // Frames

    @Override
    public boolean framesInChunk(UUID worldId, int chunkX, int chunkZ, Consumer<FrameSnapshot<ItemStack>> sink) {
        ServerLevel level = level(worldId);
        if (level == null || !level.getChunkSource().hasChunk(chunkX, chunkZ)) {
            return false;
        }
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        AABB area = new AABB(minX, VanillaApi.minY(level), minZ, minX + 16, VanillaApi.maxY(level) + 1, minZ + 16);
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, area)) {
            BlockPos pos = frame.getPos();
            if (SectionPos.blockToSectionCoord(pos.getX()) == chunkX && SectionPos.blockToSectionCoord(pos.getZ()) == chunkZ) {
                sink.accept(snapshotOf(frame, true));
            }
        }
        return true;
    }

    @Override
    public FrameSnapshot<ItemStack> frame(UUID frameId, boolean withItem) {
        ItemFrame frame = itemFrame(frameId);
        return frame == null ? null : snapshotOf(frame, withItem);
    }

    @Override
    public boolean frameExists(UUID frameId) {
        return itemFrame(frameId) != null;
    }

    private static FrameSnapshot<ItemStack> snapshotOf(ItemFrame frame, boolean withItem) {
        BlockPos pos = frame.getPos();
        return new FrameSnapshot<ItemStack>(
                frame.getUUID(),
                ModReactWorld.idOf((ServerLevel) frame.level()),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                frame.getX(),
                frame.getY(),
                frame.getZ(),
                facing(frame.getDirection()),
                frame.getRotation(),
                withItem ? frame.getItem().copy() : null,
                frame);
    }

    static Facing facing(Direction direction) {
        return switch (direction) {
            case NORTH -> Facing.NORTH;
            case SOUTH -> Facing.SOUTH;
            case EAST -> Facing.EAST;
            case WEST -> Facing.WEST;
            case UP -> Facing.UP;
            case DOWN -> Facing.DOWN;
        };
    }

    @Override
    public void setFrameItemQuietly(FrameSnapshot<ItemStack> snapshot, ItemStack item) {
        ItemFrame frame = snapshot.handle() instanceof ItemFrame handle && !handle.isRemoved() ? handle : itemFrame(snapshot.frameId());
        if (frame == null) {
            return;
        }
        boolean silent = frame.isSilent();
        quiet = true;
        try {
            frame.setSilent(true);
            frame.setItem(item, false);
        } finally {
            frame.setSilent(silent);
            quiet = false;
        }
    }

    @Override
    public void setFrameRotation(UUID frameId, int rotation) {
        ItemFrame frame = itemFrame(frameId);
        if (frame != null) {
            frame.setRotation(Math.floorMod(rotation, ItemFrame.NUM_ROTATIONS));
        }
    }

    private ItemFrame itemFrame(UUID frameId) {
        if (frameId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(frameId) instanceof ItemFrame frame && !frame.isRemoved()) {
                return frame;
            }
        }
        return null;
    }

    // Worlds and viewers

    @Override
    public boolean isWorldLoaded(UUID worldId) {
        return level(worldId) != null;
    }

    @Override
    public UUID defaultWorld() {
        return ModReactWorld.idOf(server.overworld());
    }

    private ServerLevel level(UUID worldId) {
        if (worldId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (ModReactWorld.idOf(level).equals(worldId)) {
                return level;
            }
        }
        return null;
    }

    @Override
    public void loadedChunks(Consumer<ChunkRef> sink) {
        for (ServerLevel level : server.getAllLevels()) {
            UUID worldId = ModReactWorld.idOf(level);
            for (ChunkHolder holder : ((ChunkMapAccess) level.getChunkSource().chunkMap).react$visibleChunkMap().values()) {
                LevelChunk chunk = VanillaForms.fullChunk(holder);
                if (chunk != null) {
                    sink.accept(new ChunkRef(worldId, VanillaApi.chunkX(chunk.getPos()), VanillaApi.chunkZ(chunk.getPos())));
                }
            }
        }
    }

    @Override
    public List<ViewerSnapshot> viewers() {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<ViewerSnapshot> viewers = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            viewers.add(snapshotOf(player));
        }
        return viewers;
    }

    private static ViewerSnapshot snapshotOf(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double lengthSquared = look.lengthSqr();
        boolean hasDirection = lengthSquared > 1.0E-6D;
        Vec3 direction = hasDirection ? look.scale(1.0D / Math.sqrt(lengthSquared)) : look;
        return new ViewerSnapshot(
                player.getUUID(),
                ModReactWorld.idOf(VanillaApi.level(player)),
                player.getX(),
                player.getY(),
                player.getZ(),
                eye.x,
                eye.y,
                eye.z,
                direction.x,
                direction.y,
                direction.z,
                hasDirection,
                mapIdOf(player.getMainHandItem()),
                mapIdOf(player.getOffhandItem()));
    }

    private static int mapIdOf(ItemStack item) {
        if (!item.is(Items.FILLED_MAP)) {
            return -1;
        }
        return VanillaItems.mapId(item);
    }

    @Override
    public UUID viewerWorld(UUID viewerId) {
        ServerPlayer player = player(viewerId);
        return player == null ? null : ModReactWorld.idOf(VanillaApi.level(player));
    }

    @Override
    public boolean isOnline(UUID viewerId) {
        return player(viewerId) != null;
    }

    @Override
    public boolean hasLineOfSight(UUID viewerId, UUID frameId, UUID worldId, double x, double y, double z) {
        ServerPlayer player = player(viewerId);
        ItemFrame frame = itemFrame(frameId);
        return player != null && frame != null && player.level() == frame.level() && player.hasLineOfSight(frame);
    }

    private ServerPlayer player(UUID viewerId) {
        return viewerId == null ? null : server.getPlayerList().getPlayer(viewerId);
    }

    // Threading: everything runs on the server thread

    @Override
    public boolean runOnFrameOwner(UUID frameId, long delayTicks, Runnable work, Runnable retired) {
        if (itemFrame(frameId) == null) {
            return false;
        }
        if (delayTicks <= 0L && server.isSameThread()) {
            work.run();
            return true;
        }
        return ticks.later(() -> {
            if (itemFrame(frameId) != null) {
                work.run();
            } else if (retired != null) {
                retired.run();
            }
        }, (int) Math.max(0L, delayTicks));
    }

    @Override
    public boolean runOnViewerOwner(UUID viewerId, long delayTicks, Runnable work) {
        if (player(viewerId) == null) {
            return false;
        }
        if (delayTicks <= 0L && server.isSameThread()) {
            work.run();
            return true;
        }
        return ticks.later(work, (int) Math.max(0L, delayTicks));
    }

    @Override
    public boolean runOnChunkOwner(UUID worldId, int chunkX, int chunkZ, Runnable work) {
        if (level(worldId) == null) {
            return false;
        }
        if (server.isSameThread()) {
            work.run();
            return true;
        }
        return ticks.later(work);
    }

    // Events: the map mixins report frame and player tracking; loaded work waits one tick for the entity to settle

    @Override
    public void bind(MapHostListener<ItemStack> bound) {
        unbind();
        listener = bound;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            viewerWorlds.put(player.getUUID(), ModReactWorld.idOf(VanillaApi.level(player)));
        }
        ModMapHooks.bind(this);
    }

    @Override
    public void unbind() {
        ModMapHooks.unbind(this);
        listener = null;
        viewerWorlds.clear();
        loadedFrames.clear();
        arrivedViewers.clear();
    }

    @Override
    public void frameChanged(ItemFrame frame) {
        MapHostListener<ItemStack> current = listener;
        if (current != null && !quiet) {
            current.onFrameChanged(frame.getUUID());
        }
    }

    @Override
    public void trackingStarted(ServerLevel level, Entity entity) {
        if (listener == null) {
            return;
        }
        if (entity instanceof ItemFrame frame) {
            loadedFrames.put(frame.getUUID(), frame);
            queueDrain();
        } else if (entity instanceof ServerPlayer player) {
            arrivedViewers.add(player);
            queueDrain();
        }
    }

    @Override
    public void trackingEnded(ServerLevel level, Entity entity) {
        MapHostListener<ItemStack> current = listener;
        if (current == null) {
            return;
        }
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (entity instanceof ItemFrame frame) {
            loadedFrames.remove(frame.getUUID());
            if (reason != null && reason.shouldDestroy()) {
                current.onFrameBroken(frame.getUUID());
            } else {
                current.onFrameUnloaded(frame.getUUID());
            }
        } else if (entity instanceof ServerPlayer player && reason != Entity.RemovalReason.CHANGED_DIMENSION) {
            viewerWorlds.remove(player.getUUID());
        }
    }

    private void queueDrain() {
        if (!drainQueued) {
            drainQueued = ticks.later(this::drain);
        }
    }

    private void drain() {
        drainQueued = false;
        MapHostListener<ItemStack> current = listener;
        if (current == null) {
            return;
        }
        Map<ChunkRef, List<FrameSnapshot<ItemStack>>> byChunk = new LinkedHashMap<>();
        for (ItemFrame frame : loadedFrames.values()) {
            if (!frame.isRemoved() && frame.level() instanceof ServerLevel) {
                FrameSnapshot<ItemStack> snapshot = snapshotOf(frame, true);
                ChunkRef chunk = new ChunkRef(snapshot.worldId(), SectionPos.blockToSectionCoord(snapshot.blockX()),
                        SectionPos.blockToSectionCoord(snapshot.blockZ()));
                byChunk.computeIfAbsent(chunk, (ChunkRef ignored) -> new ArrayList<>()).add(snapshot);
            }
        }
        loadedFrames.clear();
        for (Map.Entry<ChunkRef, List<FrameSnapshot<ItemStack>>> entry : byChunk.entrySet()) {
            ChunkRef chunk = entry.getKey();
            current.onFramesLoaded(chunk.worldId(), chunk.chunkX(), chunk.chunkZ(), entry.getValue());
        }
        List<ServerPlayer> arrived = new ArrayList<>(arrivedViewers);
        arrivedViewers.clear();
        for (ServerPlayer player : arrived) {
            if (player.isRemoved() || player.hasDisconnected()) {
                continue;
            }
            UUID world = ModReactWorld.idOf(VanillaApi.level(player));
            UUID previous = viewerWorlds.put(player.getUUID(), world);
            if (previous == null) {
                current.onViewerJoin(player.getUUID());
            } else if (!previous.equals(world)) {
                current.onViewerChangedWorld(player.getUUID(), world);
            }
        }
    }
}
