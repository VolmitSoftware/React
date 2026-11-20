package art.arcane.react.content.sampler;

import art.arcane.react.platform.IntegrationMetricsHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

class SamplerGlossTickMsTest {

  @Test
  void suffixNamesTheMillisecondsPerSecondUnit() {
    IntegrationMetricsHost host = Mockito.mock(IntegrationMetricsHost.class);
    Mockito.when(host.storedSample("gloss", IntegrationMetricSchema.GLOSS_TICK_MS)).thenReturn(IntegrationMetricSample.available(
        IntegrationMetricSchema.descriptor(IntegrationMetricSchema.GLOSS_TICK_MS), 3.2D, System.currentTimeMillis()));
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    Mockito.when(platform.integrationMetricsHost()).thenReturn(Optional.of(host));
    ReactPlatforms.unbind();
    ReactPlatforms.bind(platform);
    try {
      Assertions.assertEquals(" ms/s", new SamplerGlossTickMs().formattedSuffix(3.2D));
    } finally {
      ReactPlatforms.unbind();
    }
  }
}
