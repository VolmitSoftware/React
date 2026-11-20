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

package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.platform.WorldStatsHost;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.RollingSequence;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SamplerPerWorldTickTime extends ReactCachedSampler {
  public static final String ID = "per-world-tick-time";
  private static final int ROLLING_WINDOW = 20;

  private static final Map<UUID, WorldSeries> SERIES = new ConcurrentHashMap<>();

  public SamplerPerWorldTickTime() {
    super(ID, 1000);
  }

  @Override
  public void stop() {
    super.stop();
    SERIES.clear();
  }

  @Override
  public String getIcon() {
    return "minecraft:recovery_compass";
  }

  @Override
  public double onSample() {
    return sampleOnMainThread(this::computeWorst);
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 2);
  }

  @Override
  public String formattedSuffix(double t) {
    return "ms PWT";
  }

  public static double meanMsFor(UUID worldId) {
    if (worldId == null) {
      return 0D;
    }
    WorldSeries series = SERIES.get(worldId);
    return series == null ? 0D : series.lastMean;
  }

  public static double maxMsFor(UUID worldId) {
    if (worldId == null) {
      return 0D;
    }
    WorldSeries series = SERIES.get(worldId);
    return series == null ? 0D : series.lastMax;
  }

  public static Map<UUID, Double> snapshotMeansMs() {
    Map<UUID, Double> snapshot = new ConcurrentHashMap<>(SERIES.size());
    for (Map.Entry<UUID, WorldSeries> entry : SERIES.entrySet()) {
      snapshot.put(entry.getKey(), entry.getValue().lastMean);
    }
    return snapshot;
  }

  public static String measurementMode() {
    return J.isFoliaThreading() ? "folia-region-proxy" : "paper-share-proxy";
  }

  private double computeWorst() {
    double tickMs = sampleScalar(SamplerTickTime.ID);
    if (tickMs <= 0D) {
      decayAllSeries();
      return 0D;
    }

    List<WorldStatsHost.WorldLoad> worlds = SamplerHosts.worlds().map(WorldStatsHost::worldLoads).orElse(List.of());
    if (worlds.isEmpty()) {
      decayAllSeries();
      return 0D;
    }

    long[] weights = new long[worlds.size()];
    long totalWeight = 0L;
    for (int i = 0; i < worlds.size(); i++) {
      long weight = weightFor(worlds.get(i));
      weights[i] = weight;
      totalWeight += weight;
    }

    if (totalWeight <= 0L) {
      double evenShare = tickMs / worlds.size();
      double worstEven = 0D;
      for (WorldStatsHost.WorldLoad world : worlds) {
        worstEven = Math.max(worstEven, pushSample(world, evenShare));
      }
      pruneSeries(worlds);
      return worstEven;
    }

    double worst = 0D;
    for (int i = 0; i < worlds.size(); i++) {
      WorldStatsHost.WorldLoad world = worlds.get(i);
      double share = (weights[i] / (double) totalWeight) * tickMs;
      double mean = pushSample(world, share);
      if (mean > worst) {
        worst = mean;
      }
    }

    pruneSeries(worlds);
    return worst;
  }

  private long weightFor(WorldStatsHost.WorldLoad world) {
    if (world == null) {
      return 0L;
    }
    return Math.max(1L, world.entityCount() + world.chunkCount());
  }

  private double pushSample(WorldStatsHost.WorldLoad world, double share) {
    UUID worldId = world.worldId();
    WorldSeries series = SERIES.computeIfAbsent(worldId, ignored -> new WorldSeries(ROLLING_WINDOW));
    series.push(share);
    return series.lastMean;
  }

  private void decayAllSeries() {
    for (WorldSeries series : SERIES.values()) {
      series.push(0D);
    }
  }

  private void pruneSeries(List<WorldStatsHost.WorldLoad> worlds) {
    if (SERIES.size() <= worlds.size()) {
      return;
    }
    Set<UUID> live = new HashSet<>(worlds.size());
    for (WorldStatsHost.WorldLoad world : worlds) {
      live.add(world.worldId());
    }
    SERIES.keySet().retainAll(live);
  }

  private double sampleScalar(String id) {
    try {
      Sampler sampler = SamplerHosts.sampler(id);
      return sampler == null ? 0D : sampler.sample();
    } catch (Throwable ignored) {
      return 0D;
    }
  }

  private static final class WorldSeries {
    private final RollingSequence rolling;
    private volatile double lastMean;
    private volatile double lastMax;

    private WorldSeries(int window) {
      this.rolling = new RollingSequence(window);
      this.lastMean = 0D;
      this.lastMax = 0D;
    }

    private void push(double share) {
      rolling.put(share);
      lastMean = share;
      lastMax = rolling.getMax();
    }
  }
}
