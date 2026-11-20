package art.arcane.react.content.feature;

import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.IntegrationMetricsHost;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Each Iris world's share of loaded chunks.
 */
public class IrisWorldSharePieRenderer extends SharePieRenderer {
  public static final String ID = "iris-world-chunk-share-pie-map";
  private final IntegrationMetricsHost integrations;
  private final Predicate<UUID> viewerOnline;

  public IrisWorldSharePieRenderer(IntegrationMetricsHost integrations, Predicate<UUID> viewerOnline) {
    super(ID);
    this.integrations = integrations;
    this.viewerOnline = viewerOnline;
  }

  @Override
  protected TextKey title() {
    return RendererMessages.TITLE_WORLD_CHUNK_SHARE;
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(72, 132, 194);
  }

  @Override
  protected boolean viewerAvailable(MapRenderTarget target) {
    return target != null && target.viewerId() != null && viewerOnline.test(target.viewerId());
  }

  @Override
  protected Map<String, Long> collectBuckets(MapRenderTarget target) {
    Map<String, Long> counts = newCounterMap();
    if (!integrations.available()) {
      return counts;
    }

    for (IntegrationMetricGroup group : integrations.groups("iris", "world")) {
      IntegrationMetricSample sample = group.samples().get(IntegrationMetricSchema.IRIS_LOADED_CHUNKS);
      if (sample == null || !sample.available()) {
        continue;
      }
      long loadedChunks = Math.max(0L, Math.round(sample.valueOr(0D)));
      counts.merge(displayName(group.label()), loadedChunks, Long::sum);
    }
    return counts;
  }
}
