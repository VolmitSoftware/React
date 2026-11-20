package art.arcane.react.core.telemetry.runtime;

import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class PaperPathfindingDecisions implements Listener {
  private final PathfindingRates rates;

  public PaperPathfindingDecisions(PathfindingRates rates) {
    this.rates = rates;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPathfind(EntityPathfindEvent event) {
    rates.decision(event.isCancelled());
  }
}
