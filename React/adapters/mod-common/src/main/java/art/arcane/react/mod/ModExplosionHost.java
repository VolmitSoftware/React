package art.arcane.react.mod;

import art.arcane.react.platform.ExplosionHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HostListeners;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Explosions on mods, fed by the explosion mixins where Bukkit fires its block and entity explode events, its TNT spawn
 * event and the explosion packet path. Mod servers keep their dimensions loaded, so world unloads never fire here.
 */
public final class ModExplosionHost implements ExplosionHost {
    private static final double PACKET_RANGE_SQUARED = 4096.0D;
    private static final HostListeners<Explosion> EXPLOSIONS = new HostListeners<>();
    private static final HostListeners<ExplosionHost.PrimedTnt> PRIMED = new HostListeners<>();
    private static final HostListeners<WorldUnload> UNLOADS = new HostListeners<>();
    private static volatile PacketHooks packetHooks;

    private final Supplier<MinecraftServer> server;

    public ModExplosionHost(Supplier<MinecraftServer> server) {
        this.server = server;
    }

    static void clear() {
        EXPLOSIONS.clear();
        PRIMED.clear();
        UNLOADS.clear();
        packetHooks = null;
    }

    @Override
    public Subscription onExplode(ListenerPriority priority, boolean ignoreCancelled, Consumer<Explosion> listener) {
        return EXPLOSIONS.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onTntPrimed(ListenerPriority priority, boolean ignoreCancelled, Consumer<ExplosionHost.PrimedTnt> listener) {
        return PRIMED.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onWorldUnload(ListenerPriority priority, boolean ignoreCancelled, Consumer<WorldUnload> listener) {
        return UNLOADS.subscribe(priority, ignoreCancelled, listener);
    }

    /**
     * Runs the explode listeners for an explosion about to break {@code blocks}, which listeners may shorten; a
     * cancelled explosion breaks nothing.
     */
    public static void exploding(ServerLevel level, Vec3 center, float radius, boolean fromBlock, boolean destroys, boolean decay,
                                 List<BlockPos> blocks) {
        if (EXPLOSIONS.isEmpty()) {
            return;
        }
        UUID worldId = ModReactWorld.idOf(level);
        GameLocation location = fromBlock
                ? at(worldId, BlockPos.containing(center))
                : new GameLocation(worldId, center.x, center.y, center.z);
        float yield = decay && radius > 0F ? 1.0F / radius : 1.0F;
        ModExplosion explosion = new ModExplosion(level, worldId, location, fromBlock, destroys, yield, blocks);
        if (!EXPLOSIONS.dispatch(explosion)) {
            blocks.clear();
        }
    }

    /** False when a listener cancelled the spawn of this primed TNT. */
    public static boolean priming(net.minecraft.world.entity.item.PrimedTnt tnt) {
        if (PRIMED.isEmpty()) {
            return true;
        }
        return PRIMED.dispatch(new ModPrimedTnt(tnt));
    }

    /**
     * True when React takes over the vanilla packets of this explosion; the caller then sends none.
     */
    public static boolean suppressPackets(ServerLevel level, double x, double y, double z, float radius) {
        PacketHooks hooks = packetHooks;
        if (hooks == null) {
            return false;
        }
        UUID worldId = ModReactWorld.idOf(level);
        hooks.observe(new GameLocation(worldId, x, y, z));
        int recipients = 0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) < PACKET_RANGE_SQUARED) {
                recipients++;
            }
        }
        return recipients > 0 && hooks.suppress(worldId, x, y, z, radius, recipients);
    }

    @Override
    public String blockType(GameLocation location) {
        ServerLevel level = level(location);
        return level == null ? null : typeName(level.getBlockState(pos(location)));
    }

    @Override
    public void dropContents(GameLocation location) {
        ServerLevel level = level(location);
        if (level == null) {
            return;
        }
        BlockPos pos = pos(location);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container container) {
            Containers.dropContents(level, pos, container);
            container.clearContent();
        } else {
            Block.dropResources(level.getBlockState(pos), level, pos, blockEntity);
        }
    }

    @Override
    public void clear(GameLocation location, boolean fastBlockUpdates) {
        ServerLevel level = level(location);
        if (level == null) {
            return;
        }
        BlockPos pos = pos(location);
        BlockState air = Blocks.AIR.defaultBlockState();
        if (!fastBlockUpdates) {
            level.setBlock(pos, air, Block.UPDATE_ALL);
            return;
        }
        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
        if (neighbourNeedsUpdate(level, pos)) {
            level.updateNeighborsAt(pos, Blocks.AIR);
        }
    }

    private static boolean neighbourNeedsUpdate(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            BlockState state = level.getBlockState(neighbour);
            if (state.isAir()) {
                continue;
            }
            if (!state.getFluidState().isEmpty() || state.getBlock() instanceof FallingBlock || !state.canSurvive(level, neighbour)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void explode(GameLocation location, float power) {
        ServerLevel level = level(location);
        if (level != null) {
            level.explode(null, location.x(), location.y(), location.z(), power, false, Level.ExplosionInteraction.MOB);
        }
    }

    @Override
    public void installPacketHooks(PacketHooks hooks) {
        packetHooks = hooks;
        hooks.installed(true, true);
    }

    @Override
    public void uninstallPacketHooks() {
        packetHooks = null;
    }

    @Override
    public boolean canBroadcast() {
        return server.get() != null;
    }

    @Override
    public WorldState worldState(UUID worldId) {
        MinecraftServer current = server.get();
        if (current == null) {
            return WorldState.UNKNOWN;
        }
        return ModWorldHost.level(current, worldId) == null ? WorldState.UNLOADED : WorldState.LOADED;
    }

    @Override
    public boolean broadcastMergedExplosion(UUID worldId, double x, double y, double z, float radius, double rangeBlocks) {
        MinecraftServer current = server.get();
        ServerLevel level = current == null ? null : ModWorldHost.level(current, worldId);
        if (level == null) {
            return false;
        }
        current.getPlayerList().broadcast(null, x, y, z, rangeBlocks, level.dimension(), VanillaApi.mergedExplosionPacket(x, y, z, radius));
        return true;
    }

    private ServerLevel level(GameLocation location) {
        MinecraftServer current = server.get();
        return current == null || location == null ? null : ModWorldHost.level(current, location.worldId());
    }

    private static BlockPos pos(GameLocation location) {
        return new BlockPos(location.blockX(), location.blockY(), location.blockZ());
    }

    private static GameLocation at(UUID worldId, BlockPos pos) {
        return new GameLocation(worldId, pos.getX(), pos.getY(), pos.getZ());
    }

    private static String typeName(BlockState state) {
        return VanillaApi.blockTypeName(state.getBlock());
    }

    private static final class ModExplosion implements Explosion {
        private final ServerLevel level;
        private final UUID worldId;
        private final GameLocation location;
        private final boolean fromBlock;
        private final boolean destroys;
        private final float yield;
        private final List<BlockPos> blocks;
        private boolean cancelled;

        private ModExplosion(ServerLevel level, UUID worldId, GameLocation location, boolean fromBlock, boolean destroys, float yield,
                             List<BlockPos> blocks) {
            this.level = level;
            this.worldId = worldId;
            this.location = location;
            this.fromBlock = fromBlock;
            this.destroys = destroys;
            this.yield = yield;
            this.blocks = blocks;
        }

        @Override
        public GameLocation location() {
            return location;
        }

        @Override
        public boolean fromBlock() {
            return fromBlock;
        }

        @Override
        public boolean destroysBlocks() {
            return destroys;
        }

        @Override
        public float yield() {
            return yield;
        }

        @Override
        public List<ExplodedBlock> blocks() {
            List<ExplodedBlock> exploded = new ArrayList<>(blocks.size());
            for (BlockPos pos : blocks) {
                exploded.add(exploded(pos));
            }
            return exploded;
        }

        @Override
        public void removeBlocks(Predicate<ExplodedBlock> filter) {
            blocks.removeIf(pos -> filter.test(exploded(pos)));
        }

        private ExplodedBlock exploded(BlockPos pos) {
            return new ExplodedBlock(at(worldId, pos), typeName(level.getBlockState(pos)));
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

    private static final class ModPrimedTnt implements ExplosionHost.PrimedTnt {
        private final net.minecraft.world.entity.item.PrimedTnt tnt;
        private boolean cancelled;

        private ModPrimedTnt(net.minecraft.world.entity.item.PrimedTnt tnt) {
            this.tnt = tnt;
        }

        @Override
        public int fuseTicks() {
            return tnt.getFuse();
        }

        @Override
        public void setFuseTicks(int ticks) {
            tnt.setFuse(ticks);
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
