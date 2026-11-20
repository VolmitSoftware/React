package art.arcane.react.mod;

import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HopperHost;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hopper transfers on mods, fed by the hopper mixin at the two points where Bukkit fires its item move event: a
 * hopper pushing one item out, and a hopper pulling one item from the container above.
 */
public final class ModHopperHost implements HopperHost {
    private static final List<MoveListener> LISTENERS = new CopyOnWriteArrayList<>();

    @Override
    public Subscription onMove(MoveListener listener) {
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }

    static void clear() {
        LISTENERS.clear();
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

    private static boolean dispatch(ModMove move) {
        for (MoveListener listener : LISTENERS) {
            if (move.cancelled) {
                return false;
            }
            listener.onMove(move);
        }
        return !move.cancelled;
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
            if (!state.hasProperty(HopperBlock.FACING)) {
                return false;
            }
            Direction facing = state.getValue(HopperBlock.FACING);
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
