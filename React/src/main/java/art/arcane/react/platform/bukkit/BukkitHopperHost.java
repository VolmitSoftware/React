package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HopperHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.project.world.HopperMoveContext;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BukkitHopperHost implements HopperHost, Listener {
  private final List<MoveListener> listeners = new CopyOnWriteArrayList<>();
  private boolean registered;

  @Override
  public synchronized Subscription onMove(MoveListener listener) {
    listeners.add(listener);
    if (!registered && React.instance != null) {
      React.instance.registerListener(this);
      registered = true;
    }
    return () -> remove(listener);
  }

  private synchronized void remove(MoveListener listener) {
    listeners.remove(listener);
    if (listeners.isEmpty() && registered) {
      registered = false;
      if (React.instance != null) {
        React.instance.unregisterListener(this);
      }
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void on(InventoryMoveItemEvent event) {
    BukkitMove move = new BukkitMove(event);
    for (MoveListener listener : listeners) {
      if (event.isCancelled()) {
        return;
      }
      listener.onMove(move);
    }
  }

  private static final class BukkitMove implements Move {
    private final InventoryMoveItemEvent event;
    private final HopperMoveContext context;
    private GameLocation hopper;
    private boolean resolved;

    private BukkitMove(InventoryMoveItemEvent event) {
      this.event = event;
      this.context = HopperMoveContext.resolve(event);
    }

    @Override
    public GameLocation hopper() {
      if (!resolved) {
        resolved = true;
        hopper = BukkitGameEntity.location(context.hopperLocation());
      }
      return hopper;
    }

    @Override
    public boolean sourceIsHopper() {
      return context.sourceHopperBlock() != null;
    }

    @Override
    public boolean destinationIsHopper() {
      return context.destinationHopperBlock() != null;
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }
  }
}
