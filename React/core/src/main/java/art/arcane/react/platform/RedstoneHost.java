package art.arcane.react.platform;

import java.util.function.Consumer;

/**
 * Redstone power changes, where Bukkit fires its block redstone event. Listeners can hold a block at its old power.
 */
public interface RedstoneHost {
  interface Signal {
    GameLocation location();

    String type();

    int oldCurrent();

    int newCurrent();

    void setNewCurrent(int current);
  }

  Subscription onSignal(ListenerPriority priority, Consumer<Signal> listener);
}
