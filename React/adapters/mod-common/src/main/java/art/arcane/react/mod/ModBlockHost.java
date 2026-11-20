package art.arcane.react.mod;

import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HostEvent;
import art.arcane.react.platform.HostListeners;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Block changes on mods, fed by React's block, piston, fluid, falling block and chunk mixins at the points where
 * Bukkit fires the matching events. The loaded-chunk index runs from server start, so it already knows the chunks
 * that loaded before React did.
 */
public final class ModBlockHost implements BlockHost {
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final HostListeners<BlockEvent> BREAKS = new HostListeners<>();
    private static final HostListeners<BlockEvent> PLACES = new HostListeners<>();
    private static final HostListeners<PhysicsEvent> PHYSICS = new HostListeners<>();
    private static final HostListeners<PistonEvent> PISTONS = new HostListeners<>();
    private static final HostListeners<FluidFlowEvent> FLOWS = new HostListeners<>();
    private static final HostListeners<FluidLevelEvent> LEVELS = new HostListeners<>();
    private static final HostListeners<FallingBlockEvent> FALLING = new HostListeners<>();
    private static volatile FallingTickHook fallingHook;

    private final MinecraftServer server;

    ModBlockHost(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Subscription onBreak(ListenerPriority priority, boolean ignoreCancelled, Consumer<BlockEvent> listener) {
        return BREAKS.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onPlace(ListenerPriority priority, boolean ignoreCancelled, Consumer<BlockEvent> listener) {
        return PLACES.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onPhysics(ListenerPriority priority, boolean ignoreCancelled, Consumer<PhysicsEvent> listener) {
        return PHYSICS.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onPiston(ListenerPriority priority, boolean ignoreCancelled, Consumer<PistonEvent> listener) {
        return PISTONS.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onFluidFlow(ListenerPriority priority, boolean ignoreCancelled, Consumer<FluidFlowEvent> listener) {
        return FLOWS.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onFluidLevelChange(ListenerPriority priority, boolean ignoreCancelled, Consumer<FluidLevelEvent> listener) {
        return LEVELS.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onFallingBlockChange(ListenerPriority priority, boolean ignoreCancelled, Consumer<FallingBlockEvent> listener) {
        return FALLING.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public void installFallingTickHook(FallingTickHook hook, Runnable onInstalled) {
        fallingHook = hook;
        onInstalled.run();
    }

    @Override
    public void uninstallFallingTickHook() {
        fallingHook = null;
    }

    @Override
    public boolean canTickFluids() {
        return true;
    }

    @Override
    public boolean tickFluid(UUID worldId, int x, int y, int z, boolean water, boolean lava) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        if (level == null) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState block = level.getBlockState(pos);
        FluidState fluid = block.getFluidState();
        if (fluid.isEmpty() || (!water && fluid.is(FluidTags.WATER)) || (!lava && fluid.is(FluidTags.LAVA))) {
            return false;
        }
        VanillaApi.tickFluid(fluid, level, pos, block);
        return true;
    }

    @Override
    public int minHeight(UUID worldId) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        return level == null ? 0 : VanillaApi.minY(level);
    }

    @Override
    public int maxHeight(UUID worldId) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        return level == null ? 0 : VanillaApi.maxY(level) + 1;
    }

    @Override
    public String blockType(UUID worldId, int x, int y, int z) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        return level == null ? "AIR" : VanillaApi.blockTypeName(level.getBlockState(new BlockPos(x, y, z)).getBlock());
    }

    @Override
    public boolean isEmpty(UUID worldId, int x, int y, int z) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        return level == null || level.getBlockState(new BlockPos(x, y, z)).isAir();
    }

    @Override
    public void setBlock(UUID worldId, int x, int y, int z, String type, boolean applyPhysics) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        if (level != null) {
            level.setBlock(new BlockPos(x, y, z), VanillaApi.blockByName(type).defaultBlockState(), applyPhysics ? Block.UPDATE_ALL : QUIET);
        }
    }

    @Override
    public void dropItem(GameLocation at, String itemType, int amount) {
        ServerLevel level = at == null ? null : ModWorldHost.level(server, at.worldId());
        Item item = VanillaApi.itemByName(itemType);
        if (level != null && item != Items.AIR) {
            dropNaturally(level, at.x(), at.y(), at.z(), new ItemStack(item, amount));
        }
    }

    @Override
    public void giveItem(GameEntity player, GameLocation at, String itemType, int amount) {
        if (player == null || !(player.handle() instanceof Player handle) || !(handle.level() instanceof ServerLevel level)) {
            return;
        }
        if (handle.isCreative()) {
            return;
        }
        Item item = VanillaApi.itemByName(itemType);
        if (item == Items.AIR) {
            return;
        }
        ItemStack stack = new ItemStack(item, amount);
        Tweak fastDrops = ReactComponents.tweak("fast-drops");
        if (fastDrops != null && fastDrops.isEnabled()) {
            handle.getInventory().add(stack);
            if (!stack.isEmpty()) {
                dropNaturally(level, at.x(), at.y(), at.z(), stack);
            } else {
                RandomSource random = level.getRandom();
                level.playSound(null, handle.getX(), handle.getY(), handle.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                        0.7F, 1F + (random.nextFloat() * 0.4F - 0.2F));
            }
        } else {
            dropNaturally(level, at.x(), at.y(), at.z(), stack);
        }
    }

    @Override
    public int matchingAbove(GameLocation block, Object blockState) {
        ServerLevel level = ModWorldHost.level(server, block.worldId());
        if (level == null) {
            return 0;
        }
        int bonus = 0;
        int top = VanillaApi.maxY(level) + 1;
        for (int i = block.blockY() + 1; i < top - 1; i++) {
            if (level.getBlockState(new BlockPos(block.blockX(), i, block.blockZ())) == blockState) {
                bonus++;
            } else {
                break;
            }
        }
        return bonus;
    }

    @Override
    public void dropColumn(GameLocation block, Object blockState, int bonus) {
        ServerLevel level = ModWorldHost.level(server, block.worldId());
        if (level == null || !(blockState instanceof BlockState d)) {
            return;
        }
        BlockPos target = new BlockPos(block.blockX(), block.blockY(), block.blockZ());
        if (level.getBlockState(target) != d) {
            return;
        }

        level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 0; i < bonus; i++) {
            BlockPos above = target.above(1 + i);
            if (level.getBlockState(above) != d) {
                return;
            }
            level.setBlock(above, Blocks.AIR.defaultBlockState(), QUIET);
        }

        fallEffect(level, block.x(), block.y(), block.z(), target, d);
        int minimum = VanillaApi.minY(level);
        for (int i = target.getY(); i > minimum + 1; i--) {
            BlockPos at = new BlockPos(target.getX(), i, target.getZ());
            if (level.getBlockState(at.below()).isSolid()) {
                for (int j = 0; j < bonus; j++) {
                    level.setBlock(at.above(1 + j), d, QUIET);
                }
                level.setBlock(at, d, QUIET);
                landEffect(level, at, d);
                break;
            }

            BlockState current = level.getBlockState(at);
            if (!current.isAir() && current.getCollisionShape(level, at).isEmpty() && current.getFluidState().isEmpty()) {
                Item item = d.getBlock().asItem();
                if (item != Items.AIR) {
                    dropNaturally(level, at.getX(), at.getY(), at.getZ(), new ItemStack(item, 1 + bonus));
                }
                break;
            }
        }
    }

    static void clear() {
        BREAKS.clear();
        PLACES.clear();
        PHYSICS.clear();
        PISTONS.clear();
        FLOWS.clear();
        LEVELS.clear();
        FALLING.clear();
        fallingHook = null;
    }

    /** False when a listener cancelled the player breaking the block. */
    public static boolean allowBreak(Level level, BlockPos pos, Player player) {
        if (BREAKS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        BlockState state = serverLevel.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        return BREAKS.dispatch(new ModBlockEvent(serverLevel, pos, state, player));
    }

    /** False when a listener cancelled the player placing the block. */
    public static boolean allowPlace(Level level, BlockPos pos, BlockState state, Player player) {
        if (PLACES.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return PLACES.dispatch(new ModBlockEvent(serverLevel, pos, state, player));
    }

    /** False when a listener cancelled the neighbour update of the block. */
    public static boolean allowPhysics(Level level, BlockPos pos, BlockState state, Block changed) {
        if (PHYSICS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return PHYSICS.dispatch(new ModPhysicsEvent(serverLevel, pos, state, changed));
    }

    /** False when a listener cancelled the piston move. */
    public static boolean allowPiston(Level level, BlockPos piston, boolean extending, List<BlockPos> moved) {
        if (PISTONS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return PISTONS.dispatch(new ModPistonEvent(serverLevel, piston, extending, moved));
    }

    /** False when a listener cancelled the fluid spreading into {@code to}. */
    public static boolean allowFlow(LevelAccessor level, BlockPos to, Direction direction, BlockState toState) {
        if (FLOWS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return FLOWS.dispatch(new ModFlowEvent(serverLevel, to.relative(direction.getOpposite()), to, toState));
    }

    /** False when a listener cancelled the fluid at {@code pos} turning into {@code next}. */
    public static boolean allowLevelChange(Level level, BlockPos pos, BlockState next) {
        if (LEVELS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return LEVELS.dispatch(new ModLevelEvent(serverLevel, pos, serverLevel.getBlockState(pos), next));
    }

    /** False when a listener cancelled the falling block starting to fall at, or landing on, {@code pos}. */
    public static boolean allowFalling(FallingBlockEntity falling, Level level, BlockPos pos) {
        if (FALLING.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return FALLING.dispatch(new ModFallingEvent(serverLevel, falling, pos));
    }

    /** True when the falling block tick hook asks to skip this tick. */
    public static boolean skipFallingTick(FallingBlockEntity falling) {
        FallingTickHook hook = fallingHook;
        return hook != null && falling.level() instanceof ServerLevel && hook.skipTick(new ModGameEntity(falling));
    }

    private static void dropNaturally(ServerLevel level, double x, double y, double z, ItemStack stack) {
        RandomSource random = level.getRandom();
        ItemEntity item = new ItemEntity(level,
                x + (random.nextFloat() * 0.5F) + 0.25D,
                y + (random.nextFloat() * 0.5F) + 0.25D,
                z + (random.nextFloat() * 0.5F) + 0.25D,
                stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    private static void fallEffect(ServerLevel level, double x, double y, double z, BlockPos block, BlockState state) {
        SoundType sound = state.getSoundType();
        level.playSound(null, x, y, z, sound.getBreakSound(), SoundSource.MASTER, 1F, 1F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, state),
                block.getX() + 0.5D, block.getY() + 0.5D, block.getZ() + 0.5D, 6, 0.25D, 0.5D, 0.25D, 0.1D);
    }

    private static void landEffect(ServerLevel level, BlockPos block, BlockState state) {
        SoundType sound = state.getSoundType();
        level.playSound(null, block.getX(), block.getY(), block.getZ(), sound.getPlaceSound(), SoundSource.MASTER, 1F, 1F);
        Item item = state.getBlock().asItem();
        double x = block.getX() + 0.5D;
        double y = block.getY() - 0.5D;
        double z = block.getZ() + 0.5D;
        if (item != Items.AIR) {
            level.sendParticles(VanillaApi.itemParticle(item), x, y, z, 24, 0.6D, 0.6D, 0.6D, 0.15D);
        } else {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, 24, 0.6D, 0.6D, 0.6D, 0.15D);
        }
    }

    private static GameLocation location(ServerLevel level, BlockPos pos) {
        return new GameLocation(ModReactWorld.idOf(level), pos.getX(), pos.getY(), pos.getZ());
    }

    private static int fluidLevel(BlockState state) {
        return state.hasProperty(BlockStateProperties.LEVEL) ? state.getValue(BlockStateProperties.LEVEL) : -1;
    }

    private abstract static class Cancellable implements HostEvent {
        private boolean cancelled;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static class ModBlockEvent extends Cancellable implements BlockEvent {
        protected final ServerLevel level;
        protected final BlockPos pos;
        protected final BlockState state;
        private final Player player;

        private ModBlockEvent(ServerLevel level, BlockPos pos, BlockState state, Player player) {
            this.level = level;
            this.pos = pos.immutable();
            this.state = state;
            this.player = player;
        }

        @Override
        public GameLocation location() {
            return ModBlockHost.location(level, pos);
        }

        @Override
        public String type() {
            return VanillaApi.blockTypeName(state.getBlock());
        }

        @Override
        public GameEntity player() {
            return player == null ? null : new ModGameEntity(player);
        }
    }

    private static final class ModPhysicsEvent extends ModBlockEvent implements PhysicsEvent {
        private final Block changed;

        private ModPhysicsEvent(ServerLevel level, BlockPos pos, BlockState state, Block changed) {
            super(level, pos, state, null);
            this.changed = changed;
        }

        @Override
        public String changedType() {
            return VanillaApi.blockTypeName(changed);
        }
    }

    private static final class ModPistonEvent extends Cancellable implements PistonEvent {
        private final ServerLevel level;
        private final BlockPos piston;
        private final boolean extending;
        private final List<BlockPos> moved;

        private ModPistonEvent(ServerLevel level, BlockPos piston, boolean extending, List<BlockPos> moved) {
            this.level = level;
            this.piston = piston.immutable();
            this.extending = extending;
            this.moved = moved;
        }

        @Override
        public GameLocation piston() {
            return location(level, piston);
        }

        @Override
        public boolean extending() {
            return extending;
        }

        @Override
        public List<GameLocation> blocks() {
            List<GameLocation> locations = new ArrayList<>(moved.size());
            for (BlockPos pos : moved) {
                locations.add(location(level, pos));
            }
            return locations;
        }
    }

    private static final class ModFlowEvent extends Cancellable implements FluidFlowEvent {
        private final ServerLevel level;
        private final BlockPos from;
        private final BlockPos to;
        private final BlockState toState;

        private ModFlowEvent(ServerLevel level, BlockPos from, BlockPos to, BlockState toState) {
            this.level = level;
            this.from = from;
            this.to = to.immutable();
            this.toState = toState;
        }

        @Override
        public GameLocation from() {
            return location(level, from);
        }

        @Override
        public String fromType() {
            return VanillaApi.blockTypeName(level.getBlockState(from).getBlock());
        }

        @Override
        public GameLocation to() {
            return location(level, to);
        }

        @Override
        public String toType() {
            return VanillaApi.blockTypeName(toState.getBlock());
        }
    }

    private static final class ModLevelEvent extends Cancellable implements FluidLevelEvent {
        private final ServerLevel level;
        private final BlockPos pos;
        private final BlockState current;
        private final BlockState next;

        private ModLevelEvent(ServerLevel level, BlockPos pos, BlockState current, BlockState next) {
            this.level = level;
            this.pos = pos.immutable();
            this.current = current;
            this.next = next;
        }

        @Override
        public GameLocation location() {
            return ModBlockHost.location(level, pos);
        }

        @Override
        public String type() {
            return VanillaApi.blockTypeName(current.getBlock());
        }

        @Override
        public int level() {
            return fluidLevel(current);
        }

        @Override
        public String newType() {
            return VanillaApi.blockTypeName(next.getBlock());
        }

        @Override
        public int newLevel() {
            return fluidLevel(next);
        }
    }

    private static final class ModFallingEvent extends Cancellable implements FallingBlockEvent {
        private final ServerLevel level;
        private final FallingBlockEntity falling;
        private final BlockPos pos;

        private ModFallingEvent(ServerLevel level, FallingBlockEntity falling, BlockPos pos) {
            this.level = level;
            this.falling = falling;
            this.pos = pos.immutable();
        }

        @Override
        public GameEntity entity() {
            return new ModGameEntity(falling);
        }

        @Override
        public GameLocation block() {
            return location(level, pos);
        }

        @Override
        public Object blockState() {
            return falling.getBlockState();
        }

        @Override
        public double velocityY() {
            return falling.getDeltaMovement().y;
        }
    }
}
