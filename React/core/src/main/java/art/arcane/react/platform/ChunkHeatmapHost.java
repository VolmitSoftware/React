package art.arcane.react.platform;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.model.SampledChunk;

import java.util.ArrayList;
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

  /**
   * The sampled chunk with the highest total score right now; empty when nothing is sampled.
   */
  default Optional<SampledChunk> worstChunk() {
    return Optional.empty();
  }

  /**
   * One chunk's sampled values; empty when nothing is attributed to it.
   */
  default Optional<SampledChunk> sampledChunk(String worldKey, int chunkX, int chunkZ) {
    return Optional.empty();
  }

  /**
   * Loaded chunks within a circle of {@code radius} chunks around the centre chunk, read live.
   */
  default List<LoadedChunkCoordinate> loadedChunksInRadius(UUID worldId, int centerX, int centerZ, int radius) {
    int safeRadius = Math.max(0, radius);
    long radiusSquared = (long) safeRadius * safeRadius;
    List<LoadedChunkCoordinate> result = new ArrayList<>();
    for (LoadedChunkCoordinate chunk : loadedChunks(worldId, centerX - safeRadius, centerX + safeRadius,
        centerZ - safeRadius, centerZ + safeRadius)) {
      long dx = (long) chunk.chunkX() - centerX;
      long dz = (long) chunk.chunkZ() - centerZ;
      if ((dx * dx) + (dz * dz) <= radiusSquared) {
        result.add(chunk);
      }
    }
    return result;
  }
}
