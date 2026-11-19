package art.arcane.react.core.telemetry;

import java.util.ArrayDeque;

public final class SlidingCounterWindow {
  private static final double MS_PER_MINUTE = 60_000D;

  private final long windowMs;
  private final ArrayDeque<Point> points;

  public SlidingCounterWindow(long windowMs) {
    this.windowMs = Math.max(1L, windowMs);
    this.points = new ArrayDeque<>(64);
  }

  public void record(long atMs, long value) {
    Point newest = points.peekLast();
    if (newest != null && (value < newest.value() || atMs < newest.atMs())) {
      points.clear();
    }

    points.addLast(new Point(atMs, value));
    evictBefore(atMs - windowMs);
  }

  public boolean isEmpty() {
    return points.isEmpty();
  }

  public void clear() {
    points.clear();
  }

  public double perMinute() {
    return fraction() * MS_PER_MINUTE;
  }

  public double fraction() {
    if (points.size() < 2) {
      return 0D;
    }

    Point oldest = points.peekFirst();
    Point newest = points.peekLast();
    long spanMs = newest.atMs() - oldest.atMs();
    if (spanMs <= 0L) {
      return 0D;
    }

    return (newest.value() - oldest.value()) / (double) spanMs;
  }

  private void evictBefore(long cutoffMs) {
    while (points.size() > 1) {
      Point oldest = points.pollFirst();
      Point next = points.peekFirst();
      if (next.atMs() > cutoffMs) {
        points.addFirst(oldest);
        return;
      }
    }
  }

  private record Point(long atMs, long value) {
  }
}
