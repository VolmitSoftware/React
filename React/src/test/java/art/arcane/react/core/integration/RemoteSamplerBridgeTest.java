package art.arcane.react.core.integration;

import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

class RemoteSamplerBridgeTest {
  @Test
  void liveAdaptRefreshPrunesDetailsNoLongerPublishedByProvider() {
    RemoteSamplerBridge bridge = new RemoteSamplerBridge();
    String dynamicKey = IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_PREFIX
        + "excavation-spelunker."
        + IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_OPS;
    long now = System.currentTimeMillis();
    IntegrationMetricSample sample = IntegrationMetricSample.available(
        IntegrationMetricSchema.descriptor(dynamicKey),
        42D,
        now
    );
    bridge.updatePluginSamples("adapt", Set.of(dynamicKey), Map.of(dynamicKey, sample), "unavailable");

    bridge.updatePluginSamples("adapt", IntegrationMetricSchema.adaptKeys(), Map.of(), "metric-unavailable");

    Assertions.assertFalse(bridge.snapshot("adapt").containsKey(dynamicKey));
    Assertions.assertFalse(bridge.getSample("adapt", dynamicKey).available());
    Assertions.assertEquals("metric-not-published", bridge.getSample("adapt", dynamicKey).message());
  }

  @Test
  void unavailableTransitionAlsoInvalidatesPreviouslyTrackedDynamicKeys() {
    RemoteSamplerBridge bridge = new RemoteSamplerBridge();
    String dynamicKey = IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_PREFIX
        + "excavation-spelunker."
        + IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_OPS;
    long now = System.currentTimeMillis();
    IntegrationMetricSample sample = IntegrationMetricSample.available(
        IntegrationMetricSchema.descriptor(dynamicKey),
        42D,
        now
    );
    bridge.updatePluginSamples("adapt", Set.of(dynamicKey), Map.of(dynamicKey, sample), "unavailable");

    bridge.markPluginUnavailable("adapt", IntegrationMetricSchema.adaptKeys(), "provider-missing");

    IntegrationMetricSample invalidated = bridge.getSample("adapt", dynamicKey);
    Assertions.assertFalse(invalidated.available());
    Assertions.assertEquals("provider-missing", invalidated.message());
  }
}
