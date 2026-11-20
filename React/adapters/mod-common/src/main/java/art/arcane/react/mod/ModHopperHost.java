package art.arcane.react.mod;

import art.arcane.react.mod.mixin.HopperCooldownAccess;
import art.arcane.react.platform.Facing;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HopperHost;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static net.minecraft.world.level.block.HopperBlock.FACING;

/**
 * Hopper transfers on mods, fed by the hopper mixin at the two points where Bukkit fires its item move event: a
 * hopper pushing one item out, and a hopper pulling one item from the container above. The tick filter, index
 * changes and block reads mirror what the Bukkit host gets from its agent hooks and events.
 */
public final class ModHopperHost implements HopperHost {
    private static final List<MoveListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<IndexListener> INDEX_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile TickFilter tickFilter;

    private final MinecraftServer server;
    private final ModChunkSamples samples;

    public ModHopperHost(MinecraftServer server, ModChunkSamples samples) {
        this.server = server;
        this.samples = samples;
    }

    @Override
    public Subscription onMove(MoveListener listener) {
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }

    static void clear() {
        LISTENERS.clear();
        INDEX_LISTENERS.clear();
        tickFilter = null;
    }

    /** False when a listener cancelled the push from {@code hopper} at {@code pos}. */
    public static boolean allowPush(Level level, BlockPos pos) {
        if (LISTENERS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return dispatch(new ModMove(serverLevel, pos, true, pos, null));
    }

    /** False when a listener cancelled the pull into {@code destination} from {@code source}. */
    public static boolean allowPull(Level level, Object destination, Object source) {
        if (LISTENERS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        BlockPos sourceHopper = source instanceof HopperBlockEntity hopper ? hopper.getBlockPos() : null;
        BlockPos destinationHopper = destination instanceof HopperBlockEntity hopper ? hopper.getBlockPos() : null;
        BlockPos hopper = sourceHopper != null ? sourceHopper : destinationHopper;
        return dispatch(new ModMove(serverLevel, hopper, sourceHopper != null, null, destinationHopper));
    }

    /** True when the installed tick filter skips this hopper tick. */
    public static boolean skipsTick(Level level, BlockPos pos) {
        TickFilter filter = tickFilter;
        return filter != null && level instanceof ServerLevel serverLevel
                && filter.skip(ModReactWorld.idOf(serverLevel), pos.getX(), pos.getY(), pos.getZ());
    }

    public static void itemTracked(Entity entity) {
        if (!INDEX_LISTENERS.isEmpty() && entity instanceof ItemEntity && entity.level() instanceof ServerLevel) {
            for (IndexListener listener : INDEX_LISTENERS) {
                listener.itemSeen(entity);
            }
        }
    }

    public static void itemUntracked(Entity entity) {
        if (!INDEX_LISTENERS.isEmpty() && entity instanceof ItemEntity) {
            for (IndexListener listener : INDEX_LISTENERS) {
                listener.itemGone(entity.getUUID());
            }
        }
    }

    public static void blockEntitySet(LevelChunk chunk, BlockEntity blockEntity) {
        if (!INDEX_LISTENERS.isEmpty() && blockEntity instanceof HopperBlockEntity && chunk.getLevel() instanceof ServerLevel level) {
            UUID worldId = ModReactWorld.idOf(level);
            BlockPos pos = blockEntity.getBlockPos();
            for (IndexListener listener : INDEX_LISTENERS) {
                listener.hopperPlaced(worldId, pos.getX(), pos.getY(), pos.getZ());
            }
        }
    }

    public static void blockEntityRemoving(LevelChunk chunk, BlockPos pos) {
        if (!INDEX_LISTENERS.isEmpty() && chunk.getLevel() instanceof ServerLevel level
                && chunk.getBlockEntity(pos) instanceof HopperBlockEntity) {
            UUID worldId = ModReactWorld.idOf(level);
            for (IndexListener listener : INDEX_LISTENERS) {
                listener.hopperGone(worldId, pos.getX(), pos.getY(), pos.getZ());
            }
        }
    }

    private static boolean dispatch(ModMove move) {
        for (MoveListener listener : LISTENERS) {
            if (move.cancelled) {
                return false;
            }
            listener.onMove(move);
        }
        return !move.cancelled;
    }

    @Override
    public Object chunk(UUID worldId, int chunkX, int chunkZ) {
        return loadedChunk(worldId, chunkX, chunkZ);
    }

    @Override
    public List<HopperBlock> hoppers(Object handle) {
        if (!(handle instanceof LevelChunk chunk)) {
            return List.of();
        }
        List<HopperBlock> hoppers = new ArrayList<>();
        for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
            if (blockEntity instanceof HopperBlockEntity) {
                BlockPos pos = blockEntity.getBlockPos();
                BlockState state = blockEntity.getBlockState();
                Facing facing = state.hasProperty(FACING) ? facing(state.getValue(FACING)) : null;
                hoppers.add(new HopperBlock(pos.getX(), pos.getY(), pos.getZ(), facing));
            }
        }
        return hoppers;
    }

    @Override
    public String blockType(UUID worldId, int x, int y, int z) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        if (level == null) {
            return null;
        }
        if (loadedChunk(level, x >> 4, z >> 4) == null) {
            return "AIR";
        }
        return VanillaApi.blockTypeName(level.getBlockState(new BlockPos(x, y, z)).getBlock());
    }

    @Override
    public boolean isContainer(UUID worldId, int x, int y, int z) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        return level != null && containerAt(level, x, y, z) != null;
    }

    @Override
    public boolean transferOne(UUID worldId, int fromX, int fromY, int fromZ, int toX, int toY, int toZ, BooleanSupplier permit) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        if (level == null) {
            return false;
        }
        Container source = containerAt(level, fromX, fromY, fromZ);
        if (source == null || source.isEmpty()) {
            return false;
        }
        Container target = containerAt(level, toX, toY, toZ);
        if (target == null) {
            return false;
        }
        for (int slot = 0; slot < source.getContainerSize(); slot++) {
            ItemStack stack = source.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack transfer = stack.copyWithCount(1);
            if (!permit.getAsBoolean()) {
                return false;
            }
            if (!insert(target, transfer)) {
                return false;
            }
            source.removeItem(slot, 1);
            source.setChanged();
            target.setChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean isOwnedByCurrentRegion(UUID worldId, double x, double y, double z) {
        return server.isSameThread();
    }

    @Override
    public Subscription installTickFilter(TickFilter filter, Consumer<Boolean> installed) {
        tickFilter = filter;
        installed.accept(true);
        return () -> {
            if (tickFilter == filter) {
                tickFilter = null;
            }
        };
    }

    @Override
    public Subscription onIndexChange(IndexListener listener) {
        INDEX_LISTENERS.add(listener);
        return () -> INDEX_LISTENERS.remove(listener);
    }

    @Override
    public boolean supportsHopperAccess() {
        return true;
    }

    @Override
    public HopperAccess hopperAccess(UUID worldId, int x, int y, int z) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        if (level == null || loadedChunk(level, x >> 4, z >> 4) == null) {
            return null;
        }
        return level.getBlockEntity(new BlockPos(x, y, z)) instanceof HopperBlockEntity hopper ? new ModHopperAccess(hopper) : null;
    }

    @Override
    public Object item(UUID itemId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(itemId);
            if (entity != null) {
                return entity instanceof ItemEntity ? entity : null;
            }
        }
        return null;
    }

    @Override
    public UUID itemId(Object item) {
        return ((ItemEntity) item).getUUID();
    }

    @Override
    public boolean isLiveItem(Object item) {
        ItemEntity entity = (ItemEntity) item;
        return entity.isAlive() && !entity.isRemoved();
    }

    @Override
    public GameLocation itemLocation(Object item) {
        return ModGameEntity.location((ItemEntity) item);
    }

    @Override
    public Object[] chunkEntities(Object handle) {
        if (!(handle instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) {
            return new Object[0];
        }
        int chunkX = VanillaApi.chunkX(chunk.getPos());
        int chunkZ = VanillaApi.chunkZ(chunk.getPos());
        AABB bounds = new AABB(chunkX << 4, VanillaApi.minY(level), chunkZ << 4,
                (chunkX << 4) + 16, VanillaApi.maxY(level) + 1, (chunkZ << 4) + 16);
        return level.getEntities((Entity) null, bounds, (Entity entity) -> true).toArray();
    }

    @Override
    public boolean isItem(Object entity) {
        return entity instanceof ItemEntity;
    }

    @Override
    public Object[] regionPlayers() {
        return new Object[0];
    }

    @Override
    public GameLocation playerLocation(Object player) {
        ServerPlayer serverPlayer = (ServerPlayer) player;
        return serverPlayer.isRemoved() ? null : ModGameEntity.location(serverPlayer);
    }

    @Override
    public int mergeNearbyItems(UUID worldId, double x, double y, double z, double radius, int maxMergedEntities) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        if (level == null || maxMergedEntities <= 0) {
            return 0;
        }
        AABB bounds = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        List<ItemEntity> items = new ArrayList<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, bounds, (ItemEntity entity) -> true)) {
            if (live(item) && item.getItem().getCount() > 0) {
                items.add(item);
            }
        }

        int merged = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemEntity base = items.get(i);
            if (!live(base)) {
                continue;
            }

            ItemStack baseStack = base.getItem().copy();
            for (int j = i + 1; j < items.size(); j++) {
                if (merged >= maxMergedEntities) {
                    return merged;
                }

                ItemEntity other = items.get(j);
                if (!live(other)) {
                    continue;
                }

                ItemStack otherStack = other.getItem().copy();
                if (!VanillaApi.sameItemSameData(baseStack, otherStack)) {
                    continue;
                }

                int room = baseStack.getMaxStackSize() - baseStack.getCount();
                if (room <= 0) {
                    break;
                }

                int transfer = Math.min(room, otherStack.getCount());
                if (transfer <= 0) {
                    continue;
                }

                baseStack.setCount(baseStack.getCount() + transfer);
                base.setItem(baseStack.copy());
                otherStack.setCount(otherStack.getCount() - transfer);
                if (otherStack.getCount() <= 0) {
                    other.discard();
                    merged++;
                } else {
                    other.setItem(otherStack);
                }
            }
        }

        return merged;
    }

    @Override
    public List<SampledChunkRate> sampledChunks(String samplerId) {
        return samples.rates(samplerId);
    }

    @Override
    public int minBuildHeight(UUID worldId) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        return level == null ? 0 : VanillaApi.minY(level);
    }

    @Override
    public boolean unloadChunk(UUID worldId, int chunkX, int chunkZ) {
        return false;
    }

    private LevelChunk loadedChunk(UUID worldId, int chunkX, int chunkZ) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        return level == null ? null : loadedChunk(level, chunkX, chunkZ);
    }

    private static LevelChunk loadedChunk(ServerLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkNow(chunkX, chunkZ);
    }

    private static Container containerAt(ServerLevel level, int x, int y, int z) {
        if (loadedChunk(level, x >> 4, z >> 4) == null) {
            return null;
        }
        Container container = HopperBlockEntity.getContainerAt(level, new BlockPos(x, y, z));
        return container instanceof Entity ? null : container;
    }

    private static boolean insert(Container target, ItemStack stack) {
        int size = target.getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack present = target.getItem(slot);
            if (!present.isEmpty() && VanillaApi.sameItemSameData(present, stack)
                    && present.getCount() < Math.min(present.getMaxStackSize(), target.getMaxStackSize())
                    && target.canPlaceItem(slot, stack)) {
                present.grow(1);
                return true;
            }
        }
        for (int slot = 0; slot < size; slot++) {
            if (target.getItem(slot).isEmpty() && target.canPlaceItem(slot, stack)) {
                target.setItem(slot, stack);
                return true;
            }
        }
        return false;
    }

    private static boolean live(ItemEntity item) {
        return item.isAlive() && !item.isRemoved();
    }

    private static Facing facing(Direction direction) {
        return switch (direction) {
            case DOWN -> Facing.DOWN;
            case NORTH -> Facing.NORTH;
            case SOUTH -> Facing.SOUTH;
            case EAST -> Facing.EAST;
            case WEST -> Facing.WEST;
            case UP -> null;
        };
    }

    private record ModHopperAccess(HopperBlockEntity hopper) implements HopperAccess {
        @Override
        public boolean addItem(Object item) {
            return item instanceof ItemEntity entity && HopperBlockEntity.addItem(hopper, entity);
        }

        @Override
        public boolean isEmpty() {
            return hopper.isEmpty();
        }

        @Override
        public int cooldown() {
            return ((HopperCooldownAccess) hopper).reactCooldown();
        }

        @Override
        public void cooldown(int ticks) {
            ((HopperCooldownAccess) hopper).reactSetCooldown(ticks);
            hopper.setChanged();
        }
    }

    private static final class ModMove implements Move {
        private final ServerLevel level;
        private final BlockPos hopper;
        private final boolean sourceIsHopper;
        private final BlockPos pushingHopper;
        private final BlockPos destinationHopper;
        private boolean cancelled;

        private ModMove(ServerLevel level, BlockPos hopper, boolean sourceIsHopper, BlockPos pushingHopper, BlockPos destinationHopper) {
            this.level = level;
            this.hopper = hopper;
            this.sourceIsHopper = sourceIsHopper;
            this.pushingHopper = pushingHopper;
            this.destinationHopper = destinationHopper;
        }

        @Override
        public GameLocation hopper() {
            return hopper == null ? null : new GameLocation(ModReactWorld.idOf(level), hopper.getX(), hopper.getY(), hopper.getZ());
        }

        @Override
        public boolean sourceIsHopper() {
            return sourceIsHopper;
        }

        @Override
        public boolean destinationIsHopper() {
            if (destinationHopper != null) {
                return true;
            }
            if (pushingHopper == null) {
                return false;
            }
            BlockState state = level.getBlockState(pushingHopper);
            if (!state.hasProperty(FACING)) {
                return false;
            }
            Direction facing = state.getValue(FACING);
            return level.getBlockEntity(pushingHopper.relative(facing)) instanceof HopperBlockEntity;
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
