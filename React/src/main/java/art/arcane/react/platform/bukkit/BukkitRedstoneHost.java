package art.arcane.react.platform.bukkit;

import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.RedstoneHost;
import art.arcane.react.platform.Subscription;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.function.Consumer;

public class BukkitRedstoneHost implements RedstoneHost {
  @Override
  public Subscription onSignal(ListenerPriority priority, Consumer<Signal> listener) {
    return BukkitEvents.subscribe(BlockRedstoneEvent.class, priority, false, event -> listener.accept(new BukkitSignal(event)));
  }

  private record BukkitSignal(BlockRedstoneEvent event) implements Signal {
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
  }
}
