package art.arcane.react.content.feature;

import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Each Animus subsystem's share of its tick cost.
 */
public class AnimusCostSharePieRenderer extends SharePieRenderer {
  public static final String ID = "animus-cost-share-pie-map";
  private static final double MICROS_PER_MILLI = 1000D;
  private static final TinyColor HEADER = new TinyColor(58, 142, 158);
  private final Supplier<AnimusMetrics.Snapshot> metrics;
  private final Predicate<UUID> viewerOnline;

  public AnimusCostSharePieRenderer(Supplier<AnimusMetrics.Snapshot> metrics, Predicate<UUID> viewerOnline) {
    super(ID);
    this.metrics = metrics;
    this.viewerOnline = viewerOnline;
  }

  @Override
  protected TextKey title() {
    return RendererMessages.TITLE_ANIMUS_COST_SHARE;
  }

  @Override
  protected TinyColor headerColor() {
    return HEADER;
  }

  @Override
  protected TextKey totalUnit() {
    return RendererMessages.PIE_UNIT_MS_PER_SECOND;
  }

  @Override
  protected boolean viewerAvailable(MapRenderTarget target) {
    return target != null && target.viewerId() != null && viewerOnline.test(target.viewerId());
  }

  @Override
  protected Map<String, Long> collectBuckets(MapRenderTarget target) {
    return costBuckets(metrics.get());
  }

  @Override
  protected String compact(long value) {
    return formatMillis(value);
  }

  @Override
  protected String compactShort(long value) {
    double millis = Math.max(0L, value) / MICROS_PER_MILLI;
    return Form.f(millis, millis >= 10D ? 0 : 1);
  }

  public static Map<String, Long> costBuckets(AnimusMetrics.Snapshot snapshot) {
    Map<String, Long> buckets = newCounterMap();
    for (AnimusMetrics.Entry cost : snapshot.costs()) {
      long micros = Math.round(cost.value() * MICROS_PER_MILLI);
      if (micros > 0L) {
        buckets.merge(cost.displayName(), micros, Long::sum);
      }
    }
    return buckets;
  }

  public static String formatMillis(long micros) {
    double millis = Math.max(0L, micros) / MICROS_PER_MILLI;
    if (millis >= 100D) {
      return Form.f(millis, 0);
    }
    return Form.f(millis, millis >= 10D ? 1 : 2);
  }
}
