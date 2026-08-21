package art.arcane.react.content.feature;

import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Iris Biome Chunk Share Pie Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIrisBiomeChunkSharePieMap extends FeatureIrisChunkSharePieBase {
  public static final String ID = "iris-biome-chunk-share-pie-map";

  public FeatureIrisBiomeChunkSharePieMap() {
    super(ID);
  }

  @Override
  protected TextKey title() {
    return RendererMessages.TITLE_BIOME_CHUNK_SHARE;
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(66, 138, 94);
  }

  @Override
  protected Map<String, Long> collectBuckets(Player viewer) {
    Map<String, Long> counts = newCounterMap();
    if (viewer == null) {
      return counts;
    }

    World world = targetWorld(viewer);
    if (world == null) {
      return counts;
    }

    int y = sampleY(world, viewer);
    for (Chunk chunk : world.getLoadedChunks()) {
      if (chunk == null) {
        continue;
      }

      String biome = labelForChunkBiome(chunk, y);
      counts.merge(biome, 1L, Long::sum);
    }

    return counts;
  }
}
