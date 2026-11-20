package art.arcane.react.content.feature;

import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Loaded chunks of the map's world by biome, Iris biome names where Iris generates the world.
 */
public class IrisBiomeSharePieRenderer extends SharePieRenderer {
  public static final String ID = "iris-biome-chunk-share-pie-map";
  private final Function<MapRenderTarget, Map<String, Long>> biomes;
  private final Predicate<UUID> viewerOnline;

  /**
   * @param biomes chunk count by biome label for the world the map shows
   */
  public IrisBiomeSharePieRenderer(Function<MapRenderTarget, Map<String, Long>> biomes, Predicate<UUID> viewerOnline) {
    super(ID);
    this.biomes = biomes;
    this.viewerOnline = viewerOnline;
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
  protected boolean viewerAvailable(MapRenderTarget target) {
    return target != null && target.viewerId() != null && viewerOnline.test(target.viewerId());
  }

  @Override
  protected Map<String, Long> collectBuckets(MapRenderTarget target) {
    Map<String, Long> counts = newCounterMap();
    counts.putAll(biomes.apply(target));
    return counts;
  }
}
