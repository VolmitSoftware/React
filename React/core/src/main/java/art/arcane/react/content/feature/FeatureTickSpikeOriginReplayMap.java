/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.feature;

import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.TickStatsHost;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.platform.timing.SaveTimings;

import java.util.UUID;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Tick Spike Origin Replay Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureTickSpikeOriginReplayMap extends FeatureChunkHeatmapBase implements TickSpikeReplay.Settings {
  public static final String ID = TickSpikeReplayHeatmap.ID;
  private static final long SAVE_ATTRIBUTION_MS = 5_000L;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for tick spike origin replay map in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 250;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for spike in tick spike origin replay map.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double spikeThresholdMS = 50;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cooldown for spike capture cooldown in tick spike origin replay map (milliseconds).", impact = "Higher values reduce repeat frequency; lower values allow reactions more often.")
  private int spikeCaptureCooldownMS = 350;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Capture chunks radius used by tick spike origin replay map (chunks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private int captureRadiusChunks = 3;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum tracked chunks allowed by tick spike origin replay map.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private int maxTrackedChunks = 4096;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Stale chunk duration used by tick spike origin replay map (milliseconds).", impact = "Higher values keep state active longer; lower values expire or apply changes sooner.")
  private int staleChunkMS = 120000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cadence for decay every updates in tick spike origin replay map (milliseconds).", impact = "Lower values update more frequently; higher values update less often with lower overhead.")
  private int decayEveryMS = 500;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scaling factor applied to decay factor in tick spike origin replay map.", impact = "Higher values amplify this influence; lower values reduce its contribution.")
  private double decayFactor = 0.90;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum heat required by tick spike origin replay map.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private double minimumHeat = 0.15;
  private final transient TickSpikeReplay replay = new TickSpikeReplay(this);

  public FeatureTickSpikeOriginReplayMap() {
    super(ID);
  }

  @Override
  protected ChunkHeatmapRenderer createRenderer(ChunkHeatmapHost data, MapAnchorHost anchors) {
    return new TickSpikeReplayHeatmap(data, anchors, this, replay);
  }

  @Override
  public void onActivate() {
    replay.reset(System.currentTimeMillis());
  }

  @Override
  public void onDeactivate() {
    super.onDeactivate();
    replay.clear();
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    double tickMS = sample(SamplerTickTime.ID);
    SaveTimings.Save recentSave = SamplerHosts.ticks().flatMap(TickStatsHost::saveTimings)
        .flatMap((SaveTimings saves) -> saves.endedWithin(SAVE_ATTRIBUTION_MS)).orElse(null);
    replay.noteSave(recentSave);
    if (replay.claimCapture(now, tickMS) && !replay.causedBySave(recentSave)) {
      if (J.isFoliaThreading()) {
        captureSpikeFolia(now, tickMS);
      } else {
        J.s(() -> captureSpike(now, tickMS));
      }
    }
    replay.decayIfDue(now);
  }

  @Override
  public double spikeThresholdMS() {
    return spikeThresholdMS;
  }

  @Override
  public int spikeCaptureCooldownMS() {
    return spikeCaptureCooldownMS;
  }

  @Override
  public int captureRadiusChunks() {
    return captureRadiusChunks;
  }

  @Override
  public int maxTrackedChunks() {
    return maxTrackedChunks;
  }

  @Override
  public int staleChunkMS() {
    return staleChunkMS;
  }

  @Override
  public int decayEveryMS() {
    return decayEveryMS;
  }

  @Override
  public double decayFactor() {
    return decayFactor;
  }

  @Override
  public double minimumHeat() {
    return minimumHeat;
  }

  private void captureSpike(long now, double tickMS) {
    ChunkHeatmapHost heatmaps = MapFeatureHosts.heatmaps();
    SampledChunk worst = heatmaps.worstChunk().orElse(null);
    if (worst == null || worst.getWorldId() == null || worst.getWorldKey() == null) {
      return;
    }

    UUID worldId = worst.getWorldId();
    String worldKey = worst.getWorldKey();
    int originX = worst.getChunkX();
    int originZ = worst.getChunkZ();
    replay.captureScored(
        worldId,
        originX,
        originZ,
        heatmaps.loadedChunksInRadius(worldId, originX, originZ, Math.max(0, captureRadiusChunks)),
        (int chunkX, int chunkZ) -> heatmaps.sampledChunk(worldKey, chunkX, chunkZ).map(SampledChunk::totalScore).orElse(0D),
        tickMS,
        now
    );
  }

  private void captureSpikeFolia(long now, double tickMS) {
    SampledChunk worst = MapFeatureHosts.heatmaps().worstChunk().orElse(null);
    if (worst == null || worst.getWorldId() == null) {
      return;
    }

    ReactPlatform platform = ReactPlatforms.getOrNull();
    WorldHost worlds = platform == null ? null : platform.worldHost().orElse(null);
    ReactWorld world = worlds == null ? null : worlds.world(worst.getWorldId()).orElse(null);
    if (world == null) {
      return;
    }

    UUID worldId = worst.getWorldId();
    int originX = worst.getChunkX();
    int originZ = worst.getChunkZ();
    J.runChunk(world.handle(), originX, originZ, () -> replay.captureUniform(worldId, originX, originZ, tickMS, now));
  }
}
