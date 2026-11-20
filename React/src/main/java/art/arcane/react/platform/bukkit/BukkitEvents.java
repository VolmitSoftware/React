package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.Subscription;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Registers one Bukkit handler per host subscription, at the subscription's own priority, so features keep the exact
 * handler order and cancelled-event rules they had as Bukkit listeners.
 */
public final class BukkitEvents {
  private BukkitEvents() {
  }

  public static <T extends Event> Subscription subscribe(Class<T> type, ListenerPriority priority, boolean ignoreCancelled,
                                                         Consumer<T> handler) {
    Plugin plugin = React.instance;
    if (plugin == null || Bukkit.getServer() == null) {
      return Subscription.NONE;
    }
    Listener owner = new Listener() {
    };
    Bukkit.getPluginManager().registerEvent(type, owner, EventPriority.valueOf(priority.name()), (listener, event) -> {
      if (type.isInstance(event)) {
        handler.accept(type.cast(event));
      }
    }, plugin, ignoreCancelled);
    return () -> HandlerList.unregisterAll(owner);
  }

  public static GameLocation location(Block block) {
    return new GameLocation(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
  }
}
