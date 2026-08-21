package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.entity.Player;

import java.util.Map;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Iris World Chunk Share Pie Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIrisWorldChunkSharePieMap extends FeatureIrisChunkSharePieBase {
  public static final String ID = "iris-world-chunk-share-pie-map";

  public FeatureIrisWorldChunkSharePieMap() {
    super(ID);
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
  protected Map<String, Long> collectBuckets(Player viewer) {
    Map<String, Long> counts = newCounterMap();
    IntegrationController controller = React.controller(IntegrationController.class);
    RemoteSamplerBridge bridge = controller == null ? null : controller.getRemoteSamplerBridge();
    if (bridge == null) {
      return counts;
    }

    for (IntegrationMetricGroup group : bridge.groups("iris", "world")) {
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
