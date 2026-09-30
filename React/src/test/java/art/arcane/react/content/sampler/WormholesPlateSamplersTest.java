package art.arcane.react.content.sampler;

import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class WormholesPlateSamplersTest {
  @Test
  void plateCostSamplersReadKeysThatReactRequestsFromWormholes() {
    List<RemoteIntegrationSampler> samplers = List.of(
        new SamplerWormholesPlateBuilds(),
        new SamplerWormholesPlateBytes(),
        new SamplerWormholesBlockEntities()
    );

    for (RemoteIntegrationSampler sampler : samplers) {
      Assertions.assertTrue(IntegrationMetricSchema.wormholesKeys().contains(sampler.metricKey()), sampler.getId());
      Assertions.assertNotEquals(sampler.getId(), MapMessages.localizedSamplerName(sampler.getId(), sampler.getId()));
    }
  }
}
