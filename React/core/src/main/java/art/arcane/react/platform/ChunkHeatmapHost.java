package art.arcane.react.platform;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.core.controller.LoadedChunkCoordinate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-chunk data for the chunk heatmaps. Called from map render threads.
 */
public interface ChunkHeatmapHost {
  Optional<HeatmapWorldRef> heatmapWorld(UUID worldId);

  List<LoadedChunkCoordinate> loadedChunks(UUID worldId, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ);

  /**
   * The sampler's value attributed to one chunk, 0 when nothing is attributed or the sampler is not running.
   */
  double chunkSample(String worldKey, int chunkX, int chunkZ, String samplerId);

  /**
   * Sum of every sampler value attributed to one chunk.
   */
  double chunkTotalScore(String worldKey, int chunkX, int chunkZ);

  List<PlayerPosition> playersInColumn(UUID worldId, double x, double z, double radiusBlocks);

  record PlayerPosition(UUID playerId, String name, double x, double z, double speed, boolean gliding, boolean mounted) {
  }
}
