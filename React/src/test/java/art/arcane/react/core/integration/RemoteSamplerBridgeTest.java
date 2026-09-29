package art.arcane.react.core.integration;

import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationMetricType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
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

  @Test
  void completeGroupRefreshReplacesUnloadedWorldsWithoutACap() {
    RemoteSamplerBridge bridge = new RemoteSamplerBridge();
    long now = System.currentTimeMillis();
    IntegrationMetricGroup overworld = worldGroup("minecraft:overworld", 12D, now);
    IntegrationMetricGroup nether = worldGroup("minecraft:the_nether", 8D, now);

    bridge.updatePluginGroups("iris", List.of(overworld, nether));
    bridge.updatePluginGroups("iris", List.of(nether));

    Assertions.assertNull(bridge.getGroup("iris", "world", "minecraft:overworld"));
    Assertions.assertEquals(nether, bridge.getGroup("iris", "world", "minecraft:the_nether"));
    Assertions.assertEquals(1, bridge.groups("iris", "world").size());
  }

  @Test
  void unavailableTransitionClearsScopedWorldSnapshots() {
    RemoteSamplerBridge bridge = new RemoteSamplerBridge();
    bridge.updatePluginGroups("iris", List.of(worldGroup(
        "minecraft:overworld",
        12D,
        System.currentTimeMillis()
    )));

    bridge.markPluginUnavailable("iris", IntegrationMetricSchema.irisKeys(), "provider-missing");

    Assertions.assertTrue(bridge.groups("iris", "world").isEmpty());
  }

  @Test
  void rejectsWrongUnitsAndStaleSamplesAsInvalidData() {
    RemoteSamplerBridge bridge = new RemoteSamplerBridge();
    String key = IntegrationMetricSchema.IRIS_LOADED_CHUNKS;
    IntegrationMetricDescriptor wrongUnit = new IntegrationMetricDescriptor(
        key,
        IntegrationMetricType.LONG,
        "milliseconds",
        Map.of("plugin", "iris")
    );
    IntegrationMetricSample malformed = IntegrationMetricSample.available(
        wrongUnit,
        12D,
        System.currentTimeMillis()
    );

    bridge.updatePluginSamples("iris", Set.of(key), Map.of(key, malformed), "missing");

    IntegrationMetricSample rejected = bridge.getSample("iris", key);
    Assertions.assertFalse(rejected.available());
    Assertions.assertEquals("invalid-sample", rejected.message());

    IntegrationMetricSample stale = IntegrationMetricSample.available(
        IntegrationMetricSchema.descriptor(key),
        12D,
        System.currentTimeMillis() - 60_000L
    );
    bridge.updatePluginSamples("iris", Set.of(key), Map.of(key, stale), "missing");
    Assertions.assertEquals("invalid-sample", bridge.getSample("iris", key).message());
  }

  @Test
  void schemaMismatchIsLoggedOncePerRejectionTransitionWithExpectedAndReceivedShape() {
    List<String> logged = new ArrayList<>();
    RemoteSamplerBridge bridge = new RemoteSamplerBridge(logged::add);
    String key = IntegrationMetricSchema.GLOSS_TICK_MS;
    IntegrationMetricDescriptor schema = IntegrationMetricSchema.descriptor(key);
    IntegrationMetricDescriptor wrongUnit = new IntegrationMetricDescriptor(key, schema.type(), "ms", schema.tags());
    IntegrationMetricDescriptor wrongType = new IntegrationMetricDescriptor(
        key,
        IntegrationMetricType.LONG,
        schema.unit(),
        schema.tags()
    );

    bridge.updatePluginSamples("gloss", Set.of(key), Map.of(key, sampleOf(wrongUnit)), "missing");
    bridge.updatePluginSamples("gloss", Set.of(key), Map.of(key, sampleOf(wrongUnit)), "missing");

    Assertions.assertEquals(1, logged.size());
    String first = logged.getFirst();
    Assertions.assertTrue(first.contains("gloss"), first);
    Assertions.assertTrue(first.contains(key), first);
    Assertions.assertTrue(first.contains("expected DOUBLE 'ms-per-second'"), first);
    Assertions.assertTrue(first.contains("received DOUBLE 'ms'"), first);
    Assertions.assertFalse(bridge.isAvailable("gloss", key));

    bridge.updatePluginSamples("gloss", Set.of(key), Map.of(key, sampleOf(wrongType)), "missing");
    Assertions.assertEquals(2, logged.size());
    Assertions.assertTrue(logged.get(1).contains("received LONG 'ms-per-second'"), logged.get(1));

    bridge.updatePluginSamples("gloss", Set.of(key), Map.of(key, sampleOf(schema)), "missing");
    Assertions.assertTrue(bridge.isAvailable("gloss", key));
    bridge.updatePluginSamples("gloss", Set.of(key), Map.of(key, sampleOf(wrongType)), "missing");
    Assertions.assertEquals(3, logged.size());
  }

  private static IntegrationMetricSample sampleOf(IntegrationMetricDescriptor descriptor) {
    return IntegrationMetricSample.available(descriptor, 4D, System.currentTimeMillis());
  }

  private static IntegrationMetricGroup worldGroup(String identity, double loadedChunks, long now) {
    String key = IntegrationMetricSchema.IRIS_LOADED_CHUNKS;
    IntegrationMetricSample sample = IntegrationMetricSample.available(
        IntegrationMetricSchema.descriptor(key),
        loadedChunks,
        now
    );
    return new IntegrationMetricGroup(
        "world",
        identity,
        identity,
        Map.of("plugin", "iris"),
        Map.of(key, sample)
    );
  }
}
