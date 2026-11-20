package art.arcane.react.mod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;

/** A 1.20.1 portal trip: where the entity arrives, found before it leaves. */
public record PortalTrip(Entity entity, ServerLevel level, PortalInfo info) {
    private static final ThreadLocal<PortalTrip> RESUMING = new ThreadLocal<>();

    /** The trip being resumed for {@code entity}, which skips the portal listeners a second time. */
    public static PortalTrip resuming(Entity entity) {
        PortalTrip trip = RESUMING.get();
        return trip != null && trip.entity == entity ? trip : null;
    }

    boolean resume() {
        RESUMING.set(this);
        try {
            return entity.changeDimension(level) != null;
        } finally {
            RESUMING.remove();
        }
    }
}
