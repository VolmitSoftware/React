package art.arcane.react.content.feature;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.content.sampler.SamplerHopperUpdates;
import art.arcane.react.content.sampler.SamplerRedstoneUpdates;
import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.ChunkHeatmapHost.PlayerPosition;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.util.data.TinyColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chunk cost under the players near the map's anchor, with one marker per player coloured by its impact.
 */
public class PlayerImpactHeatmap extends ChunkHeatmapRenderer {
  public static final String ID = "player-impact-overlay";
  public static final PlayerSettings DEFAULT_PLAYER_SETTINGS = new PlayerSettings() {
  };
  private final PlayerSettings settings;

  public PlayerImpactHeatmap(ChunkHeatmapHost data, MapAnchorHost anchors, PlayerSettings settings) {
    super(ID, data, anchors, settings);
    this.settings = settings == null ? DEFAULT_PLAYER_SETTINGS : settings;
  }

  public interface PlayerSettings extends Settings {
    default int maxPlayersDrawn() {
      return 24;
    }

    default boolean showPlayerInitials() {
      return true;
    }
  }

  @Override
  protected String mapLabel() {
    return ReactLanguage.raw(RendererMessages.TITLE_PLAYER_IMPACT);
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(138, 78, 168);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(8, 10, 20);
  }

  @Override
  protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    double base = chunkTotalScore(world, chunkX, chunkZ);
    base += chunkSample(world, chunkX, chunkZ, SamplerEntities.ID) * 0.50D;
    base += chunkSample(world, chunkX, chunkZ, SamplerRedstoneUpdates.ID) * 0.30D;
    base += chunkSample(world, chunkX, chunkZ, SamplerHopperUpdates.ID) * 0.20D;
    return base;
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    return gradient(normalized, new TinyColor(74, 56, 160), new TinyColor(255, 122, 198));
  }

  @Override
  protected void renderOverlay(Map<LoadedChunkCoordinate, Double> score, double min, double max) {
    ProjectionAnchor anchor = projectionAnchor();
    if (anchor == null) {
      return;
    }

    Map<Long, Double> scoreByChunk = new HashMap<>(score.size());
    for (Map.Entry<LoadedChunkCoordinate, Double> entry : score.entrySet()) {
      LoadedChunkCoordinate chunk = entry.getKey();
      scoreByChunk.put(chunkKey(chunk.chunkX(), chunk.chunkZ()), entry.getValue());
    }

    List<PlayerImpact> impacts = new ArrayList<>();
    for (PlayerPosition player : data().playersInColumn(anchor.worldId(), anchor.x(), anchor.z(), visibleRadiusBlocks())) {
      impacts.add(new PlayerImpact(player, playerImpact(player, scoreByChunk)));
    }

    impacts.sort(Comparator.comparingDouble(PlayerImpact::impact).reversed());
    int drawLimit = Math.max(0, settings.maxPlayersDrawn());
    if (impacts.size() > drawLimit) {
      impacts = impacts.subList(0, drawLimit);
    }

    double maxImpact = 0D;
    for (PlayerImpact impact : impacts) {
      maxImpact = Math.max(maxImpact, impact.impact());
    }
    UUID viewerId = projectionFrameAnchored() ? null : projectionViewerId();
    boolean showInitials = settings.showPlayerInitials();
    for (PlayerImpact impact : impacts) {
      PlayerPosition player = impact.player();
      Pixel pixel = projectBlockDelta(anchor, player.x(), player.z());
      int x = pixel.x();
      int y = pixel.y();
      if (x < 0 || y < 0 || x >= width() || y >= height()) {
        continue;
      }

      double normalized = maxImpact <= 0D ? 0D : Math.max(0D, Math.min(1D, impact.impact() / maxImpact));
      TinyColor marker = gradient(normalized, new TinyColor(90, 255, 120), new TinyColor(255, 80, 50));

      set(x, y, marker);
      set(x - 1, y, marker);
      set(x + 1, y, marker);
      set(x, y - 1, marker);
      set(x, y + 1, marker);

      if (viewerId != null && player.playerId().equals(viewerId)) {
        set(x, y, new TinyColor(255, 255, 255));
      } else if (showInitials) {
        String name = player.name();
        if (name != null && !name.isEmpty()) {
          text(Math.max(0, x + 2), Math.max(0, y - 2), name.substring(0, 1).toUpperCase());
        }
      }
    }
  }

  private double playerImpact(PlayerPosition player, Map<Long, Double> scoreByChunk) {
    int chunkX = (int) Math.floor(player.x() / 16D);
    int chunkZ = (int) Math.floor(player.z() / 16D);
    double impact = scoreByChunk.getOrDefault(chunkKey(chunkX, chunkZ), 0D);
    impact += Math.min(3D, Math.max(0D, player.speed())) * 2D;
    if (player.gliding()) {
      impact += 2D;
    }
    if (player.mounted()) {
      impact += 1D;
    }
    return impact;
  }

  private static long chunkKey(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
  }

  private record PlayerImpact(PlayerPosition player, double impact) {
  }
}
