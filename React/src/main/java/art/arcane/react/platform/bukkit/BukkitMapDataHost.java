package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.content.feature.AnimusMetricSeries;
import art.arcane.react.content.feature.AnimusMetrics;
import art.arcane.react.content.feature.IrisBiomeChunkSampler;
import art.arcane.react.core.controller.EventController;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.MapDataHost;
import art.arcane.react.platform.MapRenderTarget;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class BukkitMapDataHost implements MapDataHost {
  private final IrisBiomeChunkSampler irisBiomes = new IrisBiomeChunkSampler();

  @Override
  public boolean isViewerOnline(UUID viewerId) {
    Player viewer = Bukkit.getPlayer(viewerId);
    return viewer != null && viewer.isOnline();
  }

  @Override
  public AnimusMetrics.Snapshot animusSnapshot() {
    return AnimusMetricSeries.snapshot();
  }

  @Override
  public EventMeasurement eventMeasurement() {
    return React.controller(EventController.class);
  }

  @Override
  public void startIrisBiomes() {
    irisBiomes.start();
  }

  @Override
  public void stopIrisBiomes() {
    irisBiomes.stop();
  }

  @Override
  public void tickIrisBiomes() {
    irisBiomes.tick();
  }

  @Override
  public Map<String, Long> irisBiomeBuckets(MapRenderTarget target) {
    return irisBiomes.buckets(target);
  }
}
