package art.arcane.react.content.feature;

import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for the Animus Cost Share Pie Map feature.")
public class FeatureAnimusCostSharePieMap extends FeatureIrisChunkSharePieBase implements CapabilityGatedFeature {
  public static final String ID = "animus-cost-share-pie-map";
  private static final double MICROS_PER_MILLI = 1000D;

  public FeatureAnimusCostSharePieMap() {
    super(ID);
  }

  @Override
  protected TextKey title() {
    return RendererMessages.TITLE_ANIMUS_COST_SHARE;
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(58, 142, 158);
  }

  @Override
  protected Map<String, Long> collectBuckets(Player viewer) {
    return costBuckets(AnimusMetricSeries.snapshot());
  }

  static Map<String, Long> costBuckets(AnimusMetricSeries.Snapshot snapshot) {
    Map<String, Long> buckets = newCounterMap();
    for (AnimusMetricSeries.Entry cost : snapshot.costs()) {
      long micros = Math.round(cost.value() * MICROS_PER_MILLI);
      if (micros > 0L) {
        buckets.merge(cost.displayName(), micros, Long::sum);
      }
    }
    return buckets;
  }

  @Override
  protected TextKey totalUnit() {
    return RendererMessages.PIE_UNIT_MS_PER_SECOND;
  }

  @Override
  protected String compact(long value) {
    return formatMillis(value);
  }

  static String formatMillis(long micros) {
    double millis = Math.max(0L, micros) / MICROS_PER_MILLI;
    if (millis >= 100D) {
      return Form.f(millis, 0);
    }
    return Form.f(millis, millis >= 10D ? 1 : 2);
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of(AnimusMetricSeries.SOURCE_ID);
  }
}
