package art.arcane.react.core.controller;

import art.arcane.react.api.rendering.Graph;
import art.arcane.react.api.rendering.SamplerRenderer;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.api.sampler.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleControllerRestartTest {
  private final SampleController samples = new SampleController();

  @AfterEach
  void stop() {
    samples.stop();
    Graph.clearAll();
  }

  @Test
  void stopForgetsRenderersSoASecondStartHoldsNoOldSamplers() {
    samples.start();
    Sampler first = new FixedSampler("restart-probe", 3D);
    assertTrue(samples.registerSampler(first));
    SamplerRenderer firstRenderer = SamplerRenderer.of(first);
    assertSame(firstRenderer, SamplerRenderer.of(first));

    samples.stop();
    assertNotSame(firstRenderer, SamplerRenderer.of(first));
    SamplerRenderer.forget(first);

    samples.start();
    Sampler second = new FixedSampler("restart-probe", 5D);
    assertTrue(samples.registerSampler(second), "a second start registers the same sampler id again");
    assertSame(second, samples.getSampler("restart-probe"));
  }

  @Test
  void clearingGraphsDropsTheLastServersHistory() {
    Graph before = Graph.of(fixed("restart-graph", 7D));
    assertEquals(7D, before.get(0));

    Graph.clearAll();

    Graph after = Graph.of(fixed("restart-graph", 2D));
    assertNotSame(before, after);
    assertEquals(2D, after.get(0));
  }

  private static Sampler fixed(String id, double value) {
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.getId()).thenReturn(id);
    Mockito.when(sampler.sample()).thenReturn(value);
    return sampler;
  }

  private static final class FixedSampler extends ReactCachedSampler {
    private final double value;

    FixedSampler(String id, double value) {
      super(id, 0L);
      this.value = value;
    }

    @Override
    public double onSample() {
      return value;
    }

    @Override
    public String formattedValue(double sample) {
      return Double.toString(sample);
    }

    @Override
    public String formattedSuffix(double sample) {
      return "";
    }
  }
}
