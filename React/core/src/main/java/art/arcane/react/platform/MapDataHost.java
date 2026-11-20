package art.arcane.react.platform;

import art.arcane.react.content.feature.AnimusMetrics;

import java.util.Map;
import java.util.UUID;

/**
 * Data the feature maps read beyond chunk samples: viewers, Animus metrics, per-owner event cost and Iris biome shares.
 */
public interface MapDataHost {
  boolean isViewerOnline(UUID viewerId);

  AnimusMetrics.Snapshot animusSnapshot();

  EventMeasurement eventMeasurement();

  /**
   * Starts sampling chunk biomes for the Iris biome share map; sampling stops again with {@link #stopIrisBiomes()}.
   */
  void startIrisBiomes();

  void stopIrisBiomes();

  /**
   * Samples the next batch of chunks; called once a second while the Iris biome share map is active.
   */
  void tickIrisBiomes();

  /**
   * Chunk count by biome label for the world the map shows.
   */
  Map<String, Long> irisBiomeBuckets(MapRenderTarget target);
}
