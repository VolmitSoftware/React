package art.arcane.react.api.sampler;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
