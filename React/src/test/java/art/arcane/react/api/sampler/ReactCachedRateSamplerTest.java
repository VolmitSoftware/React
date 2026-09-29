package art.arcane.react.api.sampler;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

class ReactCachedRateSamplerTest {

  @Test
  void onSampleReturnsZeroBeforeStartWhenBuffersAreNull() {
    TestRateSampler sampler = new TestRateSampler("backlog-growth-rate-test", 1000L);
    Assertions.assertEquals(0.0D, sampler.onSample(), 1.0E-9D);
  }

  @Test
  void firstSampleAfterStartWithoutIncrementsIsZero() {
    TestRateSampler sampler = new TestRateSampler("backlog-growth-rate-test", 1000L);
    sampler.start();
    Assertions.assertEquals(0.0D, sampler.onSample(), 1.0E-9D);
  }

  @Test
  void firstSampleAfterStartIsFiniteNonNegativeAndBounded() {
    TestRateSampler sampler = new TestRateSampler("backlog-growth-rate-test", 1000L);
    sampler.start();
    sampler.increment(20);
    double first = sampler.onSample();
    Assertions.assertTrue(Double.isFinite(first), "first sample was not finite: " + first);
    Assertions.assertTrue(first >= 0.0D, "first sample was negative: " + first);
    Assertions.assertTrue(first <= 20.0D, "first sample exceeded the delta of 20: " + first);
  }

  @Test
  void rateConvergesToHitsPerSecondWhenSampledEachInterval() {
    TestRateSampler sampler = new TestRateSampler("backlog-growth-rate-test", 1000L);
    sampler.start();
    int hitsPerInterval = 8;
    double rate = 0.0D;

    for (int i = 0; i < 6; i++) {
      sampler.increment(hitsPerInterval);
      rate = sampler.onSample();
    }

    Assertions.assertEquals((double) hitsPerInterval, rate, 1.0E-6D);
  }

  @Test
  void rateSettlesAtTrueRateWhenSamplesArriveJustAfterEachLatchWindow() {
    AtomicLong now = new AtomicLong(1_000_000L);
    TestRateSampler sampler = new TestRateSampler("hopper-updates-test", 1000L, now::get);
    sampler.start();
    double rate = 0.0D;

    for (int i = 0; i < 10; i++) {
      now.addAndGet(1050L);
      sampler.increment(105);
      rate = sampler.onSample();
    }

    Assertions.assertEquals(100.0D, rate, 1.0E-6D);
  }

  @Test
  void rateSettlesAtTrueRateAtOneSecondSpacing() {
    AtomicLong now = new AtomicLong(1_000_000L);
    TestRateSampler sampler = new TestRateSampler("physics-updates-test", 1000L, now::get);
    sampler.start();
    double rate = 0.0D;

    for (int i = 0; i < 10; i++) {
      now.addAndGet(i % 2 == 0 ? 1001L : 999L);
      sampler.increment(100);
      rate = sampler.onSample();
    }

    Assertions.assertEquals(100.0D, rate, 0.5D);
  }

  @Test
  void firstSampleLongAfterStartAveragesOverTheWholeElapsedTime() {
    AtomicLong now = new AtomicLong(1_000_000L);
    TestRateSampler sampler = new TestRateSampler("chunks-loaded-test", 1000L, now::get);
    sampler.start();

    now.addAndGet(60_000L);
    sampler.increment(6000);

    Assertions.assertEquals(100.0D, sampler.onSample(), 1.0E-6D);
  }

  @Property(tries = 50)
  void rateEqualsDeltaPerSecondForAnyHitCount(@ForAll @IntRange(min = 0, max = 100_000) int hitsPerInterval) {
    TestRateSampler sampler = new TestRateSampler("backlog-growth-rate-test", 1000L);
    sampler.start();
    double rate = 0.0D;

    for (int i = 0; i < 6; i++) {
      sampler.increment(hitsPerInterval);
      rate = sampler.onSample();
    }

    Assertions.assertEquals((double) hitsPerInterval, rate, 1.0E-6D);
  }

  private static final class TestRateSampler extends ReactCachedRateSampler {
    private TestRateSampler(String id, long sampleDelay) {
      super(id, sampleDelay);
    }

    private TestRateSampler(String id, long sampleDelay, LongSupplier clock) {
      super(id, sampleDelay, clock);
    }

    @Override
    public String formattedValue(double t) {
      return Double.toString(t);
    }

    @Override
    public String formattedSuffix(double t) {
      return "/s";
    }
  }
}
