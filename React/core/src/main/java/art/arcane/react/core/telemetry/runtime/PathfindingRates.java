package art.arcane.react.core.telemetry.runtime;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * Mob path searches per second, the share of them that was cancelled, and the path nodes those searches visited.
 * Counting is safe from any thread; {@link #refresh} turns the counts into rates once per sampling interval.
 */
public final class PathfindingRates {
  public static final String DECISIONS = "pathfinding-decisions-rate";
  public static final String CANCELLED = "pathfinding-cancelled-rate";
  public static final String NODES = "pathfinding-nodes-rate";
  public static final String NODES_UNSUPPORTED = "unsupported-platform";
  public static final List<RuntimeMetricSampler.Definition> DEFINITIONS = List.of(
      new RuntimeMetricSampler.Definition(DECISIONS, "Pathfinding Decisions", "/s", 1_000L),
      new RuntimeMetricSampler.Definition(CANCELLED, "Cancelled Pathfinding Decisions", "/s", 1_000L),
      new RuntimeMetricSampler.Definition(NODES, "Pathfinding Nodes Visited", "/s", 1_000L));
  private static final long MAX_SNAPSHOT_AGE_MS = 5_000L;
  private static final long MIN_WINDOW_NANOS = 250_000_000L;

  private final boolean countsNodes;
  private final LongAdder decisions = new LongAdder();
  private final LongAdder cancelled = new LongAdder();
  private final LongAdder nodes = new LongAdder();
  private volatile Snapshot snapshot = Snapshot.EMPTY;
  private long windowStartNanos;

  public PathfindingRates(boolean countsNodes) {
    this.countsNodes = countsNodes;
  }

  public void decision(boolean wasCancelled) {
    decisions.increment();
    if (wasCancelled) {
      cancelled.increment();
    }
  }

  public void visited(long count) {
    if (count > 0L) {
      nodes.add(count);
    }
  }

  /** Clears the counts and starts a new window at {@code nowNanos}; the published rates go stale until the next refresh. */
  public synchronized void reset(long nowNanos) {
    decisions.reset();
    cancelled.reset();
    nodes.reset();
    windowStartNanos = nowNanos;
    snapshot = Snapshot.EMPTY;
  }

  /** Publishes the rates since the previous refresh; a window shorter than a quarter second keeps counting instead. */
  public synchronized Snapshot refresh(long nowMs, long nowNanos) {
    long elapsedNanos = nowNanos - windowStartNanos;
    if (elapsedNanos < MIN_WINDOW_NANOS) {
      return snapshot;
    }
    double seconds = elapsedNanos / 1_000_000_000D;
    windowStartNanos = nowNanos;
    Snapshot next = new Snapshot(nowMs, decisions.sumThenReset() / seconds, cancelled.sumThenReset() / seconds,
        nodes.sumThenReset() / seconds);
    snapshot = next;
    return next;
  }

  public Snapshot snapshot() {
    return snapshot;
  }

  public RuntimeMetric metric(String id, long nowMs) {
    if (NODES.equals(id) && !countsNodes) {
      return RuntimeMetric.unavailable(NODES_UNSUPPORTED);
    }
    Snapshot current = snapshot;
    boolean available = current.sampledAtMs() > 0L && nowMs >= current.sampledAtMs()
        && nowMs - current.sampledAtMs() <= MAX_SNAPSHOT_AGE_MS;
    double value;
    if (DECISIONS.equals(id)) {
      value = current.decisionsPerSecond();
    } else if (CANCELLED.equals(id)) {
      value = current.cancelledPerSecond();
    } else if (NODES.equals(id)) {
      value = current.nodesPerSecond();
    } else {
      return RuntimeMetric.unavailable("source-unavailable");
    }
    return new RuntimeMetric(value, current.sampledAtMs(), available, available ? "" : "source-unavailable");
  }

  public record Snapshot(long sampledAtMs, double decisionsPerSecond, double cancelledPerSecond, double nodesPerSecond) {
    public static final Snapshot EMPTY = new Snapshot(0L, 0D, 0D, 0D);
  }
}
