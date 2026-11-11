package art.arcane.react.api.sampler;

import art.arcane.react.React;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

/** Cache-interval behavior the sampler inherits from its ChronoLatch. */
class ReactCachedSamplerLatchTest {

  @Test
  void valueStaysCachedForTheIntervalThenRefreshesOnce() throws InterruptedException {
    CountingSampler sampler = new CountingSampler(150L);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      sampler.start();
      long started = System.nanoTime();
      Assertions.assertEquals(1D, sampler.sample());

      while (elapsedMs(started) < 60L) {
        Assertions.assertEquals(1D, sampler.sample());
        Thread.sleep(2L);
      }
      Assertions.assertEquals(1, sampler.calls.get());

      while (sampler.calls.get() < 2 && elapsedMs(started) < 5_000L) {
        sampler.sample();
        Thread.sleep(2L);
      }
      Assertions.assertEquals(2, sampler.calls.get());
      Assertions.assertTrue(elapsedMs(started) >= 150L, "refreshed after " + elapsedMs(started) + " ms");

      for (int index = 0; index < 50; index++) {
        Assertions.assertEquals(2D, sampler.sample());
      }
      Assertions.assertEquals(2, sampler.calls.get());
    }
  }

  @Test
  void captureSharesTheSampleInterval() {
    CountingSampler sampler = new CountingSampler(60_000L);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      sampler.start();

      Assertions.assertEquals(1D, sampler.capture());
      Assertions.assertEquals(1D, sampler.sample());
      Assertions.assertEquals(1D, sampler.captureReading().value());
    }

    Assertions.assertEquals(1, sampler.calls.get());
  }

  @Test
  void restartInsideTheIntervalKeepsTheCachedValue() {
    CountingSampler sampler = new CountingSampler(60_000L);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      sampler.start();
      Assertions.assertEquals(1D, sampler.sample());
      sampler.stop();
      sampler.start();

      Assertions.assertEquals(1D, sampler.sample());
    }

    Assertions.assertEquals(1, sampler.calls.get());
  }

  @Test
  void stoppedReadsDoNotConsumeTheInterval() {
    CountingSampler sampler = new CountingSampler(60_000L);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      for (int index = 0; index < 10; index++) {
        Assertions.assertEquals(0D, sampler.sample());
      }
      sampler.start();

      Assertions.assertEquals(1D, sampler.sample());
    }

    Assertions.assertEquals(1, sampler.calls.get());
  }

  @Test
  void zeroIntervalSamplesAtMostOncePerMillisecond() throws InterruptedException {
    CountingSampler sampler = new CountingSampler(0L);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      sampler.start();
      Thread.sleep(3L);
      long started = System.currentTimeMillis();
      Assertions.assertEquals(1D, sampler.sample());

      for (int index = 0; index < 2_000; index++) {
        sampler.sample();
      }
      long elapsed = System.currentTimeMillis() - started;

      Assertions.assertTrue(sampler.calls.get() <= elapsed + 1L,
          sampler.calls.get() + " samples in " + elapsed + " ms");
    }
  }

  @Test
  void maximumIntervalNeverSamples() {
    CountingSampler sampler = new CountingSampler(Long.MAX_VALUE);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      sampler.start();

      Assertions.assertEquals(0D, sampler.sample());
      Assertions.assertEquals(0D, sampler.sample());
    }

    Assertions.assertEquals(0, sampler.calls.get());
  }

  private static long elapsedMs(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000L;
  }

  private static final class CountingSampler extends ReactCachedSampler {
    private final AtomicInteger calls = new AtomicInteger();

    private CountingSampler(long sampleDelay) {
      super("cached-latch", sampleDelay);
    }

    @Override
    public double onSample() {
      return calls.incrementAndGet();
    }

    @Override
    public String formattedValue(double t) {
      return Double.toString(t);
    }

    @Override
    public String formattedSuffix(double t) {
      return "u";
    }
  }
}
