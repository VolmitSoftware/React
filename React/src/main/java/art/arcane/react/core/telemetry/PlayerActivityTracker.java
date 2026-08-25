package art.arcane.react.core.telemetry;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerActivityTracker {
  private static final long RATE_WINDOW_MS = 60_000L;
  private static final long UNIQUE_WINDOW_MS = 86_400_000L;
  private static final long UNIQUE_PRUNE_INTERVAL_MS = 60_000L;

  private final ConcurrentLinkedDeque<Long> joins = new ConcurrentLinkedDeque<>();
  private final ConcurrentLinkedDeque<Long> quits = new ConcurrentLinkedDeque<>();
  private final Map<UUID, Long> uniquePlayers = new ConcurrentHashMap<>();
  private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
  private final AtomicLong nextUniquePruneMs = new AtomicLong();

  public void recordJoin(UUID playerId, long nowMs) {
    joins.addLast(nowMs);
    recordActive(playerId, nowMs);
  }

  public void recordQuit(UUID playerId, long nowMs) {
    quits.addLast(nowMs);
    if (playerId != null) {
      activePlayers.remove(playerId);
      uniquePlayers.put(playerId, nowMs);
    }
  }

  public void recordActive(UUID playerId, long nowMs) {
    if (playerId != null) {
      activePlayers.add(playerId);
      uniquePlayers.put(playerId, nowMs);
    }
  }

  public double joinsPerMinute(long nowMs) {
    pruneEvents(joins, nowMs - RATE_WINDOW_MS);
    return joins.size();
  }

  public double quitsPerMinute(long nowMs) {
    pruneEvents(quits, nowMs - RATE_WINDOW_MS);
    return quits.size();
  }

  public int uniquePlayers(long nowMs) {
    long pruneAtMs = nextUniquePruneMs.get();
    if (nowMs >= pruneAtMs && nextUniquePruneMs.compareAndSet(pruneAtMs, nowMs + UNIQUE_PRUNE_INTERVAL_MS)) {
      long cutoffMs = nowMs - UNIQUE_WINDOW_MS;
      uniquePlayers.entrySet().removeIf(
          entry -> entry.getValue() <= cutoffMs && !activePlayers.contains(entry.getKey())
      );
    }
    return uniquePlayers.size();
  }

  public void clear() {
    joins.clear();
    quits.clear();
    uniquePlayers.clear();
    activePlayers.clear();
    nextUniquePruneMs.set(0L);
  }

  private void pruneEvents(ConcurrentLinkedDeque<Long> events, long cutoffMs) {
    Long timestamp = events.peekFirst();
    while (timestamp != null && timestamp <= cutoffMs) {
      events.pollFirst();
      timestamp = events.peekFirst();
    }
  }
}
