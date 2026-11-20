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

import art.arcane.react.api.feature.PressureGate;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Configuration for Lazy Gravity feature. Tracks FallingBlock entities on clear vertical paths and reports eligible tick skips. Under configured pressure, the versioned hook skips eligible ticks outside bypassRadius; without the hook the feature remains measurement-only. Column changes return affected blocks to vanilla physics.")
public class FeatureLazyGravity extends ReactFeature {
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
  private transient final Map<UUID, LazyGravityColumns> columnIndex = new ConcurrentHashMap<>();
  private transient final AtomicLong projectedSkippedTicks = new AtomicLong(0L);
  private transient final AtomicLong tracked = new AtomicLong(0L);
  private transient final AtomicLong bypassed = new AtomicLong(0L);
  private transient final AtomicLong invalidated = new AtomicLong(0L);
  private transient final AtomicLong skippedFallingTicks = new AtomicLong(0L);
  private transient final PressureGate gate = new PressureGate();
  private transient final List<Subscription> subscriptions = new ArrayList<>();
  private transient volatile BlockHost blocks;
  private transient volatile WorldHost worlds;
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
    ReactPlatform platform = ReactPlatforms.get();
    worlds = platform.worldHost().orElse(null);
    blocks = platform.blockHost().orElse(null);
    BlockHost host = blocks;
    if (host != null) {
      subscriptions.add(host.onFallingBlockChange(ListenerPriority.MONITOR, true, this::onFallingBlockChange));
      subscriptions.add(host.onPlace(ListenerPriority.MONITOR, true, this::invalidateColumn));
      subscriptions.add(host.onBreak(ListenerPriority.MONITOR, true, this::invalidateColumn));
      subscriptions.add(host.onPhysics(ListenerPriority.MONITOR, true, this::invalidateColumn));
    }
    installBridgeHook();
  }

  @Override
  public void onDeactivate() {
    for (Subscription subscription : subscriptions) {
      subscription.cancel();
    }
    subscriptions.clear();
    tasks.clear();
    columnIndex.clear();
    tracked.set(0L);
    uninstallBridgeHook();
  }

  public boolean isMeasurementOnly() {
    return !bridgeActive;
  }

  public long readAndResetSkippedFallingTicks() {
    return skippedFallingTicks.getAndSet(0L);
  }

  private void installBridgeHook() {
    bridgeActive = false;
    BlockHost host = blocks;
    if (host == null) {
      return;
    }
    BlockHost.FallingTickHook hook = (GameEntity falling) -> {
      if (!gate.isEngaged()) {
        return false;
      }
      GameLocation at = falling.location();
      if (at == null) {
        return false;
      }
      if (nearPlayer(at)) {
        return false;
      }
      FallingTask task = tasks.get(falling.id());
      if (task == null) {
        return false;
      }
      if (System.currentTimeMillis() >= task.expiresAtMs - 200L) {
        return false;
      }
      skippedFallingTicks.incrementAndGet();
      return true;
    };
    host.installFallingTickHook(hook, () -> bridgeActive = true);
  }

  private void uninstallBridgeHook() {
    BlockHost host = blocks;
    if (host != null) {
      host.uninstallFallingTickHook();
    }
    bridgeActive = false;
  }

  private boolean nearPlayer(GameLocation location) {
    WorldHost presence = worlds;
    return presence != null && presence.anyPlayerNear(location, bypassRadius);
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

  void onFallingBlockChange(BlockHost.FallingBlockEvent event) {
    if (tasks.size() >= Math.max(64, maxTrackedTasks)) {
      return;
    }

    GameEntity falling = event.entity();
    GameLocation spawn = falling.location();
    if (spawn == null || spawn.worldId() == null) {
      return;
    }

    if (nearPlayer(spawn)) {
      bypassed.incrementAndGet();
      return;
    }

    int landingY = scanLandingY(spawn);
    if (landingY < 0) {
      return;
    }

    double fallDistance = Math.max(0D, spawn.y() - (landingY + 1));
    if (fallDistance < 1D) {
      return;
    }

    double initialDownwardVelocity = -event.velocityY();
    int landingTick = computeLandingTick(fallDistance, initialDownwardVelocity);
    if (landingTick <= 1) {
      return;
    }

    UUID worldId = spawn.worldId();
    UUID entityId = falling.id();
    long columnKey = packColumn(spawn.blockX(), spawn.blockZ());
    long now = System.currentTimeMillis();
    long expectedExpiry = now + Math.max(50L, landingTick * 50L) + 300L;

    FallingTask task = new FallingTask(worldId, columnKey, expectedExpiry);
    if (tasks.put(entityId, task) == null) {
      tracked.incrementAndGet();
    }
    columnIndex.computeIfAbsent(worldId, ignored -> new LazyGravityColumns()).put(columnKey, entityId);
    projectedSkippedTicks.addAndGet(landingTick - 1);
  }

  void invalidateColumn(BlockHost.BlockEvent event) {
    if (tracked.get() == 0L || event == null) {
      return;
    }

    GameLocation block = event.location();
    if (block == null || block.worldId() == null) {
      return;
    }

    LazyGravityColumns columns = columnIndex.get(block.worldId());
    if (columns == null) {
      return;
    }

    UUID entityId = columns.remove(packColumn(block.blockX(), block.blockZ()));
    if (entityId == null) {
      return;
    }

    if (tasks.remove(entityId) != null) {
      tracked.decrementAndGet();
      invalidated.incrementAndGet();
    }
  }

  private int scanLandingY(GameLocation spawn) {
    BlockHost host = blocks;
    if (host == null) {
      return -1;
    }
    UUID worldId = spawn.worldId();
    int x = spawn.blockX();
    int z = spawn.blockZ();
    int startY = spawn.blockY() - 1;
    int min = host.minHeight(worldId);
    int scanned = 0;

    for (int y = startY; y >= min && scanned < MAX_SCAN_DEPTH; y--) {
      scanned++;
      if (!host.isChunkLoaded(worldId, x >> 4, z >> 4)) {
        return -1;
      }

      String type = host.blockType(worldId, x, y, z);
      if ("AIR".equals(type) || "CAVE_AIR".equals(type) || "VOID_AIR".equals(type)) {
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

      if (!tasks.remove(entry.getKey(), task)) {
        continue;
      }
      tracked.decrementAndGet();
      LazyGravityColumns columns = columnIndex.get(task.worldId);
      if (columns != null) {
        columns.removeOwned(task.columnKey, entry.getKey());
      }
      reaped++;
    }

    for (LazyGravityColumns columns : columnIndex.values()) {
      columns.retainTracked(tasks);
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
