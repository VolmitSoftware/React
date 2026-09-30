package art.arcane.react.content.sampler;

import art.arcane.react.React;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.function.Supplier;

class TickClockTest {
  private static final long MS = 1_000_000L;
  private static final long BASE = 10_000_000_000L;

  @Test
  void workTimePercentilesCoverTheServerTrailingWindow() {
    SimulatedServerTickTimes server = new SimulatedServerTickTimes();
    TickClock clock = new TickClock(server);
    long at = BASE;
    for (int i = 0; i < 100; i++) {
      at = server.tick(clock, at, ((i % 50) + 1) * MS);
    }
    clock.tick(at);
    TickClock.Snapshot snapshot = clock.snapshot();

    Assertions.assertEquals(100, server.size());
    Assertions.assertTrue(snapshot.workTimeMode());
    Assertions.assertTrue(snapshot.hasHistory());
    Assertions.assertEquals(25.5D, snapshot.percentile(0.50D, 1200), 1.0E-9D);
    Assertions.assertEquals(48D, snapshot.percentile(0.95D, 1200), 1.0E-9D);
    Assertions.assertEquals(50D, snapshot.percentile(0.99D, 1200), 1.0E-9D);
    Assertions.assertEquals(25.5D, snapshot.averageTickMS(100), 1.0E-9D);
  }

  @Test
  void lateFirstTickTimesKeepWorkTimeMode() {
    SimulatedServerTickTimes server = new SimulatedServerTickTimes();
    TickClock clock = new TickClock(server);

    server.ticks(clock, BASE, 60, 5L * MS);
    TickClock.Snapshot snapshot = clock.snapshot();

    Assertions.assertTrue(snapshot.workTimeMode());
    Assertions.assertTrue(snapshot.hasHistory());
    Assertions.assertEquals(5D, snapshot.averageTickMS(100), 1.0E-9D);
    Assertions.assertEquals(5D, snapshot.percentile(0.99D, 1200), 1.0E-9D);
  }

  @Test
  void persistentlyEmptyTickTimesLatchGapMode() {
    CountingSource source = new CountingSource(new long[0]);
    TickClock clock = new TickClock(source);
    long at = BASE;
    clock.tick(at);
    Assertions.assertTrue(clock.snapshot().workTimeMode());
    Assertions.assertFalse(clock.snapshot().hasHistory());

    for (int i = 0; i < 30; i++) {
      at += 50L * MS;
      clock.tick(at);
    }
    Assertions.assertTrue(clock.snapshot().workTimeMode());
    Assertions.assertFalse(clock.snapshot().hasHistory());

    for (int i = 0; i < 20; i++) {
      at += 50L * MS;
      clock.tick(at);
    }
    TickClock.Snapshot snapshot = clock.snapshot();
    Assertions.assertFalse(snapshot.workTimeMode());
    Assertions.assertTrue(snapshot.hasHistory());
    Assertions.assertEquals(50D, snapshot.percentile(0.50D, 1200), 1.0E-9D);
    Assertions.assertEquals(50D, snapshot.averageTickMS(100), 1.0E-9D);

    int calls = source.calls;
    for (int i = 0; i < 20; i++) {
      at += 50L * MS;
      clock.tick(at);
    }
    Assertions.assertEquals(calls, source.calls);
    Assertions.assertFalse(clock.snapshot().workTimeMode());
  }

  @Test
  void gapModeLatchesWhenTheServerHasNoTickTimes() {
    CountingSource source = new CountingSource(null);
    TickClock clock = new TickClock(source);

    clock.tick(BASE);
    Assertions.assertFalse(clock.snapshot().workTimeMode());
    Assertions.assertFalse(clock.snapshot().hasHistory());

    long at = BASE;
    for (int i = 0; i < 120; i++) {
      at += 50L * MS;
      clock.tick(at);
    }
    at += 250L * MS;
    clock.tick(at);
    TickClock.Snapshot snapshot = clock.snapshot();

    Assertions.assertEquals(1, source.calls);
    Assertions.assertFalse(snapshot.workTimeMode());
    Assertions.assertTrue(snapshot.hasHistory());
    Assertions.assertEquals(50D, snapshot.percentile(0.50D, 1200), 1.0E-9D);
    Assertions.assertEquals(250D, snapshot.percentile(1.0D, 1200), 1.0E-9D);
    Assertions.assertEquals(250D, snapshot.percentile(1.0D, 1), 1.0E-9D);
    Assertions.assertEquals(250D, snapshot.percentile(0.50D, -5), 1.0E-9D);
    Assertions.assertEquals(150D, snapshot.averageTickMS(2), 1.0E-9D);
  }

  @Test
  void steadyTicksAreNeverSpikesInGapMode() {
    TickClock clock = new TickClock(() -> null);
    long[] jitter = {50L, 49L, 51L, 50L, 52L, 48L, 50L, 50L, 53L, 50L};
    long at = BASE;
    for (int i = 0; i < 200; i++) {
      at += jitter[i % jitter.length] * MS;
      clock.tick(at);
    }

    Assertions.assertTrue(clock.snapshot().hasHistory());
    Assertions.assertEquals(0D, clock.snapshot().spikesPerMinute(at, 50D, 60_000L), 1.0E-9D);

    at += 120L * MS;
    clock.tick(at);
    for (int i = 0; i < 6; i++) {
      at += 50L * MS;
      clock.tick(at);
    }

    Assertions.assertEquals(1D, clock.snapshot().spikesPerMinute(at, 50D, 60_000L), 1.0E-9D);
    Assertions.assertEquals(0D, clock.snapshot().spikesPerMinute(at, 210D, 60_000L), 1.0E-9D);
    Assertions.assertEquals(0D, clock.snapshot().spikesPerMinute(at + 61_000L * MS, 50D, 60_000L), 1.0E-9D);
  }

  @Test
  void workTimeSpikesCountOnlyTicksFinishedSinceTheLastEpoch() {
    SimulatedServerTickTimes server = new SimulatedServerTickTimes();
    TickClock clock = new TickClock(server);
    long at = server.ticks(clock, BASE, 100, 5L * MS);
    Assertions.assertEquals(0D, clock.snapshot().spikesPerMinute(at, 50D, 60_000L), 1.0E-9D);

    at = server.tick(clock, at, 70L * MS);
    at = server.tick(clock, at, 51L * MS);
    at = server.tick(clock, at, 50L * MS);
    at = server.tick(clock, at, 12L * MS);
    at = server.ticks(clock, at, 6, 5L * MS);

    Assertions.assertEquals(2D, clock.snapshot().spikesPerMinute(at, 50D, 60_000L), 1.0E-9D);
    Assertions.assertEquals(4D, clock.snapshot().spikesPerMinute(at, 50D, 30_000L), 1.0E-9D);

    at = server.ticks(clock, at, 200, 5L * MS);
    Assertions.assertEquals(2D, clock.snapshot().spikesPerMinute(at, 50D, 60_000L), 1.0E-9D);
  }

  @Test
  void spikeRateCountsEachSlowTickOnce() {
    SimulatedServerTickTimes server = new SimulatedServerTickTimes();
    TickClock clock = new TickClock(server);
    long at = BASE;
    for (int i = 0; i < 1000; i++) {
      at = server.tick(clock, at, i % 200 == 199 ? 70L * MS : 5L * MS);
    }
    at = server.ticks(clock, at, 6, 5L * MS);

    Assertions.assertEquals(5D, clock.snapshot().spikesPerMinute(at, 50D, 60_000L), 1.0E-9D);
  }

  @Test
  void ticksPerSecondCountsTicksInTheTrailingWindow() {
    TickClock clock = new TickClock(() -> null);
    long at = BASE;
    for (int i = 0; i < 196; i++) {
      at += 50L * MS;
      clock.tick(at);
    }

    Assertions.assertEquals(20D, clock.snapshot().ticksPerSecond(at + 25L * MS), 1.0E-9D);
    Assertions.assertEquals(20D, clock.snapshot().ticksPerSecond(at), 1.0E-9D);
    Assertions.assertEquals(4D, clock.snapshot().ticksPerSecond(at + 4_000L * MS), 1.0E-9D);
    Assertions.assertEquals(0D, clock.snapshot().ticksPerSecond(at + 6_000L * MS), 1.0E-9D);
  }

  @Test
  void ticksPerSecondSpansTheWholeWindowUnderLoad() {
    Assertions.assertEquals(10D, steadyTicksPerSecond(100L), 1.0E-9D);
    Assertions.assertEquals(4D, steadyTicksPerSecond(250L), 1.0E-9D);
    Assertions.assertEquals(2D, steadyTicksPerSecond(500L), 1.0E-9D);
    Assertions.assertEquals(1D, steadyTicksPerSecond(1_000L), 1.0E-9D);
  }

  @Test
  void snapshotRefreshesOncePerEpoch() {
    TickClock clock = new TickClock(() -> null);
    clock.tick(BASE);
    TickClock.Snapshot first = clock.snapshot();

    clock.tick(BASE + 50L * MS);
    clock.tick(BASE + 100L * MS);
    Assertions.assertSame(first, clock.snapshot());

    clock.tick(BASE + 250L * MS);
    Assertions.assertNotSame(first, clock.snapshot());
    Assertions.assertEquals(300D, clock.millisSinceLastTick(BASE + 550L * MS), 1.0E-9D);
  }

  @Test
  void emptyClockReportsNothingAvailable() {
    TickClock clock = new TickClock(() -> null);
    TickClock.Snapshot snapshot = clock.snapshot();

    Assertions.assertFalse(snapshot.hasHistory());
    Assertions.assertFalse(snapshot.hasTicks());
    Assertions.assertEquals(0D, snapshot.percentile(0.5D, 100), 1.0E-9D);
    Assertions.assertEquals(0D, snapshot.averageTickMS(100), 1.0E-9D);
    Assertions.assertEquals(0D, snapshot.spikesPerMinute(BASE, 50D, 60_000L), 1.0E-9D);
    Assertions.assertEquals(0D, snapshot.ticksPerSecond(BASE), 1.0E-9D);
    Assertions.assertEquals(0D, clock.millisSinceLastTick(BASE), 1.0E-9D);
  }

  @Test
  void ownersShareOneRegisteredListener() {
    React previous = React.instance;
    React plugin = Mockito.mock(React.class);
    React.instance = plugin;
    try {
      TickClock clock = new TickClock(() -> null);
      Object first = new Object();
      Object second = new Object();

      clock.acquire(first);
      clock.acquire(first);
      clock.acquire(second);
      Mockito.verify(plugin, Mockito.times(1)).registerListener(clock);

      clock.release(first);
      Mockito.verify(plugin, Mockito.never()).unregisterListener(clock);
      clock.release(second);
      Mockito.verify(plugin, Mockito.times(1)).unregisterListener(clock);

      clock.tick(BASE);
      clock.tick(BASE + 50L * MS);
      clock.acquire(first);
      Assertions.assertFalse(clock.snapshot().hasTicks());
      Mockito.verify(plugin, Mockito.times(2)).registerListener(clock);
    } finally {
      React.instance = previous;
    }
  }

  @Property(tries = 100)
  void sortedPercentileStaysWithinBoundsAndGrowsWithRank(
      @ForAll @Size(min = 1, max = 64) List<@DoubleRange(min = 0D, max = 500D) Double> values,
      @ForAll @DoubleRange(min = 0D, max = 1D) double low,
      @ForAll @DoubleRange(min = 0D, max = 1D) double high
  ) {
    double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    double lower = Math.min(low, high);
    double upper = Math.max(low, high);

    double a = SamplerMath.percentileSorted(sorted, lower);
    double b = SamplerMath.percentileSorted(sorted, upper);

    Assertions.assertTrue(a >= sorted[0] - 1.0E-9D && a <= sorted[sorted.length - 1] + 1.0E-9D);
    Assertions.assertTrue(b >= a - 1.0E-9D);
  }

  private static double steadyTicksPerSecond(long gapMillis) {
    TickClock clock = new TickClock(() -> null);
    long at = BASE;
    for (int i = 0; i < 100; i++) {
      at += gapMillis * MS;
      clock.tick(at);
    }

    return clock.snapshot().ticksPerSecond(at + 50L * MS);
  }

  private static final class CountingSource implements Supplier<long[]> {
    private final long[] value;
    private int calls;

    private CountingSource(long[] value) {
      this.value = value;
    }

    @Override
    public long[] get() {
      calls++;
      return value;
    }
  }
}
