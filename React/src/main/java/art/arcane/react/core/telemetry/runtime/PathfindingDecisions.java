package art.arcane.react.core.telemetry.runtime;

import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.concurrent.atomic.LongAdder;

public final class PathfindingDecisions implements Listener {
  private final LongAdder decisions = new LongAdder();
  private final LongAdder cancelled = new LongAdder();

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPathfind(EntityPathfindEvent event) {
    decisions.increment();
    if (event.isCancelled()) {
      cancelled.increment();
    }
  }

  public Snapshot snapshot(long nowMs, long elapsedNanos) {
    double seconds = Math.max(1L, elapsedNanos) / 1_000_000_000D;
    return new Snapshot(nowMs, decisions.sumThenReset() / seconds, cancelled.sumThenReset() / seconds);
  }

  public record Snapshot(long sampledAtMs, double decisionsPerSecond, double cancelledPerSecond) {
    public static final Snapshot EMPTY = new Snapshot(0L, 0D, 0D);
  }
}
