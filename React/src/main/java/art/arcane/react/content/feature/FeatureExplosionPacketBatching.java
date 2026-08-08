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
import art.arcane.react.api.feature.PressureGate;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.nms.ExplosionDecision;
import art.arcane.react.nms.ExplosionHook;
import art.arcane.react.nms.ExplosionPacketSuppressor;
import art.arcane.react.nms.NmsBridge;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.util.common.scheduling.J;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Explosion Packet Batching feature. Collects same-tick explosions per world, clusters them by mergeRadius, and reports how many ClientboundExplodePackets are removed. With the versioned explosion hooks available, eligible clusters suppress the original packets and broadcast one merged packet; otherwise the feature remains measurement-only. Clusters within bypassRadius of a player keep vanilla packets for animation and sound fidelity.")
public class FeatureExplosionPacketBatching extends ReactFeature implements Listener {
  public static final String ID = "explosion-packet-batching";

  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for explosion packet batching in milliseconds.", impact = "Lower values flush the per-world collection buffer more often; higher values reduce overhead.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Bypass radius in blocks around any clustered explosion; if any player is within this distance the cluster keeps vanilla per-explosion packet fanout.", impact = "Higher values preserve more nearby animation fidelity; lower values let batching reach closer to players.")
  private int bypassRadius = 16;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cluster radius in blocks; explosions in the same world this close together are projected to merge into one packet.", impact = "Higher values pack more explosions into a single packet (bigger reductions, fuzzier center); lower values keep clusters tight.")
  private int mergeRadius = 12;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Incident score (0-100) required before explosion packet batching engages.", impact = "Lower values engage during milder incidents; higher values reserve it for severe incidents.")
  private double engageIncidentScore = 55;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before explosion packet batching engages.", impact = "Lower values engage earlier; higher values reserve batching for heavier load.")
  private double engageTickTimeMs = 55;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds the server must stay below before batching releases.", impact = "Lower values hold batching longer for stability; higher values release sooner.")
  private double releaseTickTimeMs = 42;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained pressure duration required before engaging (milliseconds).", impact = "Higher values ignore short spikes; lower values engage faster.")
  private long sustainEngageMs = 6000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained recovery duration required before releasing (milliseconds).", impact = "Higher values avoid flapping between states; lower values release sooner.")
  private long sustainReleaseMs = 30_000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum pending explosions buffered per world before evaluation flushes early.", impact = "Higher values capture larger same-tick bursts at higher memory cost; lower values cap memory.")
  private int maxBufferedPerWorld = 4096;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Range in blocks for the merged explosion broadcast sent at end of tick.", impact = "Higher values reach more distant observers with the merged packet; lower values cut packet fanout further.")
  private int mergedBroadcastRangeBlocks = 64;

  private transient final Map<UUID, List<PendingExplosion>> pending = new ConcurrentHashMap<>();
  private transient final AtomicLong totalExplosions = new AtomicLong(0L);
  private transient final AtomicLong totalClusters = new AtomicLong(0L);
  private transient final AtomicLong bypassedExplosions = new AtomicLong(0L);
  private transient final AtomicLong observedNmsExplosions = new AtomicLong(0L);
  private transient final AtomicLong mergedBroadcastsSent = new AtomicLong(0L);
  private transient final PressureGate gate = new PressureGate();
  @Getter
  private transient volatile boolean bridgeActive;
  @Getter
  private transient volatile boolean suppressorActive;
  private transient final AtomicLong suppressedPackets = new AtomicLong(0L);

  public FeatureExplosionPacketBatching() {
    super(ID);
  }

  @Override
  public void onActivate() {
    pending.clear();
    totalExplosions.set(0L);
    totalClusters.set(0L);
    bypassedExplosions.set(0L);
    observedNmsExplosions.set(0L);
    mergedBroadcastsSent.set(0L);
    suppressedPackets.set(0L);
    gate.reset();
    installBridgeHook();
  }

  @Override
  public void onDeactivate() {
    pending.clear();
    uninstallBridgeHook();
  }

  public boolean isMeasurementOnly() {
    return !suppressorActive;
  }

  public long readAndResetSuppressedPackets() {
    return suppressedPackets.getAndSet(0L);
  }

  public long readAndResetObservedNmsExplosions() {
    return observedNmsExplosions.getAndSet(0L);
  }

  public long readAndResetMergedBroadcastsSent() {
    return mergedBroadcastsSent.getAndSet(0L);
  }

  private void installBridgeHook() {
    NmsBridge bridge = NmsBridges.get();
    if (bridge == null) {
      bridgeActive = false;
      suppressorActive = false;
      return;
    }
    ExplosionHook hook = new ExplosionHook() {
      @Override
      public void observe(Location center) {
        observedNmsExplosions.incrementAndGet();
      }

      @Override
      public ExplosionDecision onExplodePacket(World world, double x, double y, double z, float radius) {
        return ExplosionDecision.BROADCAST;
      }
    };
    bridgeActive = bridge.installExplosionHook(hook);
    ExplosionPacketSuppressor suppressor = (World world, double x, double y, double z, float radius) -> {
      if (!gate.isEngaged()) {
        return false;
      }
      if (world == null) {
        return false;
      }
      Location loc = new Location(world, x, y, z);
      if (React.hasNearbyPlayer(loc, Math.max(0, bypassRadius))) {
        return false;
      }
      suppressedPackets.incrementAndGet();
      return true;
    };
    suppressorActive = bridge.installExplosionPacketSuppressor(suppressor);
  }

  private void uninstallBridgeHook() {
    NmsBridge bridge = NmsBridges.get();
    if (bridge != null) {
      bridge.uninstallExplosionHook();
      bridge.uninstallExplosionPacketSuppressor();
    }
    bridgeActive = false;
    suppressorActive = false;
  }

  @Override
  public int getTickInterval() {
    return Math.max(250, tickIntervalMS);
  }

  @Override
  public void onTick() {
    updateEngagement();
    flushPending();
  }

  public long readAndResetExplosions() {
    return totalExplosions.getAndSet(0L);
  }

  public long readAndResetClusters() {
    return totalClusters.getAndSet(0L);
  }

  public long bypassedCount() {
    return bypassedExplosions.get();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityExplodeEvent event) {
    Location location = event.getLocation();
    if (location == null || location.getWorld() == null) {
      return;
    }
    enqueue(location);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockExplodeEvent event) {
    if (event.getBlock() == null) {
      return;
    }
    Location location = event.getBlock().getLocation();
    if (location == null || location.getWorld() == null) {
      return;
    }
    enqueue(location);
  }

  private void enqueue(Location location) {
    UUID worldId = location.getWorld().getUID();
    List<PendingExplosion> buffer = pending.computeIfAbsent(worldId, ignored -> new ArrayList<>());
    boolean overCap;
    synchronized (buffer) {
      overCap = buffer.size() >= Math.max(64, maxBufferedPerWorld);
      if (!overCap) {
        buffer.add(new PendingExplosion(location.getX(), location.getY(), location.getZ()));
      }
    }
    if (overCap) {
      broadcastOverflow(location);
    }
  }

  private void broadcastOverflow(Location location) {
    if (!suppressorActive) {
      return;
    }
    World world = location.getWorld();
    if (world == null) {
      return;
    }
    if (React.hasNearbyPlayer(location, Math.max(0, bypassRadius))) {
      return;
    }
    NmsBridge bridge = NmsBridges.get();
    if (bridge == null) {
      return;
    }
    broadcastMerged(bridge, world, location.getX(), location.getY(), location.getZ());
  }

  private void flushPending() {
    if (pending.isEmpty()) {
      return;
    }

    Iterator<Map.Entry<UUID, List<PendingExplosion>>> iterator = pending.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<UUID, List<PendingExplosion>> entry = iterator.next();
      List<PendingExplosion> buffer = entry.getValue();
      List<PendingExplosion> drained;
      synchronized (buffer) {
        if (buffer.isEmpty()) {
          iterator.remove();
          continue;
        }
        drained = new ArrayList<>(buffer);
        buffer.clear();
      }

      World world = Bukkit.getWorld(entry.getKey());
      if (world == null) {
        continue;
      }

      evaluate(world, drained);
    }
  }

  private void evaluate(World world, List<PendingExplosion> explosions) {
    int explosionCount = explosions.size();
    if (explosionCount == 0) {
      return;
    }

    double clusterRadius = Math.max(1, mergeRadius);
    double clusterRadiusSq = clusterRadius * clusterRadius;
    boolean[] consumed = new boolean[explosionCount];
    int clusters = 0;
    int bypassed = 0;
    int reduced = 0;

    for (int i = 0; i < explosionCount; i++) {
      if (consumed[i]) {
        continue;
      }

      PendingExplosion head = explosions.get(i);
      consumed[i] = true;
      List<PendingExplosion> cluster = new ArrayList<>();
      cluster.add(head);

      for (int j = i + 1; j < explosionCount; j++) {
        if (consumed[j]) {
          continue;
        }
        PendingExplosion candidate = explosions.get(j);
        double dx = candidate.x - head.x;
        double dy = candidate.y - head.y;
        double dz = candidate.z - head.z;
        if (dx * dx + dy * dy + dz * dz <= clusterRadiusSq) {
          consumed[j] = true;
          cluster.add(candidate);
        }
      }

      if (clusterContainsNearbyPlayer(world, cluster)) {
        bypassed += cluster.size();
        continue;
      }

      if (broadcastCluster(world, cluster)) {
        clusters++;
        reduced += cluster.size();
      } else {
        bypassed += cluster.size();
      }
    }

    totalExplosions.addAndGet(reduced);
    totalClusters.addAndGet(clusters);
    bypassedExplosions.addAndGet(bypassed);
  }

  private boolean broadcastCluster(World world, List<PendingExplosion> cluster) {
    if (!suppressorActive) {
      return false;
    }
    NmsBridge bridge = NmsBridges.get();
    if (bridge == null) {
      return false;
    }
    double cx = 0D;
    double cy = 0D;
    double cz = 0D;
    int size = cluster.size();
    for (PendingExplosion explosion : cluster) {
      cx += explosion.x;
      cy += explosion.y;
      cz += explosion.z;
    }
    cx /= size;
    cy /= size;
    cz /= size;
    broadcastMerged(bridge, world, cx, cy, cz);
    return true;
  }

  private void broadcastMerged(NmsBridge bridge, World world, double cx, double cy, double cz) {
    int range = Math.max(16, mergedBroadcastRangeBlocks);
    J.s(new Location(world, cx, cy, cz), () -> {
      int reached = bridge.broadcastMergedExplosion(world, cx, cy, cz, 4.0F, range);
      if (reached >= 0) {
        mergedBroadcastsSent.incrementAndGet();
      }
    });
  }

  private boolean clusterContainsNearbyPlayer(World world, List<PendingExplosion> cluster) {
    int radius = Math.max(0, bypassRadius);
    if (radius == 0) {
      return false;
    }

    for (PendingExplosion explosion : cluster) {
      Location loc = new Location(world, explosion.x, explosion.y, explosion.z);
      if (React.hasNearbyPlayer(loc, radius)) {
        return true;
      }
    }
    return false;
  }

  private void updateEngagement() {
    double tickMs = sample(SamplerTickTime.ID);
    double incident = sample(SamplerIncidentScore.ID);
    boolean pressure = tickMs >= engageTickTimeMs || incident >= engageIncidentScore;
    boolean calm = tickMs <= releaseTickTimeMs && incident < engageIncidentScore;
    long now = System.currentTimeMillis();
    gate.update(now, pressure, calm, sustainEngageMs, sustainReleaseMs);
  }

  private static final class PendingExplosion {
    private final double x;
    private final double y;
    private final double z;

    private PendingExplosion(double x, double y, double z) {
      this.x = x;
      this.y = y;
      this.z = z;
    }
  }
}
