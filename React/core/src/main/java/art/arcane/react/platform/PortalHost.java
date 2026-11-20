package art.arcane.react.platform;

import java.util.function.Consumer;

/**
 * Players and entities about to travel through a portal.
 */
public interface PortalHost {
  interface Portal extends HostEvent {
    GameEntity entity();

    boolean isPlayer();

    GameLocation from();

    /**
     * Where the portal leads; null when the platform has not resolved it yet.
     */
    GameLocation to();
  }

  Subscription onPortal(ListenerPriority priority, boolean ignoreCancelled, Consumer<Portal> listener);
}
