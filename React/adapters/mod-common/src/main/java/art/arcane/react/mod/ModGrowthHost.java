package art.arcane.react.mod;

import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.GrowthHost;
import art.arcane.react.platform.HostListeners;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.PlayerPosition;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Natural block changes on mods, fed by the growth mixins at the points where Bukkit fires its grow, spread, form,
 * fade, burn and leaf decay events.
 */
public final class ModGrowthHost implements GrowthHost {
    private static final int NO_UPDATES = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int UPDATES = Block.UPDATE_ALL;
    private static final HostListeners<SpreadEvent> SPREADS = new HostListeners<>();
    private static final HostListeners<GrowEvent> GROWS = new HostListeners<>();
    private static final HostListeners<ChangeEvent> FORMS = new HostListeners<>();
    private static final HostListeners<ChangeEvent> FADES = new HostListeners<>();
    private static final HostListeners<BlockHost.BlockEvent> BURNS = new HostListeners<>();
    private static final HostListeners<BlockHost.BlockEvent> DECAYS = new HostListeners<>();
    private static final Map<Block, String> TYPE_NAMES = new ConcurrentHashMap<>();
    private static final Direction[] FACES = Direction.values();

    private final MinecraftServer server;

    public ModGrowthHost(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Subscription onSpread(ListenerPriority priority, boolean ignoreCancelled, Consumer<SpreadEvent> listener) {
        return SPREADS.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onGrow(ListenerPriority priority, boolean ignoreCancelled, Consumer<GrowEvent> listener) {
        return GROWS.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onForm(ListenerPriority priority, boolean ignoreCancelled, Consumer<ChangeEvent> listener) {
        return FORMS.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onFade(ListenerPriority priority, boolean ignoreCancelled, Consumer<ChangeEvent> listener) {
        return FADES.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onBurn(ListenerPriority priority, boolean ignoreCancelled, Consumer<BlockHost.BlockEvent> listener) {
        return BURNS.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onLeavesDecay(ListenerPriority priority, boolean ignoreCancelled, Consumer<BlockHost.BlockEvent> listener) {
        return DECAYS.subscribe(priority, ignoreCancelled, listener);
    }

    static void clear() {
        SPREADS.clear();
        GROWS.clear();
        FORMS.clear();
        FADES.clear();
        BURNS.clear();
        DECAYS.clear();
    }

    /** False when a listener cancelled the growth of the block at {@code pos} into {@code grown}. */
    public static boolean allowGrow(Level level, BlockPos pos, BlockState grown) {
        if (GROWS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return GROWS.dispatch(new Change(serverLevel, pos, grown, null));
    }

    /** False when a listener cancelled the spread from {@code source} into {@code pos}. */
    public static boolean allowSpread(Level level, BlockPos pos, BlockPos source, BlockState spread) {
        if (SPREADS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return SPREADS.dispatch(new Change(serverLevel, pos, spread, source));
    }

    /** False when a listener cancelled the block forming at {@code pos}, such as snow or ice. */
    public static boolean allowForm(Level level, BlockPos pos, BlockState formed) {
        if (FORMS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return FORMS.dispatch(new Change(serverLevel, pos, formed, null));
    }

    /** False when a listener cancelled the block at {@code pos} fading, such as snow melting or fire burning out. */
    public static boolean allowFade(Level level, BlockPos pos) {
        if (FADES.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return FADES.dispatch(new Change(serverLevel, pos, null, null));
    }

    /** False when a listener cancelled fire burning the block at {@code pos}. */
    public static boolean allowBurn(Level level, BlockPos pos) {
        if (BURNS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return BURNS.dispatch(new Change(serverLevel, pos, null, null));
    }

    /** False when a listener cancelled the leaves at {@code pos} decaying. */
    public static boolean allowLeavesDecay(Level level, BlockPos pos) {
        if (DECAYS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return DECAYS.dispatch(new Change(serverLevel, pos, null, null));
    }

    static String typeName(BlockState state) {
        return TYPE_NAMES.computeIfAbsent(state.getBlock(), VanillaApi::blockTypeName);
    }

    static int leaf(BlockState state) {
        if (!(state.getBlock() instanceof LeavesBlock)) {
            return NOT_LEAVES;
        }
        return state.getValue(LeavesBlock.DISTANCE) | (state.getValue(LeavesBlock.PERSISTENT) ? PERSISTENT_LEAVES : 0);
    }

    @Override
    public String type(GameLocation location) {
        ServerLevel level = level(location);
        BlockPos pos = pos(location);
        return level == null || !level.isLoaded(pos) ? null : typeName(level.getBlockState(pos));
    }

    @Override
    public int leaf(GameLocation location) {
        ServerLevel level = level(location);
        BlockPos pos = pos(location);
        return level == null || !level.isLoaded(pos) ? NOT_LEAVES : leaf(level.getBlockState(pos));
    }

    @Override
    public ChunkBlocks chunk(UUID worldId, int chunkX, int chunkZ) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        LevelChunk chunk = level == null ? null : level.getChunkSource().getChunkNow(chunkX, chunkZ);
        return chunk == null ? null : new LiveChunk(level, chunk);
    }

    @Override
    public boolean advanceAge(GameLocation location, AgeStep step) {
        ServerLevel level = level(location);
        BlockPos pos = pos(location);
        if (level == null || !level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        IntegerProperty age = ageProperty(state);
        if (age == null) {
            return false;
        }
        int current = state.getValue(age);
        int maximum = current;
        for (int value : age.getPossibleValues()) {
            maximum = Math.max(maximum, value);
        }
        int target = step.targetAge(current, maximum);
        if (target == current || !age.getPossibleValues().contains(target)) {
            return false;
        }
        return level.setBlock(pos, state.setValue(age, target), NO_UPDATES);
    }

    private static IntegerProperty ageProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integer && "age".equals(property.getName())) {
                return integer;
            }
        }
        return null;
    }

    @Override
    public boolean boneMeal(GameLocation location) {
        ServerLevel level = level(location);
        BlockPos pos = pos(location);
        if (level == null || !level.isLoaded(pos)) {
            return false;
        }
        try {
            Block before = level.getBlockState(pos).getBlock();
            if (!BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos)) {
                return false;
            }
            return level.getBlockState(pos).getBlock() != before;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public void setState(GameLocation location, Object state, boolean applyPhysics) {
        ServerLevel level = level(location);
        if (level != null && state instanceof BlockState blockState) {
            level.setBlock(pos(location), blockState, applyPhysics ? UPDATES : NO_UPDATES);
        }
    }

    @Override
    public void fastSet(GameLocation location, Object state) {
        ServerLevel level = level(location);
        if (level != null && state instanceof BlockState blockState) {
            BlockPos pos = pos(location);
            level.setBlock(pos, blockState, shouldUpdate(level, pos) ? UPDATES : NO_UPDATES);
        }
    }

    @Override
    public void breakNaturally(GameLocation location, boolean fast) {
        ServerLevel level = level(location);
        BlockPos pos = pos(location);
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        if (!fast || shouldUpdate(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        Block.dropResources(state, level, pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), NO_UPDATES);
    }

    /** True when a neighbour may depend on the block, so changing it must run block updates, as Bukkit's FastWorld checks. */
    private static boolean shouldUpdate(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (Direction face : FACES) {
            neighbour.setWithOffset(pos, face);
            BlockState state = level.getBlockState(neighbour);
            if (!state.isAir() && !state.isCollisionShapeFullBlock(level, neighbour)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void playSound(GameLocation location, String sound, float volume, float pitch) {
        ServerLevel level = level(location);
        SoundEvent event = VanillaApi.sound(sound);
        if (level != null && event != null) {
            level.playSound(null, location.blockX(), location.blockY(), location.blockZ(), event, SoundSource.MASTER, volume, pitch);
        }
    }

    @Override
    public int randomTickSpeed(UUID worldId) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        return level == null ? 0 : Math.max(0, VanillaApi.randomTickSpeed(level));
    }

    @Override
    public int simulationDistance(UUID worldId) {
        return server.getPlayerList().getSimulationDistance();
    }

    @Override
    public Optional<List<PlayerPosition>> simulatingPlayers() {
        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        List<PlayerPosition> positions = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            positions.add(new PlayerPosition(player.getUUID(), ModReactWorld.idOf(VanillaApi.level(player)), player.getX(), player.getY(),
                    player.getZ()));
        }
        return Optional.of(positions);
    }

    @Override
    public boolean anyPlayerInColumn(UUID worldId, double x, double z, double blocks) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        if (level == null || blocks <= 0) {
            return false;
        }
        double limit = blocks * blocks;
        for (ServerPlayer player : List.copyOf(level.players())) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if (dx * dx + dz * dz <= limit) {
                return true;
            }
        }
        return false;
    }

    private ServerLevel level(GameLocation location) {
        return location == null ? null : ModWorldHost.level(server, location.worldId());
    }

    private static BlockPos pos(GameLocation location) {
        return new BlockPos(location.blockX(), location.blockY(), location.blockZ());
    }

    private static GameLocation location(ServerLevel level, BlockPos pos) {
        return new GameLocation(ModReactWorld.idOf(level), pos.getX(), pos.getY(), pos.getZ());
    }

    private static final class Change implements GrowEvent, SpreadEvent {
        private final ServerLevel level;
        private final BlockPos pos;
        private final BlockState state;
        private final BlockState changed;
        private final BlockPos source;
        private boolean cancelled;

        private Change(ServerLevel level, BlockPos pos, BlockState changed, BlockPos source) {
            this.level = level;
            this.pos = pos.immutable();
            this.state = level.getBlockState(this.pos);
            this.changed = changed;
            this.source = source == null ? null : source.immutable();
        }

        @Override
        public GameLocation location() {
            return ModGrowthHost.location(level, pos);
        }

        @Override
        public String type() {
            return typeName(state);
        }

        @Override
        public Object state() {
            return state;
        }

        @Override
        public Object newState() {
            return changed;
        }

        @Override
        public GameLocation source() {
            return source == null ? null : ModGrowthHost.location(level, source);
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

    private static final class LiveChunk implements ChunkBlocks {
        private final ServerLevel level;
        private final LevelChunk chunk;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private final int minX;
        private final int minZ;

        private LiveChunk(ServerLevel level, LevelChunk chunk) {
            this.level = level;
            this.chunk = chunk;
            this.minX = VanillaApi.chunkX(chunk.getPos()) << 4;
            this.minZ = VanillaApi.chunkZ(chunk.getPos()) << 4;
        }

        @Override
        public int minHeight() {
            return VanillaApi.minY(level);
        }

        @Override
        public int maxHeight() {
            return VanillaApi.maxY(level) + 1;
        }

        @Override
        public int highestY(int x, int z) {
            return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        }

        @Override
        public String type(int x, int y, int z) {
            return typeName(state(x, y, z));
        }

        @Override
        public int leaf(int x, int y, int z) {
            return ModGrowthHost.leaf(state(x, y, z));
        }

        private BlockState state(int x, int y, int z) {
            return chunk.getBlockState(cursor.set(minX + x, y, minZ + z));
        }
    }
}
