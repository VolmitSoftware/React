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
import art.arcane.react.nms.FallingBlockTickHook;
import art.arcane.react.nms.NmsBridge;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.nms.TickDecision;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Configuration for Lazy Gravity feature. Tracks FallingBlock entities on clear vertical paths and reports eligible tick skips. Under configured pressure, the versioned hook skips eligible ticks outside bypassRadius; without the hook the feature remains measurement-only. Column changes return affected blocks to vanilla physics.")
public class FeatureLazyGravity extends ReactFeature implements Listener {
  public static final String ID = "lazy-gravity";
  private static final double VANILLA_GRAVITY = 0.04D;
  private static final double VANILLA_DRAG = 0.98D;
  private static final double TERMINAL_VELOCITY = 3.92D;
  private static final int MAX_SCAN_DEPTH = 384;

  @ConfigDoc(value = "Main evaluation interval for lazy gravity in milliseconds.", impact = "Lower values reap completed falling-blocks faster but consume more CPU; higher values reduce overhead.")
  private int tickIntervalMS = 1000;
  @ConfigDoc(value = "Bypass radius around players in blocks; falling blocks inside skip lazy gravity entirely.", impact = "Higher values keep more falling-block animations vanilla near players; lower values let lazy gravity reach closer.")
  private int bypassRadius = 24;
  @ConfigDoc(value = "Incident score (0-100) required before lazy gravity engages.", impact = "Lower values engage during milder incidents; higher values reserve it for severe incidents.")
  private double engageIncidentScore = 55;
  @ConfigDoc(value = "Average tick milliseconds required before lazy gravity engages.", impact = "Lower values engage earlier; higher values reserve it for heavier load.")
  private double engageTickTimeMs = 55;
  @ConfigDoc(value = "Average tick milliseconds the server must stay below before lazy gravity releases.", impact = "Lower values hold tracking longer for stability; higher values release sooner.")
  private double releaseTickTimeMs = 42;
  @ConfigDoc(value = "Sustained pressure duration required before engaging (milliseconds).", impact = "Higher values ignore short spikes; lower values engage faster.")
  private long sustainEngageMs = 6000;
  @ConfigDoc(value = "Sustained recovery duration required before releasing (milliseconds).", impact = "Higher values avoid flapping between states; lower values release sooner.")
  private long sustainReleaseMs = 30_000;
  @ConfigDoc(value = "Maximum tracked falling-block tasks at once.", impact = "Higher values cover larger sand-dropper farms at higher memory cost; lower values clamp memory.")
  private int maxTrackedTasks = 8192;
  @ConfigDoc(value = "Maximum entries reaped per maintenance tick when scanning expired tasks.", impact = "Higher values keep the task map cleaner; lower values smooth CPU at the cost of slower reaping.")
  private int reapPerTick = 1024;

  private transient final Map<UUID, FallingTask> tasks = new ConcurrentHashMap<>();
  private transient final Map<UUID, Long2ObjectOpenHashMap<UUID>> columnIndex = new ConcurrentHashMap<>();
  private transient final AtomicLong projectedSkippedTicks = new AtomicLong(0L);
  private transient final AtomicLong tracked = new AtomicLong(0L);
  private transient final AtomicLong bypassed = new AtomicLong(0L);
  private transient final AtomicLong invalidated = new AtomicLong(0L);
  private transient final AtomicLong skippedFallingTicks = new AtomicLong(0L);
  private transient final PressureGate gate = new PressureGate();
  @Getter
  private transient volatile boolean bridgeActive;

  public FeatureLazyGravity() {
    super(ID);
  }

  @Override
  public void onActivate() {
    tasks.clear();
    columnIndex.clear();
    projectedSkippedTicks.set(0L);
    tracked.set(0L);
    bypassed.set(0L);
    invalidated.set(0L);
    skippedFallingTicks.set(0L);
    gate.reset();
    installBridgeHook();
  }

  @Override
  public void onDeactivate() {
    tasks.clear();
    columnIndex.clear();
    uninstallBridgeHook();
  }

  public boolean isMeasurementOnly() {
    return !bridgeActive;
  }

  public long readAndResetSkippedFallingTicks() {
    return skippedFallingTicks.getAndSet(0L);
  }

  private void installBridgeHook() {
    NmsBridge bridge = NmsBridges.get();
    if (bridge == null) {
      bridgeActive = false;
      return;
    }
    FallingBlockTickHook hook = (FallingBlock falling) -> {
      if (!gate.isEngaged()) {
        return TickDecision.RUN_VANILLA;
      }
      Location at = falling.getLocation();
      if (at == null) {
        return TickDecision.RUN_VANILLA;
      }
      if (React.hasNearbyPlayer(at, bypassRadius)) {
        return TickDecision.RUN_VANILLA;
      }
      FallingTask task = tasks.get(falling.getUniqueId());
      if (task == null) {
        return TickDecision.RUN_VANILLA;
      }
      if (System.currentTimeMillis() >= task.expiresAtMs - 200L) {
        return TickDecision.RUN_VANILLA;
      }
      skippedFallingTicks.incrementAndGet();
      return TickDecision.SKIP;
    };
    bridgeActive = bridge.installFallingBlockTickHook(hook);
  }

  private void uninstallBridgeHook() {
    NmsBridge bridge = NmsBridges.get();
    if (bridge != null) {
      bridge.uninstallFallingBlockTickHook();
    }
    bridgeActive = false;
  }

  @Override
  public int getTickInterval() {
    return Math.max(250, tickIntervalMS);
  }

  @Override
  public void onTick() {
    updateEngagement();
    reapTasks();
  }

  public long readAndResetProjectedSkippedTicks() {
    return projectedSkippedTicks.getAndSet(0L);
  }

  public long activeTaskCount() {
    return tracked.get();
  }

  public long bypassedCount() {
    return bypassed.get();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityChangeBlockEvent event) {
    Entity entity = event.getEntity();
    if (!(entity instanceof FallingBlock falling)) {
      return;
    }

    if (tasks.size() >= Math.max(64, maxTrackedTasks)) {
      return;
    }

    Location spawn = falling.getLocation();
    if (spawn == null || spawn.getWorld() == null) {
      return;
    }

    if (React.hasNearbyPlayer(spawn, bypassRadius)) {
      bypassed.incrementAndGet();
      return;
    }

    int landingY = scanLandingY(spawn);
    if (landingY < 0) {
      return;
    }

    double fallDistance = Math.max(0D, spawn.getY() - (landingY + 1));
    if (fallDistance < 1D) {
      return;
    }

    double initialDownwardVelocity = -falling.getVelocity().getY();
    int landingTick = computeLandingTick(fallDistance, initialDownwardVelocity);
    if (landingTick <= 1) {
      return;
    }

    UUID worldId = spawn.getWorld().getUID();
    UUID entityId = falling.getUniqueId();
    long columnKey = packColumn(spawn.getBlockX(), spawn.getBlockZ());
    long now = System.currentTimeMillis();
    long expectedExpiry = now + Math.max(50L, landingTick * 50L) + 300L;

    FallingTask task = new FallingTask(worldId, columnKey, expectedExpiry);
    tasks.put(entityId, task);
    Long2ObjectOpenHashMap<UUID> world = columnIndex.computeIfAbsent(worldId, ignored -> new Long2ObjectOpenHashMap<>());
    synchronized (world) {
      world.put(columnKey, entityId);
    }

    tracked.incrementAndGet();
    projectedSkippedTicks.addAndGet(landingTick - 1);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent event) {
    invalidateColumn(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent event) {
    invalidateColumn(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPhysicsEvent event) {
    invalidateColumn(event.getBlock());
  }

  private void invalidateColumn(Block block) {
    if (block == null) {
      return;
    }

    World world = block.getWorld();
    if (world == null) {
      return;
    }

    Long2ObjectOpenHashMap<UUID> map = columnIndex.get(world.getUID());
    if (map == null) {
      return;
    }

    long columnKey = packColumn(block.getX(), block.getZ());
    UUID entityId;
    synchronized (map) {
      entityId = map.remove(columnKey);
    }

    if (entityId == null) {
      return;
    }

    if (tasks.remove(entityId) != null) {
      invalidated.incrementAndGet();
    }
  }

  private int scanLandingY(Location spawn) {
    World world = spawn.getWorld();
    int x = spawn.getBlockX();
    int z = spawn.getBlockZ();
    int startY = spawn.getBlockY() - 1;
    int min = world.getMinHeight();
    int scanned = 0;

    for (int y = startY; y >= min && scanned < MAX_SCAN_DEPTH; y--) {
      scanned++;
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return -1;
      }

      Block block = world.getBlockAt(x, y, z);
      Material type = block.getType();
      if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
        continue;
      }

      return y;
    }

    return -1;
  }

  private int computeLandingTick(double fallDistance, double initialDownwardVelocity) {
    double v = Math.min(TERMINAL_VELOCITY, Math.max(0D, initialDownwardVelocity));
    double traveled = 0D;
    int ticks = 0;
    int safety = 4096;

    while (traveled < fallDistance && safety-- > 0) {
      v = (v + VANILLA_GRAVITY) * VANILLA_DRAG;
      if (v > TERMINAL_VELOCITY) {
        v = TERMINAL_VELOCITY;
      }
      traveled += v;
      ticks++;
    }

    return ticks;
  }

  private void reapTasks() {
    if (tasks.isEmpty()) {
      return;
    }

    long now = System.currentTimeMillis();
    int budget = Math.max(64, reapPerTick);
    int reaped = 0;

    Iterator<Map.Entry<UUID, FallingTask>> iterator = tasks.entrySet().iterator();
    while (iterator.hasNext() && reaped < budget) {
      Map.Entry<UUID, FallingTask> entry = iterator.next();
      FallingTask task = entry.getValue();
      if (task.expiresAtMs > now) {
        continue;
      }

      iterator.remove();
      tracked.decrementAndGet();
      Long2ObjectOpenHashMap<UUID> world = columnIndex.get(task.worldId);
      if (world == null) {
        continue;
      }

      synchronized (world) {
        UUID currentOwner = world.get(task.columnKey);
        if (currentOwner != null && currentOwner.equals(entry.getKey())) {
          world.remove(task.columnKey);
        }
        if (world.isEmpty()) {
          columnIndex.remove(task.worldId, world);
        }
      }
      reaped++;
    }

    Iterator<Map.Entry<UUID, Long2ObjectOpenHashMap<UUID>>> worldIterator = columnIndex.entrySet().iterator();
    while (worldIterator.hasNext()) {
      Map.Entry<UUID, Long2ObjectOpenHashMap<UUID>> entry = worldIterator.next();
      Long2ObjectOpenHashMap<UUID> map = entry.getValue();
      synchronized (map) {
        ObjectIterator<Long2ObjectOpenHashMap.Entry<UUID>> mapIterator = map.long2ObjectEntrySet().fastIterator();
        while (mapIterator.hasNext()) {
          Long2ObjectOpenHashMap.Entry<UUID> mapEntry = mapIterator.next();
          if (!tasks.containsKey(mapEntry.getValue())) {
            mapIterator.remove();
          }
        }
        if (map.isEmpty()) {
          worldIterator.remove();
        }
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

  private static long packColumn(int x, int z) {
    return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
  }

  private static final class FallingTask {
    private final UUID worldId;
    private final long columnKey;
    private final long expiresAtMs;

    private FallingTask(UUID worldId, long columnKey, long expiresAtMs) {
      this.worldId = worldId;
      this.columnKey = columnKey;
      this.expiresAtMs = expiresAtMs;
    }
  }
}
