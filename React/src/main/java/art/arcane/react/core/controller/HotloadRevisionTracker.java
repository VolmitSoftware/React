package art.arcane.react.core.controller;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

final class HotloadRevisionTracker {
  private final ConcurrentHashMap<Path, AtomicLong> revisions = new ConcurrentHashMap<>();

  long touch(Path path) {
    Path normalized = normalize(path);
    AtomicLong revision = revisions.computeIfAbsent(normalized, ignored -> new AtomicLong());
    synchronized (revision) {
      return revision.incrementAndGet();
    }
  }

  long touchAndRun(Path path, Runnable action) {
    Path normalized = normalize(path);
    AtomicLong revision = revisions.computeIfAbsent(normalized, ignored -> new AtomicLong());
    synchronized (revision) {
      long next = revision.incrementAndGet();
      action.run();
      return next;
    }
  }

  long current(Path path) {
    AtomicLong revision = revisions.get(normalize(path));
    return revision == null ? 0L : revision.get();
  }

  boolean isCurrent(Path path, long revision) {
    return current(path) == revision;
  }

  GuardedBoolean runBooleanIfCurrent(Path path, long expectedRevision, BooleanSupplier action) {
    AtomicLong revision = revisions.computeIfAbsent(normalize(path), ignored -> new AtomicLong());
    synchronized (revision) {
      if (revision.get() != expectedRevision) {
        return new GuardedBoolean(false, false);
      }
      return new GuardedBoolean(true, action.getAsBoolean());
    }
  }

  boolean runIfCurrent(Path path, long expectedRevision, Runnable action) {
    AtomicLong revision = revisions.computeIfAbsent(normalize(path), ignored -> new AtomicLong());
    synchronized (revision) {
      if (revision.get() != expectedRevision) {
        return false;
      }
      action.run();
      return true;
    }
  }

  void clear() {
    revisions.clear();
  }

  private Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
  }

  record GuardedBoolean(boolean current, boolean value) {
  }
}
