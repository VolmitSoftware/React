package art.arcane.react.content.sampler;

import java.util.ArrayDeque;
import java.util.function.Supplier;

final class SimulatedServerTickTimes implements Supplier<long[]> {
  static final long MS = 1_000_000L;
  private static final long NOMINAL_TICK_NANOS = 50L * MS;
  private static final long WINDOW_NANOS = 5_000L * MS;

  private final ArrayDeque<long[]> ticks = new ArrayDeque<>();

  long tick(TickClock clock, long startNanos, long durationNanos) {
    clock.tick(startNanos);
    long endNanos = startNanos + durationNanos;
    ticks.addLast(new long[]{endNanos, durationNanos});
    while (!ticks.isEmpty() && endNanos - ticks.peekFirst()[0] > WINDOW_NANOS) {
      ticks.pollFirst();
    }

    return Math.max(endNanos, startNanos + NOMINAL_TICK_NANOS);
  }

  long ticks(TickClock clock, long startNanos, int count, long durationNanos) {
    long at = startNanos;
    for (int i = 0; i < count; i++) {
      at = tick(clock, at, durationNanos);
    }

    return at;
  }

  int size() {
    return ticks.size();
  }

  @Override
  public long[] get() {
    long[] raw = new long[ticks.size()];
    int index = 0;
    for (long[] tick : ticks) {
      raw[index++] = tick[1];
    }

    return raw;
  }
}
