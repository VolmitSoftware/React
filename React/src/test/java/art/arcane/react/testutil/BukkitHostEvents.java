package art.arcane.react.testutil;

import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.RedstoneHost;
import art.arcane.react.platform.bukkit.BukkitEvents;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;

/**
 * Host events backed by real Bukkit events, the way the Bukkit hosts hand them to features, so tests can drive a
 * feature's host listeners without a running server.
 */
public final class BukkitHostEvents {
  private BukkitHostEvents() {
  }

  public static RedstoneHost.Signal signal(BlockRedstoneEvent event) {
    return new RedstoneHost.Signal() {
      @Override
      public GameLocation location() {
        return BukkitEvents.location(event.getBlock());
      }

      @Override
      public String type() {
        return event.getBlock().getType().name();
      }

      @Override
      public int oldCurrent() {
        return event.getOldCurrent();
      }

      @Override
      public int newCurrent() {
        return event.getNewCurrent();
      }

      @Override
      public void setNewCurrent(int current) {
        event.setNewCurrent(current);
      }
    };
  }

  public static BlockHost.PhysicsEvent physics(BlockPhysicsEvent event) {
    return new BlockHost.PhysicsEvent() {
      @Override
      public String changedType() {
        return event.getChangedType().name();
      }

      @Override
      public GameLocation location() {
        return BukkitEvents.location(event.getBlock());
      }

      @Override
      public String type() {
        return event.getBlock().getType().name();
      }

      @Override
      public boolean isCancelled() {
        return event.isCancelled();
      }

      @Override
      public void cancel() {
        event.setCancelled(true);
      }
    };
  }

}
