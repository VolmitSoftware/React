package art.arcane.react.core.pluginapi;

import art.arcane.react.api.metric.ReactMetricKind;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.MetricDefinition;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.SourceDefinition;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.SourceType;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.TransformDefinition;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.TransformMode;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginApiMetricRuntimeTest {
  @Test
  void appliesDeltaScaleOffsetAndBounds() {
    PluginApiMetricRuntime runtime = runtime(new TransformDefinition(TransformMode.DELTA_PER_SECOND, 2D, 1D, 0D, 11D));
    runtime.accept(10D, 1_000L, 1L);
    assertFalse(runtime.available(1_000L));
    runtime.accept(15D, 2_000L, 1L);
    assertTrue(runtime.available(2_000L));
    assertEquals(11D, runtime.lastValue());
  }

  @Test
  void marksValuesStaleAtConfiguredBoundary() {
    PluginApiMetricRuntime runtime = runtime(new TransformDefinition(TransformMode.VALUE, 1D, 0D, null, null));
    runtime.accept(5D, 1_000L, 0L);
    assertTrue(runtime.available(16_000L));
    assertFalse(runtime.available(16_001L));
    assertEquals("stale", runtime.availabilityReason(16_001L));
  }

  @Test
  void quarantinesAfterFiveCollectorFailures() {
    PluginApiMetricRuntime runtime = runtime(new TransformDefinition(TransformMode.VALUE, 1D, 0D, null, null));
    for (int index = 0; index < PluginApiMetricRuntime.FAILURE_LIMIT; index++) {
      runtime.unavailable("failure", 0L, true);
    }
    assertTrue(runtime.quarantined());
    assertFalse(runtime.due(100_000L));
    assertEquals(PluginApiMetricRuntime.FAILURE_LIMIT, runtime.failedSamples());
  }

  @Test
  void slowWarningsAreRateLimitedPerMetric() {
    PluginApiMetricRuntime runtime = runtime(new TransformDefinition(TransformMode.VALUE, 1D, 0D, null, null));

    assertTrue(runtime.shouldWarnSlow(60_000L));
    assertFalse(runtime.shouldWarnSlow(119_999L));
    assertTrue(runtime.shouldWarnSlow(120_000L));
  }

  private PluginApiMetricRuntime runtime(TransformDefinition transform) {
    SourceDefinition source = new SourceDefinition(SourceType.INTEGRATION, "example", "value", "", "", true);
    MetricDefinition definition = new MetricDefinition(
        "value",
        "plugin-api-test-value",
        "Value",
        ReactMetricKind.GAUGE,
        "",
        Material.CLOCK,
        1,
        1_000L,
        15_000L,
        source,
        transform
    );
    return new PluginApiMetricRuntime(definition);
  }
}
