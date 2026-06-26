package art.arcane.react.api.sampler;

import art.arcane.react.React;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

class ReactCachedSamplerTest {

  @Test
  void invokesValueSupplierOnceForManyRapidCallsWithinCacheWindow() {
    CountingCachedSampler sampler = new CountingCachedSampler("processor-load-test", 60_000L, 42.0D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      sampler.start();

      for (int i = 0; i < 1000; i++) {
        double value = sampler.sample();
        Assertions.assertEquals(42.0D, value, 1.0E-9D);
      }
    }

    Assertions.assertEquals(1, sampler.sampleCalls.get());
  }

  @Test
  void cachedValueIsReturnedForEveryCallAfterTheWindowOpens() {
    CountingCachedSampler sampler = new CountingCachedSampler("memory-used-test", 60_000L, 7.5D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      sampler.start();

      double first = sampler.sample();
      double second = sampler.sample();
      double third = sampler.sample();

      Assertions.assertEquals(7.5D, first, 1.0E-9D);
      Assertions.assertEquals(first, second, 1.0E-9D);
      Assertions.assertEquals(first, third, 1.0E-9D);
    }

    Assertions.assertEquals(1, sampler.sampleCalls.get());
  }

  @Test
  void doesNotInvokeValueSupplierBeforeStart() {
    CountingCachedSampler sampler = new CountingCachedSampler("chunk-load-test", 60_000L, 99.0D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      double value = sampler.sample();
      Assertions.assertEquals(0.0D, value, 1.0E-9D);
    }

    Assertions.assertEquals(0, sampler.sampleCalls.get());
  }

  @Test
  void recomputesValueAfterCacheWindowElapses() {
    CountingCachedSampler sampler = new CountingCachedSampler("tick-time-test", 40L, 13.0D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      sampler.start();
      Assertions.assertEquals(13.0D, sampler.sample(), 1.0E-9D);
      Assertions.assertEquals(1, sampler.sampleCalls.get());

      long deadline = System.currentTimeMillis() + 3000L;
      while (sampler.sampleCalls.get() < 2 && System.currentTimeMillis() < deadline) {
        sampler.sample();
        sleepQuietly(5L);
      }
    }

    Assertions.assertTrue(
        sampler.sampleCalls.get() >= 2,
        "expected at least a second recompute after the cache window, got " + sampler.sampleCalls.get());
  }

  private static void sleepQuietly(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static final class CountingCachedSampler extends ReactCachedSampler {
    private final AtomicInteger sampleCalls = new AtomicInteger(0);
    private final double value;

    private CountingCachedSampler(String id, long sampleDelay, double value) {
      super(id, sampleDelay);
      this.value = value;
    }

    @Override
    public double onSample() {
      sampleCalls.incrementAndGet();
      return value;
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
