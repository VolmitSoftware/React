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
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Explosion Packet Batching feature. Collects pressure-gated explosions between evaluation flushes, clusters them by mergeRadius, and reports how many ClientboundExplodePackets are removed. With the versioned explosion hooks available, candidates whose vanilla packets were intercepted broadcast one merged packet; otherwise the feature remains measurement-only. Explosions within bypassRadius of a player keep vanilla packets for animation and sound fidelity.")
public class FeatureExplosionPacketBatching extends ReactFeature implements Listener {
  public static final String ID = "explosion-packet-batching";
  private static final int MAX_MERGED_BROADCASTS_PER_TICK = 1024;
  private static final int MAX_SUPPRESSED_EXPLOSION_DEBT = 65_536;

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
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum pending explosions buffered per world between evaluations.", impact = "Higher values capture larger bursts at higher memory cost; overflow falls back to vanilla packets.")
  private int maxBufferedPerWorld = 4096;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Range in blocks for each retained merged explosion broadcast.", impact = "Higher values reach more distant observers with the merged packet; lower values cut packet fanout further.")
  private int mergedBroadcastRangeBlocks = 64;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum merged explosion clusters broadcast across all worlds per server tick.", impact = "Higher values flush bursts sooner but multiply recipient fanout; values clamp to 1..1024.")
  private int maxMergedBroadcastsPerTick = 64;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Global maximum suppressed explosions awaiting a merged broadcast.", impact = "Once full, additional explosion packets stay vanilla; values clamp to 0..65536.")
  private int maxSuppressedExplosionDebt = 4096;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum time to drain suppressed explosion broadcasts during deactivation (milliseconds).", impact = "A timeout fails deactivation and retains unsent broadcasts instead of silently losing their sound and particles.")
  private int shutdownDrainTimeoutMS = 2000;

  private transient final Map<UUID, PendingWorldBuffer> pending = new ConcurrentHashMap<>();
  private transient final Queue<BroadcastBatch> retainedBroadcasts = new ConcurrentLinkedQueue<>();
  private transient final ReentrantLock broadcastDrainLock = new ReentrantLock();
  private transient final AtomicInteger retainedClusterCount = new AtomicInteger(0);
  private transient final AtomicInteger suppressedExplosionDebt = new AtomicInteger(0);
  private transient final AtomicLong totalExplosions = new AtomicLong(0L);
  private transient final AtomicLong totalClusters = new AtomicLong(0L);
  private transient final AtomicLong bypassedExplosions = new AtomicLong(0L);
  private transient final AtomicLong observedNmsExplosions = new AtomicLong(0L);
  private transient final AtomicLong mergedBroadcastsSent = new AtomicLong(0L);
  private transient final AtomicLong unavailableWorldSuppressedExplosions = new AtomicLong(0L);
  private transient final PressureGate gate = new PressureGate();
  @Getter
  private transient volatile boolean bridgeActive;
  @Getter
  private transient volatile boolean suppressorActive;
  private transient volatile boolean acceptingCandidates;
  private transient final AtomicLong suppressedPackets = new AtomicLong(0L);
  private transient int broadcastTaskId;

  public FeatureExplosionPacketBatching() {
    super(ID);
  }

  @Override
  public void onActivate() {
    acceptingCandidates = false;
    if (hasRetainedSuppressionDebt()) {
      throw retainedShutdownFailure("cannot activate while a previous shutdown still owns suppressed explosions");
    }
    pending.clear();
    retainedBroadcasts.clear();
    retainedClusterCount.set(0);
    suppressedExplosionDebt.set(0);
    totalExplosions.set(0L);
    totalClusters.set(0L);
    bypassedExplosions.set(0L);
    observedNmsExplosions.set(0L);
    mergedBroadcastsSent.set(0L);
    suppressedPackets.set(0L);
    gate.reset();
    installBridgeHook();
    if (suppressorActive) {
      broadcastTaskId = J.sr(this::drainRetainedBroadcasts, 1);
    }
    acceptingCandidates = suppressorActive;
  }

  @Override
  public void onDeactivate() {
    acceptingCandidates = false;
    if (broadcastTaskId != 0) {
      J.csr(broadcastTaskId);
      broadcastTaskId = 0;
    }
    flushPending();
    drainShutdownBroadcasts();
    uninstallBridgeHook();
    pending.clear();
    retainedBroadcasts.clear();
    retainedClusterCount.set(0);
    suppressedExplosionDebt.set(0);
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

  public long readAndResetUnavailableWorldSuppressedExplosions() {
    return unavailableWorldSuppressedExplosions.getAndSet(0L);
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
    if (J.isFoliaThreading()) {
      suppressorActive = false;
      return;
    }
    ExplosionPacketSuppressor suppressor = (World world, double x, double y, double z, float radius, int packetCount) -> {
      if (world == null || !acceptingCandidates) {
        return false;
      }
      PendingWorldBuffer buffer = pending.get(world.getUID());
      return admitSuppression(buffer, x, y, z, packetCount);
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
    if (!acceptingCandidates || !gate.isEngaged()) {
      return;
    }
    if (React.hasNearbyPlayer(location, Math.max(0, bypassRadius))) {
      bypassedExplosions.incrementAndGet();
      return;
    }

    UUID worldId = location.getWorld().getUID();
    PendingExplosion explosion = new PendingExplosion(location.getX(), location.getY(), location.getZ());
    while (true) {
      PendingWorldBuffer buffer = pending.computeIfAbsent(
          worldId,
          ignored -> new PendingWorldBuffer(location.getWorld())
      );
      AddResult result = buffer.tryAdd(explosion, Math.max(64, maxBufferedPerWorld));
      if (result == AddResult.ACCEPTED) {
        return;
      }
      if (result == AddResult.FULL) {
        bypassedExplosions.incrementAndGet();
        return;
      }
      pending.remove(worldId, buffer);
    }
  }

  private void flushPending() {
    if (pending.isEmpty()) {
      return;
    }

    for (Map.Entry<UUID, PendingWorldBuffer> entry : pending.entrySet()) {
      PendingWorldBuffer buffer = entry.getValue();
      List<PendingExplosion> drained = buffer.drainSuppressed();
      pending.remove(entry.getKey(), buffer);
      if (drained.isEmpty()) {
        continue;
      }

      UUID worldId = entry.getKey();
      World world = buffer.world();
      try {
        evaluate(worldId, world, drained);
      } catch (Throwable throwable) {
        retainUnmerged(worldId, world, drained);
        React.reportError(throwable);
      }
    }
  }

  private void evaluate(UUID worldId, World world, List<PendingExplosion> explosions) {
    int explosionCount = explosions.size();
    if (explosionCount == 0) {
      return;
    }

    List<MergedCluster> merged = new ArrayList<>();
    for (List<PendingExplosion> cluster : clusterExplosions(explosions, Math.max(1D, mergeRadius))) {
      merged.add(centerOf(cluster));
    }
    retainClusters(worldId, world, merged);
  }

  static List<List<PendingExplosion>> clusterExplosions(List<PendingExplosion> explosions, double radius) {
    int explosionCount = explosions.size();
    if (explosionCount == 0) {
      return List.of();
    }

    double cellSize = Math.max(1D, radius);
    double radiusSquared = cellSize * cellSize;
    Map<Cell, List<Integer>> indicesByCell = new HashMap<>(explosionCount * 2);
    for (int index = 0; index < explosionCount; index++) {
      PendingExplosion explosion = explosions.get(index);
      Cell cell = Cell.of(explosion, cellSize);
      indicesByCell.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(index);
    }

    boolean[] consumed = new boolean[explosionCount];
    List<List<PendingExplosion>> clusters = new ArrayList<>();
    for (int headIndex = 0; headIndex < explosionCount; headIndex++) {
      if (consumed[headIndex]) {
        continue;
      }

      PendingExplosion head = explosions.get(headIndex);
      Cell headCell = Cell.of(head, cellSize);
      List<PendingExplosion> cluster = new ArrayList<>();
      cluster.add(head);
      consumed[headIndex] = true;

      for (int cellX = headCell.x - 1; cellX <= headCell.x + 1; cellX++) {
        for (int cellY = headCell.y - 1; cellY <= headCell.y + 1; cellY++) {
          for (int cellZ = headCell.z - 1; cellZ <= headCell.z + 1; cellZ++) {
            List<Integer> candidates = indicesByCell.get(new Cell(cellX, cellY, cellZ));
            if (candidates == null) {
              continue;
            }

            for (int candidateIndex : candidates) {
              if (candidateIndex <= headIndex || consumed[candidateIndex]) {
                continue;
              }

              PendingExplosion candidate = explosions.get(candidateIndex);
              double dx = candidate.x - head.x;
              double dy = candidate.y - head.y;
              double dz = candidate.z - head.z;
              if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                consumed[candidateIndex] = true;
                cluster.add(candidate);
              }
            }
          }
        }
      }

      clusters.add(cluster);
    }

    return clusters;
  }

  private MergedCluster centerOf(List<PendingExplosion> cluster) {
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
    return new MergedCluster(cx, cy, cz, size);
  }

  void retainClusters(World world, List<MergedCluster> clusters) {
    retainClusters(world == null ? null : world.getUID(), world, clusters);
  }

  void retainClusters(UUID worldId, List<MergedCluster> clusters) {
    retainClusters(worldId, null, clusters);
  }

  private void retainClusters(UUID worldId, World world, List<MergedCluster> clusters) {
    if (clusters.isEmpty()) {
      return;
    }
    List<MergedCluster> retained = List.copyOf(clusters);
    retainedClusterCount.addAndGet(retained.size());
    retainedBroadcasts.offer(new BroadcastBatch(worldId, world, retained));
  }

  int drainRetainedBroadcasts(NmsBridge bridge, int maximumBroadcasts) {
    if (bridge == null || maximumBroadcasts <= 0) {
      return 0;
    }
    if (!broadcastDrainLock.tryLock()) {
      return 0;
    }

    try {
      return drainRetainedBroadcastsLocked(bridge, maximumBroadcasts, Long.MAX_VALUE);
    } finally {
      broadcastDrainLock.unlock();
    }
  }

  private int drainRetainedBroadcastsLocked(NmsBridge bridge, int maximumBroadcasts, long deadlineNanos) {
    int range = Math.max(16, mergedBroadcastRangeBlocks);
    int completed = 0;
    int attempts = 0;
    while (attempts < maximumBroadcasts) {
      if (deadlineNanos != Long.MAX_VALUE && System.nanoTime() - deadlineNanos >= 0L) {
        break;
      }
      BroadcastBatch batch = retainedBroadcasts.peek();
      if (batch == null) {
        break;
      }
      MergedCluster cluster = batch.peek();
      if (cluster == null) {
        retainedBroadcasts.remove(batch);
        continue;
      }

      attempts++;
      World world = batch.resolveWorld();
      if (world == null) {
        if (batch.worldAvailability() == WorldAvailability.UNAVAILABLE) {
          disposeUnavailableWorld(batch);
          continue;
        }
        break;
      }

      boolean sent = bridge.broadcastMergedExplosion(
          world,
          cluster.x,
          cluster.y,
          cluster.z,
          4.0F,
          range
      );
      if (!sent) {
        break;
      }

      batch.acknowledge();
      retainedClusterCount.decrementAndGet();
      releaseSuppressedDebt(cluster.size);
      totalExplosions.addAndGet(cluster.size);
      totalClusters.incrementAndGet();
      mergedBroadcastsSent.incrementAndGet();
      completed++;
      if (batch.exhausted()) {
        retainedBroadcasts.remove(batch);
      }
    }
    return completed;
  }

  private void disposeUnavailableWorld(BroadcastBatch batch) {
    BatchDisposition disposition = batch.disposeRemaining();
    retainedBroadcasts.remove(batch);
    if (disposition.clusterCount() <= 0) {
      return;
    }
    retainedClusterCount.addAndGet(-disposition.clusterCount());
    releaseSuppressedDebt(disposition.explosionCount());
    unavailableWorldSuppressedExplosions.addAndGet(disposition.explosionCount());
    React.reportError(new IllegalStateException(
        "Explosion packet batching could not replace " + disposition.explosionCount()
            + " suppressed explosions in " + disposition.clusterCount() + " retained broadcasts because world "
            + batch.worldId() + " is no longer loaded"
    ));
  }

  int retainedClusterCount() {
    return retainedClusterCount.get();
  }

  int suppressedExplosionDebt() {
    return suppressedExplosionDebt.get();
  }

  boolean admitSuppression(
      PendingWorldBuffer buffer,
      double x,
      double y,
      double z,
      int packetCount
  ) {
    if (buffer == null) {
      return false;
    }
    if (!reserveSuppressedDebt()) {
      buffer.discardCandidate(x, y, z);
      return false;
    }

    if (!acceptingCandidates || !buffer.markSuppressed(x, y, z)) {
      if (!acceptingCandidates) {
        buffer.discardCandidate(x, y, z);
      }
      releaseSuppressedDebt(1);
      return false;
    }
    suppressedPackets.addAndGet(Math.max(1, packetCount));
    return true;
  }

  private void drainRetainedBroadcasts() {
    NmsBridge bridge = NmsBridges.get();
    if (bridge == null) {
      return;
    }
    int budget = Math.max(1, Math.min(MAX_MERGED_BROADCASTS_PER_TICK, maxMergedBroadcastsPerTick));
    drainRetainedBroadcasts(bridge, budget);
  }

  private void drainShutdownBroadcasts() {
    if (!hasRetainedSuppressionDebt()) {
      return;
    }
    NmsBridge bridge = NmsBridges.get();
    if (bridge == null) {
      throw retainedShutdownFailure("has no NMS bridge available for its shutdown drain");
    }

    long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, shutdownDrainTimeoutMS));
    long deadlineNanos = System.nanoTime() + timeoutNanos;
    boolean locked;
    try {
      locked = broadcastDrainLock.tryLock(timeoutNanos, TimeUnit.NANOSECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while draining suppressed explosion broadcasts", exception);
    }
    if (!locked) {
      if (!hasRetainedSuppressionDebt()) {
        return;
      }
      throw retainedShutdownFailure("timed out waiting for an in-flight broadcast drain");
    }

    try {
      drainRetainedBroadcastsLocked(bridge, Integer.MAX_VALUE, deadlineNanos);
      if (!hasRetainedSuppressionDebt()) {
        return;
      }
      if (System.nanoTime() - deadlineNanos >= 0L) {
        throw retainedShutdownFailure("timed out during its shutdown drain");
      }
      throw retainedShutdownFailure("could not deliver the next replacement broadcast");
    } finally {
      broadcastDrainLock.unlock();
    }
  }

  private void retainUnmerged(UUID worldId, World world, List<PendingExplosion> explosions) {
    List<MergedCluster> replacements = new ArrayList<>(explosions.size());
    for (PendingExplosion explosion : explosions) {
      replacements.add(new MergedCluster(explosion.x, explosion.y, explosion.z, 1));
    }
    retainClusters(worldId, world, replacements);
  }

  private boolean hasRetainedSuppressionDebt() {
    return retainedClusterCount.get() > 0 || suppressedExplosionDebt.get() > 0;
  }

  private IllegalStateException retainedShutdownFailure(String reason) {
    return new IllegalStateException(
        "Explosion packet batching " + reason + "; retained " + retainedClusterCount.get()
            + " merged broadcasts for " + suppressedExplosionDebt.get() + " suppressed explosions"
    );
  }

  private boolean reserveSuppressedDebt() {
    int limit = Math.max(0, Math.min(MAX_SUPPRESSED_EXPLOSION_DEBT, maxSuppressedExplosionDebt));
    while (acceptingCandidates) {
      int current = suppressedExplosionDebt.get();
      if (current >= limit) {
        return false;
      }
      if (suppressedExplosionDebt.compareAndSet(current, current + 1)) {
        return true;
      }
    }
    return false;
  }

  private void releaseSuppressedDebt(int released) {
    if (released <= 0) {
      return;
    }
    while (true) {
      int current = suppressedExplosionDebt.get();
      int updated = Math.max(0, current - released);
      if (suppressedExplosionDebt.compareAndSet(current, updated)) {
        return;
      }
    }
  }

  private void updateEngagement() {
    double tickMs = sample(SamplerTickTime.ID);
    double incident = sample(SamplerIncidentScore.ID);
    boolean pressure = tickMs >= engageTickTimeMs || incident >= engageIncidentScore;
    boolean calm = tickMs <= releaseTickTimeMs && incident < engageIncidentScore;
    long now = System.currentTimeMillis();
    gate.update(now, pressure, calm, sustainEngageMs, sustainReleaseMs);
  }

  static final class PendingExplosion {
    private final double x;
    private final double y;
    private final double z;
    private boolean suppressed;

    PendingExplosion(double x, double y, double z) {
      this.x = x;
      this.y = y;
      this.z = z;
    }
  }

  record MergedCluster(double x, double y, double z, int size) {
  }

  private record BatchDisposition(int clusterCount, int explosionCount) {
  }

  private static final class BroadcastBatch {
    private final UUID worldId;
    private final List<MergedCluster> clusters;
    private WeakReference<World> world;
    private WorldAvailability worldAvailability;
    private int next;

    private BroadcastBatch(UUID worldId, World world, List<MergedCluster> clusters) {
      this.worldId = worldId;
      this.world = new WeakReference<>(world);
      this.clusters = clusters;
      worldAvailability = world == null ? WorldAvailability.RETRY : WorldAvailability.RESOLVED;
    }

    private synchronized World resolveWorld() {
      World retained = world.get();
      if (retained != null) {
        worldAvailability = WorldAvailability.RESOLVED;
        return retained;
      }
      if (worldId == null) {
        worldAvailability = WorldAvailability.RETRY;
        return null;
      }

      try {
        Server server = Bukkit.getServer();
        if (server == null) {
          worldAvailability = WorldAvailability.RETRY;
          return null;
        }
        World resolved = server.getWorld(worldId);
        if (resolved == null) {
          worldAvailability = WorldAvailability.UNAVAILABLE;
          return null;
        }
        world = new WeakReference<>(resolved);
        worldAvailability = WorldAvailability.RESOLVED;
        return resolved;
      } catch (Throwable throwable) {
        worldAvailability = WorldAvailability.RETRY;
        React.reportError(new IllegalStateException(
            "Explosion packet batching failed to resolve retained world " + worldId,
            throwable
        ));
        return null;
      }
    }

    private synchronized WorldAvailability worldAvailability() {
      return worldAvailability;
    }

    private UUID worldId() {
      return worldId;
    }

    private synchronized MergedCluster peek() {
      return next >= clusters.size() ? null : clusters.get(next);
    }

    private synchronized void acknowledge() {
      if (next < clusters.size()) {
        next++;
      }
    }

    private synchronized boolean exhausted() {
      return next >= clusters.size();
    }

    private synchronized BatchDisposition disposeRemaining() {
      int remainingClusters = clusters.size() - next;
      int remainingExplosions = 0;
      while (next < clusters.size()) {
        remainingExplosions += clusters.get(next).size;
        next++;
      }
      return new BatchDisposition(remainingClusters, remainingExplosions);
    }
  }

  private enum WorldAvailability {
    RESOLVED,
    UNAVAILABLE,
    RETRY
  }

  static final class PendingWorldBuffer {
    private final WeakReference<World> world;
    private final List<PendingExplosion> explosions = new ArrayList<>();
    private final Map<Cell, ArrayDeque<PendingExplosion>> candidates = new HashMap<>();
    private boolean retired;

    PendingWorldBuffer(World world) {
      this.world = new WeakReference<>(world);
    }

    World world() {
      return world.get();
    }

    synchronized AddResult tryAdd(PendingExplosion explosion, int capacity) {
      if (retired) {
        return AddResult.RETIRED;
      }
      if (explosions.size() >= capacity) {
        return AddResult.FULL;
      }
      explosions.add(explosion);
      candidates.computeIfAbsent(Cell.of(explosion, 1D), ignored -> new ArrayDeque<>()).addLast(explosion);
      return AddResult.ACCEPTED;
    }

    synchronized boolean markSuppressed(double x, double y, double z) {
      PendingExplosion explosion = pollCandidate(x, y, z);
      if (explosion == null) {
        return false;
      }
      explosion.suppressed = true;
      return true;
    }

    synchronized void discardCandidate(double x, double y, double z) {
      pollCandidate(x, y, z);
    }

    private PendingExplosion pollCandidate(double x, double y, double z) {
      Cell cell = Cell.of(x, y, z);
      ArrayDeque<PendingExplosion> cellCandidates = candidates.get(cell);
      if (cellCandidates == null) {
        return null;
      }
      PendingExplosion explosion = cellCandidates.pollFirst();
      if (cellCandidates.isEmpty()) {
        candidates.remove(cell);
      }
      return explosion;
    }

    synchronized List<PendingExplosion> drainSuppressed() {
      retired = true;
      if (explosions.isEmpty()) {
        return List.of();
      }
      List<PendingExplosion> drained = new ArrayList<>(explosions.size());
      for (PendingExplosion explosion : explosions) {
        if (explosion.suppressed) {
          drained.add(explosion);
        }
      }
      explosions.clear();
      candidates.clear();
      return drained;
    }
  }

  enum AddResult {
    ACCEPTED,
    FULL,
    RETIRED
  }

  private record Cell(int x, int y, int z) {
    private static Cell of(PendingExplosion explosion, double cellSize) {
      return new Cell(
          (int) Math.floor(explosion.x / cellSize),
          (int) Math.floor(explosion.y / cellSize),
          (int) Math.floor(explosion.z / cellSize)
      );
    }

    private static Cell of(double x, double y, double z) {
      return new Cell(
          (int) Math.floor(x),
          (int) Math.floor(y),
          (int) Math.floor(z)
      );
    }
  }
}
