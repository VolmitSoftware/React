package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.dto.SamplerDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetricsSerializerTest {
  @Test
  void serializesOnlyCurrentScalarMetadata() {
    SamplerDto value = new MetricsSerializer().toDto(new FakeSampler());

    assertEquals("tick-time", value.id);
    assertEquals(42D, value.value);
    assertEquals("ms", value.suffix);
    assertEquals("42.0", value.display);
    assertTrue(value.available);
  }

  private static final class FakeSampler implements Sampler {
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
      return 42D;
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
