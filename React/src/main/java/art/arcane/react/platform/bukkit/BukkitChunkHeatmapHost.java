package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.controller.NearbyPlayerIndexController.PlayerViewSnapshot;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.platform.ChunkHeatmapHost;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Heatmap data from the observer's chunk attribution and the nearby player index.
 */
public final class BukkitChunkHeatmapHost implements ChunkHeatmapHost {
  public static final BukkitChunkHeatmapHost INSTANCE = new BukkitChunkHeatmapHost();

  private BukkitChunkHeatmapHost() {
  }

  @Override
  public Optional<HeatmapWorldRef> heatmapWorld(UUID worldId) {
    ObserverController observer = React.controller(ObserverController.class);
    return observer == null ? Optional.empty() : observer.heatmapWorld(worldId);
  }

  @Override
  public List<LoadedChunkCoordinate> loadedChunks(UUID worldId, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
    ObserverController observer = React.controller(ObserverController.class);
    return observer == null
        ? List.of()
        : observer.loadedChunkCoordinatesInBounds(worldId, minChunkX, maxChunkX, minChunkZ, maxChunkZ);
  }

  @Override
  public double chunkSample(String worldKey, int chunkX, int chunkZ, String samplerId) {
    Sampler sampler = React.sampler(samplerId);
    ObserverController observer = React.controller(ObserverController.class);
    if (sampler == null || observer == null) {
      return 0D;
    }
    return observer.sample(worldKey, chunkX, chunkZ, sampler).orElse(0D);
  }

  @Override
  public double chunkTotalScore(String worldKey, int chunkX, int chunkZ) {
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null || observer.getSampled() == null) {
      return 0D;
    }
    return observer.sampledChunk(worldKey, chunkX, chunkZ).map(SampledChunk::totalScore).orElse(0D);
  }

  @Override
  public List<PlayerPosition> playersInColumn(UUID worldId, double x, double z, double radiusBlocks) {
    NearbyPlayerIndexController playerIndex = React.controller(NearbyPlayerIndexController.class);
    if (playerIndex == null) {
      return List.of();
    }
    List<PlayerViewSnapshot> snapshots = playerIndex.playerSnapshotsInColumn(worldId, x, z, radiusBlocks);
    List<PlayerPosition> players = new ArrayList<>(snapshots.size());
    for (PlayerViewSnapshot snapshot : snapshots) {
      players.add(new PlayerPosition(snapshot.playerId(), snapshot.name(), snapshot.x(), snapshot.z(),
          snapshot.speed(), snapshot.gliding(), snapshot.mounted()));
    }
    return players;
  }
}
