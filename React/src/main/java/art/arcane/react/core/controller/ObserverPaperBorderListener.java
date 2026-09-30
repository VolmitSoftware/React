package art.arcane.react.core.controller;

import io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent;
import io.papermc.paper.event.world.border.WorldBorderCenterChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ObserverPaperBorderListener implements Listener {
  private final ObserverController observer;

  public ObserverPaperBorderListener(ObserverController observer) {
    this.observer = observer;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(WorldBorderCenterChangeEvent event) {
    observer.borderChanged(event.getWorld(), event.getNewCenter(), event.getWorldBorder().getSize());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(WorldBorderBoundsChangeEvent event) {
    observer.borderChanged(event.getWorld(), event.getWorldBorder().getCenter(), event.getNewSize());
  }
}
