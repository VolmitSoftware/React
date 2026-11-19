package art.arcane.react.core.controller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

final class HotloadPendingQueue {
  private static final long UNSET_NANOS = Long.MIN_VALUE;

  private final long cooldownNanos;
  private final long tombstoneGraceNanos;
  private final LongSupplier nanoClock;
  private final Map<Path, PendingChange> pending;
  private long lastCompletionNanos;
  private boolean draining;

  HotloadPendingQueue(long cooldownNanos, long tombstoneGraceNanos, LongSupplier nanoClock) {
    this.cooldownNanos = Math.max(0L, cooldownNanos);
    this.tombstoneGraceNanos = Math.max(0L, tombstoneGraceNanos);
    this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    this.pending = new LinkedHashMap<>();
    this.lastCompletionNanos = UNSET_NANOS;
  }

  public synchronized void enqueue(Path path, boolean present) {
    if (path == null) {
      return;
    }

    Path normalized = path.toAbsolutePath().normalize();
    PendingChange previous = pending.get(normalized);
    long missingSinceNanos = UNSET_NANOS;
    if (!present) {
      missingSinceNanos = previous == null || previous.missingSinceNanos() == UNSET_NANOS
          ? nanoClock.getAsLong()
          : previous.missingSinceNanos();
    }
    pending.put(normalized, new PendingChange(normalized, missingSinceNanos));
  }

  public synchronized List<ReadyChange> beginDrain() {
    if (draining || pending.isEmpty()) {
      return List.of();
    }

    long now = nanoClock.getAsLong();
    if (lastCompletionNanos != UNSET_NANOS && now - lastCompletionNanos < cooldownNanos) {
      return List.of();
    }

    List<ReadyChange> ready = new ArrayList<>(pending.size());
    for (PendingChange change : pending.values()) {
      if (change.missingSinceNanos() != UNSET_NANOS
          && now - change.missingSinceNanos() < tombstoneGraceNanos) {
        continue;
      }
      ready.add(new ReadyChange(change.path(), change.missingSinceNanos() == UNSET_NANOS));
    }
    if (ready.isEmpty()) {
      return List.of();
    }

    for (ReadyChange change : ready) {
      pending.remove(change.path());
    }
    draining = true;
    return List.copyOf(ready);
  }

  public synchronized void finishDrain() {
    if (!draining) {
      return;
    }
    draining = false;
    lastCompletionNanos = nanoClock.getAsLong();
  }

  public synchronized void clear() {
    pending.clear();
    draining = false;
    lastCompletionNanos = UNSET_NANOS;
  }

  synchronized int pendingCount() {
    return pending.size();
  }

  private record PendingChange(Path path, long missingSinceNanos) {
  }

  record ReadyChange(Path path, boolean present) {
  }
}
