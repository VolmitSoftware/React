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
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Chunk Quarantine feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureChunkQuarantine extends ReactFeature implements Listener {
  public static final String ID = "chunk-quarantine";
  private transient final AtomicBoolean maintenanceQueued = new AtomicBoolean(false);
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for chunk quarantine in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 2500;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Rolling enforcement window length used by chunk quarantine (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
  private int windowMS = 1600;
  @art.arcane.react.util.project.config.ConfigDoc(value = "How long a chunk remains quarantined after crossing thresholds in chunk quarantine (milliseconds).", impact = "Higher values keep hot chunks constrained longer; lower values release them sooner.")
  private int quarantineMS = 12000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for score trigger in chunk quarantine.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double scoreTrigger = 145;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum tracked chunks allowed by chunk quarantine.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private int maxTrackedChunks = 4096;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether chunk quarantine applies only during pressure.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean onlyDuringPressure = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for pressure incident score in chunk quarantine.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double pressureIncidentScore = 48;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold for pressure in chunk quarantine (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double pressureTickMS = 58;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Bypasses chunk quarantine handling for bypass near players.", impact = "Enable this to skip enforcement in matching situations; disable it for strict handling.")
  private boolean bypassNearPlayers = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Bypass player radius used by chunk quarantine (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double bypassPlayerRadius = 18;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether chunk quarantine applies track natural spawns.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean trackNaturalSpawns = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether chunk quarantine applies track spawner spawns.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean trackSpawnerSpawns = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether chunk quarantine applies track redstone.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean trackRedstone = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether chunk quarantine applies track physics.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean trackPhysics = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sampling cadence used by chunk quarantine when counting expensive events.", impact = "Lower values sample more frequently for accuracy; higher values reduce overhead with coarser sampling.")
  private int samplePhysicsEveryN = 3;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether chunk quarantine applies track hoppers.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean trackHoppers = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum stale chunk-state entries removed per maintenance cycle in chunk quarantine.", impact = "Higher values clean old state faster but can cause burst CPU; lower values smooth CPU with slower cleanup.")
  private int maxExpiryRemovalsPerCycle = 192;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum chunk-state entries inspected per maintenance cycle in chunk quarantine.", impact = "Higher values clean stale state faster with higher CPU bursts; lower values smooth CPU with slower cleanup of old entries.")
  private int maxExpiryScansPerCycle = 1024;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maintenance cadence used by chunk quarantine for pressure checks and stale-state cleanup (milliseconds).", impact = "Lower values respond and clean faster with more overhead; higher values reduce overhead but react/clean slower.")
  private int maintenanceIntervalMS = 1000;
  private transient Map<UUID, Long2ObjectOpenHashMap<ChunkState>> states = new ConcurrentHashMap<>();
  private transient final AtomicInteger trackedCount = new AtomicInteger(0);
  private transient volatile boolean pressure;
  private transient volatile long lastMaintenanceMS;

  public FeatureChunkQuarantine() {
    super(ID);
  }

  @Override
  public void onActivate() {
    states = new ConcurrentHashMap<>();
    trackedCount.set(0);
    pressure = false;
    maintenanceQueued.set(false);
    lastMaintenanceMS = 0L;
  }

  @Override
  public void onDeactivate() {
    states.clear();
    trackedCount.set(0);
    pressure = false;
    maintenanceQueued.set(false);
    lastMaintenanceMS = 0L;
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    if (maintenanceIntervalMS > 0 && System.currentTimeMillis() - lastMaintenanceMS < maintenanceIntervalMS) {
      return;
    }

    if (!maintenanceQueued.compareAndSet(false, true)) {
      return;
    }

    try {
      art.arcane.react.util.common.scheduling.J.s(() -> {
        try {
          runMaintenance();
        } finally {
          maintenanceQueued.set(false);
        }
      });
    } catch (Throwable ex) {
      maintenanceQueued.set(false);
      React.reportError("Failed to schedule chunk quarantine maintenance: "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()), ex);
    }
  }

  private void runMaintenance() {
    long now = System.currentTimeMillis();
    if (maintenanceIntervalMS > 0 && now - lastMaintenanceMS < maintenanceIntervalMS) {
      return;
    }

    lastMaintenanceMS = now;
    pressure = !onlyDuringPressure || isPressure();
    long expiry = Math.max(windowMS * 8L, quarantineMS * 2L);
    int maxRemovals = Math.max(16, maxExpiryRemovalsPerCycle);
    int maxScans = Math.max(maxRemovals, maxExpiryScansPerCycle);
    int removed = 0;
    int scanned = 0;

    Iterator<Map.Entry<UUID, Long2ObjectOpenHashMap<ChunkState>>> worldIterator = states.entrySet().iterator();
    outer:
    while (worldIterator.hasNext()) {
      Map.Entry<UUID, Long2ObjectOpenHashMap<ChunkState>> worldEntry = worldIterator.next();
      Long2ObjectOpenHashMap<ChunkState> chunkMap = worldEntry.getValue();
      synchronized (chunkMap) {
        ObjectIterator<Long2ObjectOpenHashMap.Entry<ChunkState>> chunkIterator = chunkMap.long2ObjectEntrySet().fastIterator();
        while (chunkIterator.hasNext()) {
          if (scanned++ >= maxScans) {
            break outer;
          }
          ChunkState state = chunkIterator.next().getValue();
          if (now - state.lastHit > expiry && now >= state.quarantinedUntil) {
            chunkIterator.remove();
            trackedCount.decrementAndGet();
            removed++;
            if (removed >= maxRemovals) {
              break outer;
            }
          }
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(CreatureSpawnEvent event) {
    if (!shouldTrackSpawn(event.getSpawnReason())) {
      return;
    }

    boolean quarantined = registerActivity(event.getLocation(), event.getEntity() instanceof Monster ? 1.4 : 1.0);
    if (!quarantined) {
      return;
    }

    if (bypassNearPlayers && React.hasNearbyPlayer(event.getLocation(), bypassPlayerRadius)) {
      return;
    }

    event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(BlockRedstoneEvent event) {
    if (!trackRedstone || event.getOldCurrent() == event.getNewCurrent()) {
      return;
    }

    Block block = event.getBlock();
    boolean quarantined = registerActivity(block.getLocation(), 0.8);
    if (!quarantined) {
      return;
    }

    if (bypassNearPlayers && React.hasNearbyPlayer(block.getLocation(), bypassPlayerRadius)) {
      return;
    }

    event.setNewCurrent(event.getOldCurrent());
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(BlockPhysicsEvent event) {
    if (!trackPhysics) {
      return;
    }

    int stride = Math.max(1, samplePhysicsEveryN);
    if (stride > 1 && ThreadLocalRandom.current().nextInt(stride) != 0) {
      return;
    }

    Block block = event.getBlock();
    boolean quarantined = registerActivity(block.getLocation(), 0.45);
    if (!quarantined) {
      return;
    }

    if (bypassNearPlayers && React.hasNearbyPlayer(block.getLocation(), bypassPlayerRadius)) {
      return;
    }

    event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void on(InventoryMoveItemEvent event) {
    if (!trackHoppers) {
      return;
    }

    Location location = resolveHopperLocation(event);
    if (location == null) {
      return;
    }

    boolean quarantined = registerActivity(location, 0.6);
    if (!quarantined) {
      return;
    }

    if (bypassNearPlayers && React.hasNearbyPlayer(location, bypassPlayerRadius)) {
      return;
    }

    event.setCancelled(true);
  }

  private boolean registerActivity(Location location, double score) {
    if (location == null || location.getWorld() == null) {
      return false;
    }

    UUID worldId = location.getWorld().getUID();
    long chunkKey = packChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    Long2ObjectOpenHashMap<ChunkState> chunkMap = states.computeIfAbsent(worldId, ignored -> new Long2ObjectOpenHashMap<>());
    long now = System.currentTimeMillis();
    ChunkState state;
    synchronized (chunkMap) {
      state = chunkMap.get(chunkKey);
      if (state == null) {
        if (trackedCount.get() >= maxTrackedChunks) {
          return false;
        }
        state = new ChunkState(now);
        chunkMap.put(chunkKey, state);
        trackedCount.incrementAndGet();
      }
    }

    state.rollover(windowMS, now);
    state.score += score;

    if (pressure && state.score >= scoreTrigger) {
      state.quarantinedUntil = Math.max(state.quarantinedUntil, now + quarantineMS);
      state.score = scoreTrigger * 0.5;
    }

    return now < state.quarantinedUntil;
  }

  private static long packChunk(int cx, int cz) {
    return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
  }

  private boolean shouldTrackSpawn(CreatureSpawnEvent.SpawnReason reason) {
    boolean natural = switch (reason) {
      case NATURAL, NETHER_PORTAL, REINFORCEMENTS, JOCKEY, PATROL,
           RAID -> true;
      default -> false;
    };

    boolean spawner = reason == CreatureSpawnEvent.SpawnReason.SPAWNER || "TRIAL_SPAWNER".equals(reason.name());
    return (trackNaturalSpawns && natural) || (trackSpawnerSpawns && spawner);
  }

  private boolean isPressure() {
    return sample(SamplerTickTime.ID) >= pressureTickMS
        || sample(SamplerIncidentScore.ID) >= pressureIncidentScore;
  }

  private Location resolveHopperLocation(InventoryMoveItemEvent event) {
    if (event.getSource().getHolder() instanceof Hopper source) {
      return source.getBlock().getLocation();
    }

    if (event.getDestination().getHolder() instanceof Hopper destination) {
      return destination.getBlock().getLocation();
    }

    return null;
  }

  private static final class ChunkState {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by chunk quarantine to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long start;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by chunk quarantine to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long lastHit;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by chunk quarantine to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long quarantinedUntil;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for score in chunk quarantine.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
    private double score;

    private ChunkState(long now) {
      start = now;
      lastHit = now;
      quarantinedUntil = 0L;
      score = 0D;
    }

    private void rollover(int windowMS, long now) {
      if (now - start > windowMS) {
        start = now;
        score = 0D;
      }

      lastHit = now;
    }
  }

}
