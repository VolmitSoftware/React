package art.arcane.react.core.telemetry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SlidingCounterWindowTest {
  private static final long WINDOW_MS = 60_000L;

  @Test
  void oneCollectionEveryTwentySecondsReadsThreePerMinuteOnEveryPoll() {
    SlidingCounterWindow window = new SlidingCounterWindow(WINDOW_MS);
    long collections = 0L;
    window.record(0L, collections);
    for (long atMs = 1_000L; atMs <= 600_000L; atMs += 1_000L) {
      if (atMs % 20_000L == 0L) {
        collections++;
      }
      window.record(atMs, collections);
      if (atMs >= WINDOW_MS) {
        Assertions.assertEquals(3D, window.perMinute(), 0.051D);
      }
    }
  }

  @Test
  void firstReadingsUseTheLifetimeAnchorInsteadOfOneSecondSpikes() {
    SlidingCounterWindow window = new SlidingCounterWindow(WINDOW_MS);
    window.record(0L, 0L);
    window.record(120_000L, 6L);
    window.record(121_000L, 7L);

    Assertions.assertEquals(7D * 60_000D / 121_000D, window.perMinute(), 1.0E-9D);
  }

  @Test
  void spanUsesRealElapsedTimeBetweenIrregularSamples() {
    SlidingCounterWindow window = new SlidingCounterWindow(WINDOW_MS);
    window.record(0L, 0L);
    window.record(70_000L, 1_400L);
    window.record(75_500L, 1_510L);
    window.record(130_000L, 2_600L);

    Assertions.assertEquals(0.02D, window.fraction(), 1.0E-9D);
    Assertions.assertEquals(1_200D, window.perMinute(), 1.0E-9D);
  }

  @Test
  void samplesOlderThanTheWindowStopContributing() {
    SlidingCounterWindow window = new SlidingCounterWindow(WINDOW_MS);
    window.record(0L, 0L);
    window.record(10_000L, 50L);
    window.record(80_000L, 50L);
    window.record(140_000L, 50L);

    Assertions.assertEquals(0D, window.perMinute(), 1.0E-9D);
  }

  @Test
  void counterResetRestartsTheWindow() {
    SlidingCounterWindow window = new SlidingCounterWindow(WINDOW_MS);
    window.record(0L, 0L);
    window.record(30_000L, 100L);
    window.record(31_000L, 5L);

    Assertions.assertEquals(0D, window.perMinute(), 1.0E-9D);

    window.record(32_000L, 6L);
    Assertions.assertEquals(60D, window.perMinute(), 1.0E-9D);
  }

  @Test
  void emptyOrSinglePointWindowReadsZero() {
    SlidingCounterWindow window = new SlidingCounterWindow(WINDOW_MS);
    Assertions.assertEquals(0D, window.perMinute(), 1.0E-9D);

    window.record(5_000L, 12L);
    Assertions.assertEquals(0D, window.perMinute(), 1.0E-9D);
    Assertions.assertEquals(0D, window.fraction(), 1.0E-9D);
  }
}
