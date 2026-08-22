package art.arcane.react.content.feature;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class PluginEventImpactSeriesTest {
  @Test
  void callVolumeWithoutMeasuredTimeHasNoImpact() {
    Map<String, PluginEventImpactSeries.Entry> entries = PluginEventImpactSeries.mergeCurrent(
        Map.of("Adapt", 0D),
        Map.of("Adapt", 10_000)
    );

    PluginEventImpactSeries.Entry adapt = entries.get("Adapt");
    Assertions.assertEquals(0D, adapt.impact());
    Assertions.assertEquals(0D, adapt.currentMS());
    Assertions.assertEquals(10_000, adapt.currentCalls());
  }

  @Test
  void measuredTimeRemainsTheOnlyImpactWeight() {
    Map<String, PluginEventImpactSeries.Entry> entries = PluginEventImpactSeries.mergeCurrent(
        Map.of("Adapt", 4D),
        Map.of("Adapt", 10_000)
    );

    PluginEventImpactSeries.Entry adapt = entries.get("Adapt");
    Assertions.assertEquals(4D, adapt.impact());
    Assertions.assertEquals(4D, adapt.currentMS());
    Assertions.assertEquals(10_000, adapt.currentCalls());
  }
}
