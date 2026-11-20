package art.arcane.react.content.feature;

import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.platform.cost.OwnerNoun;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Each plugin's, or mod's, share of event cost.
 */
public class OwnerImpactPieRenderer extends SharePieRenderer {
  public static final String ID = "plugin-event-impact-pie-map";
  private static final TinyColor HEADER = new TinyColor(162, 94, 48);
  private static final double MINIMUM_SLICE_SHARE = 0.02D;
  private final Supplier<EventMeasurement> events;
  private final Predicate<UUID> viewerOnline;

  public OwnerImpactPieRenderer(Supplier<EventMeasurement> events, Predicate<UUID> viewerOnline) {
    super(ID);
    this.events = events;
    this.viewerOnline = viewerOnline;
  }

  @Override
  protected TextKey title() {
    return OwnerNoun.pick(RendererMessages.TITLE_PLUGIN_IMPACT_SHARE, RendererMessages.TITLE_MOD_IMPACT_SHARE);
  }

  @Override
  protected TinyColor headerColor() {
    return HEADER;
  }

  @Override
  protected TextKey totalUnit() {
    return RendererMessages.PIE_UNIT_IMPACT;
  }

  @Override
  protected double minimumSliceShare() {
    return MINIMUM_SLICE_SHARE;
  }

  @Override
  protected boolean viewerAvailable(MapRenderTarget target) {
    return target != null && target.viewerId() != null && viewerOnline.test(target.viewerId());
  }

  @Override
  protected Map<String, Long> collectBuckets(MapRenderTarget target) {
    Map<String, Long> counts = newCounterMap();
    for (PluginEventImpactSeries.Entry entry : PluginEventImpactSeries.snapshot(events.get()).entries()) {
      counts.put(entry.plugin(), Math.max(1L, Math.round(entry.impact() * 1000D)));
    }
    return counts;
  }
}
