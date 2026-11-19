package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactOperations;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtectionMaskCache {
  private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

  public Entry get(UUID id) {
    return id == null ? null : entries.get(id);
  }

  public int maskOr(UUID id, int fallback) {
    Entry entry = get(id);
    return entry == null ? fallback : entry.mask();
  }

  public void put(UUID id, int mask, String owners, long nowMs) {
    if (id == null) {
      return;
    }

    entries.put(id, new Entry(ReactOperations.sanitize(mask), owners == null ? "" : owners, nowMs));
  }

  public void invalidate(UUID id) {
    if (id != null) {
      entries.remove(id);
    }
  }

  public void clear() {
    entries.clear();
  }

  public int size() {
    return entries.size();
  }

  public int sweep(long nowMs, long retentionMs) {
    if (entries.isEmpty()) {
      return 0;
    }

    long cutoff = nowMs - Math.max(0L, retentionMs);
    int removed = 0;
    Iterator<Map.Entry<UUID, Entry>> iterator = entries.entrySet().iterator();

    while (iterator.hasNext()) {
      Map.Entry<UUID, Entry> entry = iterator.next();

      if (entry.getValue().hydratedAtMs() < cutoff) {
        iterator.remove();
        removed++;
      }
    }

    return removed;
  }

  public int protectedCount() {
    int count = 0;

    for (Entry entry : entries.values()) {
      if (entry.mask() != ReactOperations.NONE) {
        count++;
      }
    }

    return count;
  }

  public record Entry(int mask, String owners, long hydratedAtMs) {
  }
}
