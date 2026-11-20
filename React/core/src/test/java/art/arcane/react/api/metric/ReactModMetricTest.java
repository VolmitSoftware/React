package art.arcane.react.api.metric;

import art.arcane.react.api.ReactModApi;
import art.arcane.react.core.integration.ModApiRegistrations;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactModMetricTest {
  @Test
  void iconsNormalizeToItemIds() {
    assertEquals("minecraft:clock", ReactModMetric.gauge("pets.live", "Live", "").withIcon("CLOCK").icon());
    assertEquals("petmod:collar", ReactModMetric.gauge("pets.live", "Live", "").withIcon("petmod:collar").icon());
    assertEquals(ReactModMetric.DEFAULT_ICON, ReactModMetric.gauge("pets.live", "Live", "").withIcon("not an item").icon());
  }

  @Test
  void factoriesMatchTheBukkitDefaults() {
    assertEquals(1, ReactModMetric.rate("a.b", "Rate", "/s").decimals());
    assertEquals("%", ReactModMetric.percent("a.b", "Pct").unit());
    assertEquals(2, ReactModMetric.millis("a.b", "Ms").decimals());
    assertEquals(4, ReactModMetric.gauge("a.b", "G", "").withDecimals(9).decimals());
  }

  @Test
  void registrationIsKeyedByModAndReplacesDuplicates() {
    ReactModMetricSource source = new ReactModMetricSource() {
      @Override
      public String sourceId() {
        return "pets";
      }

      @Override
      public List<ReactModMetric> metrics() {
        return List.of(ReactModMetric.gauge("pets.live", "Live", ""));
      }
    };

    assertFalse(ReactModApi.registerMetricSource(" ", source));
    assertTrue(ReactModApi.registerMetricSource("petmod", source));
    assertTrue(ReactModApi.registerMetricSource("petmod", source));
    assertEquals(1, ModApiRegistrations.metricSources().size());
    assertEquals("petmod", ModApiRegistrations.metricSources().get(0).owner());
    assertTrue(ReactModApi.unregisterMetricSource(source));
    assertTrue(ModApiRegistrations.metricSources().isEmpty());
  }
}
