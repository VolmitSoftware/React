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

import art.arcane.react.React;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.data.TinyColor;
import org.bukkit.Chunk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Tick Spike Origin Replay Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureTickSpikeOriginReplayMap extends FeatureChunkHeatmapBase {
  public static final String ID = "tick-spike-origin-replay-map";
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
  private transient Map<ChunkKey, HeatCell> replayHeat = new ConcurrentHashMap<>();
  private transient volatile long lastCaptureMS;
  private transient volatile long lastDecayMS;

  public FeatureTickSpikeOriginReplayMap() {
    super(ID);
  }

  @Override
  public void onActivate() {
    replayHeat = new ConcurrentHashMap<>();
    long now = System.currentTimeMillis();
    lastCaptureMS = now;
    lastDecayMS = now;
  }

  @Override
  public void onDeactivate() {
    replayHeat.clear();
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    double tickMS = sample(SamplerTickTime.ID);

    if (tickMS >= spikeThresholdMS && now - lastCaptureMS >= spikeCaptureCooldownMS) {
      lastCaptureMS = now;
      if (J.isFoliaThreading()) {
        captureSpikeFolia(now, tickMS);
      } else {
        J.s(() -> captureSpike(now, tickMS));
      }
    }

    if (now - lastDecayMS >= decayEveryMS) {
      lastDecayMS = now;
      decay(now);
    }
  }

  @Override
  protected String mapLabel() {
    return ReactLanguage.raw(RendererMessages.TITLE_TICK_SPIKE_REPLAY);
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(168, 86, 34);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(18, 9, 5);
  }

  @Override
  protected double chunkScore(Chunk chunk) {
    ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    HeatCell cell = replayHeat.get(key);
    return cell == null ? 0D : Math.max(0D, cell.heat);
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    if (normalized < 0.5D) {
      return gradient(normalized * 2D, new TinyColor(70, 40, 0), new TinyColor(255, 120, 10));
    }

    return gradient((normalized - 0.5D) * 2D, new TinyColor(255, 120, 10), new TinyColor(255, 240, 180));
  }

  private void captureSpike(long now, double tickMS) {
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null) {
      return;
    }

    SampledChunk worst = observer.absoluteWorst();
    if (worst == null || worst.getChunk() == null || worst.getChunk().getWorld() == null) {
      return;
    }

    Chunk origin = worst.getChunk();
    int radius = Math.max(0, captureRadiusChunks);
    double spikePressure = Math.max(1D, tickMS - spikeThresholdMS + 1D);

    for (Chunk chunk : origin.getWorld().getLoadedChunks()) {
      int dx = chunk.getX() - origin.getX();
      int dz = chunk.getZ() - origin.getZ();
      if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
        continue;
      }

      double distance = Math.sqrt((dx * dx) + (dz * dz));
      double localScore = Math.max(0D, chunkTotalScore(chunk));
      double distanceWeight = 1D / (1D + distance);
      double scoreWeight = 1D + (Math.min(localScore, 250D) / 120D);
      addHeat(chunk, spikePressure * distanceWeight * scoreWeight, now);
    }

    addHeat(origin, spikePressure * 1.8D, now);
  }

  private void captureSpikeFolia(long now, double tickMS) {
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null) {
      return;
    }

    SampledChunk worst = observer.absoluteWorst();
    if (worst == null || worst.getChunk() == null || worst.getChunk().getWorld() == null) {
      return;
    }

    Chunk origin = worst.getChunk();
    int y = Math.max(origin.getWorld().getMinHeight() + 1, 64);
    J.s(origin.getBlock(8, y, 8).getLocation(), () -> captureSpikeAroundOrigin(origin, now, tickMS), 0);
  }

  private void captureSpikeAroundOrigin(Chunk origin, long now, double tickMS) {
    if (origin == null || origin.getWorld() == null) {
      return;
    }

    int radius = Math.max(0, captureRadiusChunks);
    double spikePressure = Math.max(1D, tickMS - spikeThresholdMS + 1D);
    UUID world = origin.getWorld().getUID();
    int originX = origin.getX();
    int originZ = origin.getZ();

    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        double distance = Math.sqrt((dx * dx) + (dz * dz));
        double distanceWeight = 1D / (1D + distance);
        addHeat(world, originX + dx, originZ + dz, spikePressure * distanceWeight, now);
      }
    }

    addHeat(world, originX, originZ, spikePressure * 1.8D, now);
  }

  private void addHeat(Chunk chunk, double heat, long now) {
    if (chunk == null || chunk.getWorld() == null || heat <= 0D) {
      return;
    }

    addHeat(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ(), heat, now);
  }

  private void addHeat(UUID worldId, int chunkX, int chunkZ, double heat, long now) {
    if (worldId == null || heat <= 0D) {
      return;
    }

    ChunkKey key = new ChunkKey(worldId, chunkX, chunkZ);
    HeatCell cell = replayHeat.computeIfAbsent(key, k -> replayHeat.size() >= maxTrackedChunks ? null : new HeatCell());
    if (cell == null) {
      return;
    }

    cell.heat = Math.min(10000D, cell.heat + heat);
    cell.lastSeenMS = now;
  }

  private void decay(long now) {
    for (Map.Entry<ChunkKey, HeatCell> entry : replayHeat.entrySet()) {
      HeatCell cell = entry.getValue();
      cell.heat *= decayFactor;

      if (cell.heat < minimumHeat || now - cell.lastSeenMS > staleChunkMS) {
        replayHeat.remove(entry.getKey(), cell);
      }
    }
  }

  private static final class HeatCell {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Accumulated heat score tracked by tick spike origin replay map for hotspot replay.", impact = "Higher values mark stronger spike contribution and decay over time as configured.")
    private double heat = 0D;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by tick spike origin replay map to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long lastSeenMS = 0L;
  }

  private static final class ChunkKey {
    @art.arcane.react.util.project.config.ConfigDoc(value = "World identifier used by tick spike origin replay map internal tracking.", impact = "This is runtime identity data and should normally be left to automatic updates.")
    private final UUID world;
    @art.arcane.react.util.project.config.ConfigDoc(value = "X-axis coordinate used by tick spike origin replay map internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Z-axis coordinate used by tick spike origin replay map internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int z;

    private ChunkKey(UUID world, int x, int z) {
      this.world = world;
      this.x = x;
      this.z = z;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }

      if (!(object instanceof ChunkKey key)) {
        return false;
      }

      return x == key.x && z == key.z && world.equals(key.world);
    }

    @Override
    public int hashCode() {
      int result = world.hashCode();
      result = 31 * result + x;
      result = 31 * result + z;
      return result;
    }
  }
}
