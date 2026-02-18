package art.arcane.react.content.feature;

import art.arcane.react.util.data.TinyColor;
import org.bukkit.entity.Player;

import java.util.Map;

@art.arcane.react.util.config.ConfigDescription("Configuration for Plugin Event Impact Pie Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeaturePluginEventImpactPieMap extends FeatureIrisChunkSharePieBase {
  public static final String ID = "plugin-event-impact-pie-map";

  public FeaturePluginEventImpactPieMap() {
    super(ID);
  }

  @Override
  protected String title() {
    return "Plugin Impact Share";
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(162, 94, 48);
  }

  @Override
  protected Map<String, Long> collectBuckets(Player viewer) {
    Map<String, Long> counts = newCounterMap();
    PluginEventImpactSeries.Snapshot snapshot = PluginEventImpactSeries.snapshot();
    if (snapshot.entries().isEmpty()) {
      return counts;
    }

    for (PluginEventImpactSeries.Entry entry : snapshot.entries()) {
      long scaled = Math.max(1L, Math.round(entry.impact() * 1000D));
      counts.put(entry.plugin(), scaled);
    }

    return counts;
  }

  @Override
  protected String totalUnitLabel() {
    return "impact";
  }
}
