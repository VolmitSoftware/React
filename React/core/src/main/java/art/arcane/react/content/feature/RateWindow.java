package art.arcane.react.content.feature;

import java.util.Arrays;

final class RateWindow<E extends Enum<E>> {
  private final int[] counts;
  private long startedAt;

  RateWindow(int counters) {
    counts = new int[Math.max(1, counters)];
  }

  synchronized boolean tryAcquire(E counter, int maximum, long now, long windowMs) {
    if (now - startedAt > Math.max(0L, windowMs)) {
      reset(now);
    }

    int count = ++counts[counter.ordinal()];
    return count <= Math.max(0, maximum);
  }

  synchronized void reset(long now) {
    startedAt = now;
    Arrays.fill(counts, 0);
  }
}
