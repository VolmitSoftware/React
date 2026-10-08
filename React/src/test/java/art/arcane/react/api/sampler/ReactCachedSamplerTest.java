package art.arcane.react.api.sampler;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ReactCachedSamplerTest {

  @Test
  void cachedReadingRetainsCaptureTimeUntilTheCacheRefreshes() {
    CountingCachedSampler sampler = new CountingCachedSampler("capture-time", 60_000L, 42D);
    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      Assertions.assertFalse(sampler.captureReading().available());
      sampler.start();
      Sampler.Reading first = sampler.captureReading();
      Assertions.assertTrue(first.available());
      Assertions.assertTrue(first.sampledAtMs() > 0L);
      Assertions.assertEquals(first, sampler.captureReading());
      Assertions.assertEquals(1, sampler.sampleCalls.get());
      sampler.stop();
      Assertions.assertFalse(sampler.captureReading().available());
    }
  }

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
    CountingCachedSampler sampler = new CountingCachedSampler("tick-time-test", 1L, 13.0D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      sampler.start();
      Assertions.assertEquals(13.0D, sampler.sample(), 1.0E-9D);
      Assertions.assertEquals(1, sampler.sampleCalls.get());

      long deadline = System.currentTimeMillis() + 3000L;
      while (sampler.sampleCalls.get() < 2 && System.currentTimeMillis() < deadline) {
        sampler.sample();
        Thread.onSpinWait();
      }
    }

    Assertions.assertTrue(
        sampler.sampleCalls.get() >= 2,
        "expected at least a second recompute after the cache window, got " + sampler.sampleCalls.get());
  }

  @Test
  void refreshBehindValueReachesSampleAsSoonAsTheMainThreadJobCompletes() {
    RefreshBehindSampler sampler = new RefreshBehindSampler("players-test", 60_000L, 5.0D);
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> j = Mockito.mockStatic(J.class)) {
      j.when(J::isPrimaryThread).thenReturn(false);
      j.when(() -> J.s(ArgumentMatchers.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });
      sampler.start();

      Assertions.assertFalse(sampler.captureReading().available());
      Assertions.assertEquals(1, queued.size());
      queued.getFirst().run();

      Assertions.assertEquals(5.0D, sampler.sample(), 1.0E-9D);
      Assertions.assertTrue(sampler.captureReading().sampledAtMs() > 0L);
      Assertions.assertTrue(sampler.captureReading().available());
    }
  }

  @Test
  void refreshBehindSamplerIsUnavailableUntilTheFirstRefreshCompletes() {
    RefreshBehindSampler sampler = new RefreshBehindSampler("chunk-tickets-test", 60_000L, 3.0D);
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> j = Mockito.mockStatic(J.class)) {
      j.when(J::isPrimaryThread).thenReturn(false);
      j.when(() -> J.s(ArgumentMatchers.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });
      sampler.start();

      sampler.sample();
      Assertions.assertFalse(sampler.isSampleAvailable());

      queued.getFirst().run();
      Assertions.assertTrue(sampler.isSampleAvailable());
    }
  }

  @Test
  void samplerWithoutMainThreadRefreshStaysAvailable() {
    CountingCachedSampler sampler = new CountingCachedSampler("memory-free-test", 60_000L, 1.0D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      sampler.start();
      sampler.sample();
    }

    Assertions.assertTrue(sampler.isSampleAvailable());
  }

  private static final class RefreshBehindSampler extends ReactCachedSampler {
    private final double value;

    private RefreshBehindSampler(String id, long sampleDelay, double value) {
      super(id, sampleDelay);
      this.value = value;
    }

    @Override
    public double onSample() {
      return sampleOnMainThread(() -> value);
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
