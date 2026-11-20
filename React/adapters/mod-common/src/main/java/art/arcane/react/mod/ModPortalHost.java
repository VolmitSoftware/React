package art.arcane.react.mod;

import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HostListeners;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.PortalHost;
import art.arcane.react.platform.Subscription;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.function.Consumer;

/**
 * Portal travel on mods, fed by the portal mixin right before an entity uses the destination its portal resolved.
 */
public final class ModPortalHost implements PortalHost {
    private static final HostListeners<Portal> PORTALS = new HostListeners<>();

    @Override
    public Subscription onPortal(ListenerPriority priority, boolean ignoreCancelled, Consumer<Portal> listener) {
        return PORTALS.subscribe(priority, ignoreCancelled, listener);
    }

    static void clear() {
        PORTALS.clear();
    }

    /**
     * False when a listener cancelled the trip of {@code entity} to the given position in {@code destination}.
     */
    public static boolean allow(Entity entity, ServerLevel destination, double x, double y, double z) {
        if (PORTALS.isEmpty() || !(entity.level() instanceof ServerLevel)) {
            return true;
        }
        GameLocation to = destination == null ? null : new GameLocation(ModReactWorld.idOf(destination), x, y, z);
        return PORTALS.dispatch(new ModPortal(entity, ModGameEntity.location(entity), to));
    }

    private static final class ModPortal implements Portal {
        private final Entity entity;
        private final GameLocation from;
        private final GameLocation to;
        private boolean cancelled;

        private ModPortal(Entity entity, GameLocation from, GameLocation to) {
            this.entity = entity;
            this.from = from;
            this.to = to;
        }

        @Override
        public GameEntity entity() {
            return new ModGameEntity(entity);
        }

        @Override
        public boolean isPlayer() {
            return entity instanceof ServerPlayer;
        }

        @Override
        public GameLocation from() {
            return from;
        }

        @Override
        public GameLocation to() {
            return to;
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
