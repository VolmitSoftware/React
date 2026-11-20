package art.arcane.react.api.rendering;

import art.arcane.react.platform.DirtyRect;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each receiver of a map has not seen yet. A receiver is a viewer, or on Bukkit a map canvas. A
 * receiver's first take is always the full surface.
 */
public final class MapDelivery {
  private final Map<Integer, Map<UUID, Pending>> byMap = new ConcurrentHashMap<>();

  public void changed(int mapId, DirtyRect rect) {
    if (rect == null || rect.isEmpty()) {
      return;
    }

    Map<UUID, Pending> receivers = byMap.get(mapId);
    if (receivers == null) {
      return;
    }

    for (Pending pending : receivers.values()) {
      pending.add(rect);
    }
  }

  public DirtyRect take(int mapId, UUID receiver, long now) {
    Map<UUID, Pending> receivers = byMap.computeIfAbsent(mapId, ignored -> new ConcurrentHashMap<>());
    Pending pending = receivers.get(receiver);
    if (pending == null) {
      receivers.put(receiver, new Pending(now));
      return DirtyRect.FULL;
    }

    return pending.take(now);
  }

  public void reset(int mapId, UUID receiver) {
    Map<UUID, Pending> receivers = byMap.get(mapId);
    if (receivers != null) {
      receivers.remove(receiver);
    }
  }

  public void forget(int mapId) {
    byMap.remove(mapId);
  }

  public void prune(long now, long retentionMs) {
    byMap.values().forEach(receivers -> receivers.values().removeIf(pending -> now - pending.lastTakenMs > retentionMs));
    byMap.values().removeIf(Map::isEmpty);
  }

  public void clear() {
    byMap.clear();
  }

  private static final class Pending {
    private DirtyRect rect = DirtyRect.EMPTY;
    private volatile long lastTakenMs;

    private Pending(long now) {
      lastTakenMs = now;
    }

    private synchronized void add(DirtyRect changed) {
      rect = rect.union(changed);
    }

    private synchronized DirtyRect take(long now) {
      lastTakenMs = now;
      DirtyRect taken = rect;
      rect = DirtyRect.EMPTY;
      return taken;
    }
  }
}
