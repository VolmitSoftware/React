package art.arcane.react.mod;

import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HostEvent;
import art.arcane.react.platform.HostListeners;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.RedstoneHost;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Redstone power changes on mods, fed by the redstone mixins at the points where CraftBukkit fires its block redstone
 * event: wire, repeaters, comparators, torches, observers, lamps, buttons, levers, pressure plates, rails, doors,
 * trapdoors, fence gates, note blocks, sculk sensors and tripwire hooks.
 */
public final class ModRedstoneHost implements RedstoneHost {
    private static final HostListeners<ModSignal> SIGNALS = new HostListeners<>();

    @Override
    public Subscription onSignal(ListenerPriority priority, Consumer<Signal> listener) {
        return SIGNALS.subscribe(priority, false, listener::accept);
    }

    static void clear() {
        SIGNALS.clear();
    }

    /**
     * The power the block at {@code pos} should change to after listeners ran; {@code newCurrent} when none changed it.
     */
    public static int signal(Level level, BlockPos pos, BlockState state, int oldCurrent, int newCurrent) {
        if (oldCurrent == newCurrent || SIGNALS.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return newCurrent;
        }
        ModSignal signal = new ModSignal(serverLevel, pos.immutable(), state, oldCurrent, newCurrent);
        SIGNALS.dispatch(signal);
        return signal.newCurrent;
    }

    public static boolean isListening() {
        return !SIGNALS.isEmpty();
    }

    /**
     * True when a listener kept the block at {@code pos} from switching between off and on, judged the way CraftBukkit
     * judges its binary redstone changes: any power above zero counts as on.
     */
    public static boolean held(Level level, BlockPos pos, BlockState state, boolean turningOn) {
        return signal(level, pos, state, turningOn ? 0 : 15, turningOn ? 15 : 0) > 0 != turningOn;
    }

    static String typeName(BlockState state) {
        String key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return key.startsWith("minecraft:") ? key.substring("minecraft:".length()).toUpperCase(Locale.ROOT) : key;
    }

    private static final class ModSignal implements Signal, HostEvent {
        private final ServerLevel level;
        private final BlockPos pos;
        private final BlockState state;
        private final int oldCurrent;
        private int newCurrent;

        private ModSignal(ServerLevel level, BlockPos pos, BlockState state, int oldCurrent, int newCurrent) {
            this.level = level;
            this.pos = pos;
            this.state = state;
            this.oldCurrent = oldCurrent;
            this.newCurrent = newCurrent;
        }

        @Override
        public GameLocation location() {
            return new GameLocation(ModReactWorld.idOf(level), pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public String type() {
            return typeName(state);
        }

        @Override
        public int oldCurrent() {
            return oldCurrent;
        }

        @Override
        public int newCurrent() {
            return newCurrent;
        }

        @Override
        public void setNewCurrent(int current) {
            newCurrent = current;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void cancel() {
        }
    }
}
