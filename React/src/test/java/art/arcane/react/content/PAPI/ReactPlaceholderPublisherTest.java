package art.arcane.react.content.PAPI;

import art.arcane.react.api.sampler.Sampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactPlaceholderPublisherTest {
  @Test
  void shouldPublishNaNWhenTheSamplerReportsUnavailable() {
    FakeSampler sampler = new FakeSampler(0D, false);

    assertTrue(Double.isNaN(ReactPlaceholderPublisher.read(sampler)));
    assertEquals(1, sampler.samples);
  }

  @Test
  void shouldPublishTheSampledValueWhenTheSamplerIsAvailable() {
    FakeSampler sampler = new FakeSampler(0.28D, true);

    assertEquals(0.28D, ReactPlaceholderPublisher.read(sampler), 1.0E-9D);
    assertEquals(1, sampler.samples);
  }

  @Test
  void shouldPublishNaNForAMissingSampler() {
    assertTrue(Double.isNaN(ReactPlaceholderPublisher.read(null)));
  }

  private static final class FakeSampler implements Sampler {
    private final double value;
    private final boolean available;
    private int samples;

    private FakeSampler(double value, boolean available) {
      this.value = value;
      this.available = available;
    }

    @Override
    public String getId() {
      return "tick-time";
    }

    @Override
    public String getName() {
      return "Tick Time";
    }

    @Override
    public double sample() {
      samples++;
      return value;
    }

    @Override
    public boolean isSampleAvailable() {
      return available;
    }

    @Override
    public String formattedValue(double sampled) {
      return String.valueOf(sampled);
    }

    @Override
    public String formattedSuffix(double sampled) {
      return "ms";
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void render() {
    }
  }
}
