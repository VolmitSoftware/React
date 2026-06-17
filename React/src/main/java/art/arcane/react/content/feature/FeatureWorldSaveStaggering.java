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
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Configuration for World Save Staggering feature. Spreads vanilla auto-save calls across multiple worlds so two regions never serialize on the same tick.")
public class FeatureWorldSaveStaggering extends ReactFeature implements Listener {
  public static final String ID = "world-save-staggering";

  @ConfigDoc(value = "Main evaluation interval for world save staggering in milliseconds.", impact = "Lower values reschedule slot ownership faster on world add/remove; higher values reduce overhead.")
  private int tickIntervalMS = 5000;
  @ConfigDoc(value = "Full auto-save period in ticks; matches the vanilla 5-minute cadence.", impact = "Higher values save less often (less disk IO, larger crash loss); lower values save more often.")
  private int autoSavePeriodTicks = 6000;
  @ConfigDoc(value = "Controls whether world save staggering actively dispatches staggered saves.", impact = "Disable to keep this feature in measurement-only mode and only sample save spike alignment.")
  private boolean active = true;
  @ConfigDoc(value = "Suppresses vanilla auto-save while React drives the staggered cadence.", impact = "Disable to leave vanilla auto-save on; the measurement-only path still reports tick alignment.")
  private boolean suppressVanillaAutoSave = true;

  private transient final Map<UUID, Long> originalAutoSaveByWorld = new ConcurrentHashMap<>();
  private transient final Map<UUID, AtomicLong> lastSaveStartNanosByWorld = new ConcurrentHashMap<>();
  private transient final Map<UUID, AtomicLong> lastSaveDurationMsByWorld = new ConcurrentHashMap<>();
  private transient volatile boolean deactivating;
  private transient int taskId;
  private transient int tickCounter;

  public FeatureWorldSaveStaggering() {
    super(ID);
  }

  @Override
  public void onActivate() {
    deactivating = false;
    lastSaveStartNanosByWorld.clear();
    lastSaveDurationMsByWorld.clear();
    tickCounter = 0;

    if (suppressVanillaAutoSave && active) {
      J.s(this::suppressVanillaAutoSaveOnAllWorlds);
    }

    if (active) {
      taskId = J.sr(this::tickStaggerScheduler, 1);
    }
  }

  @Override
  public void onDeactivate() {
    deactivating = true;

    if (taskId != 0) {
      J.csr(taskId);
      taskId = 0;
    }

    restoreVanillaAutoSaveOnAllWorlds();
    originalAutoSaveByWorld.clear();
    lastSaveStartNanosByWorld.clear();
    lastSaveDurationMsByWorld.clear();
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    if (suppressVanillaAutoSave && active && !deactivating) {
      J.s(this::suppressVanillaAutoSaveOnAllWorlds);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(WorldSaveEvent event) {
    World world = event.getWorld();
    if (world == null) {
      return;
    }

    UUID worldId = world.getUID();
    AtomicLong start = lastSaveStartNanosByWorld.get(worldId);
    long now = System.nanoTime();
    if (start != null) {
      long prev = start.getAndSet(now);
      if (prev > 0L) {
        long durationMs = Math.max(0L, (now - prev) / 1_000_000L);
        lastSaveDurationMsByWorld
            .computeIfAbsent(worldId, ignored -> new AtomicLong(0L))
            .set(durationMs);
      } else {
        start.set(now);
      }
    } else {
      lastSaveStartNanosByWorld.put(worldId, new AtomicLong(now));
    }
  }

  public long lastSaveIntervalMs(UUID worldId) {
    AtomicLong duration = lastSaveDurationMsByWorld.get(worldId);
    return duration == null ? 0L : duration.get();
  }

  private void tickStaggerScheduler() {
    int period = Math.max(20, autoSavePeriodTicks);
    List<World> worlds = Bukkit.getWorlds();
    int worldCount = worlds.size();
    if (worldCount == 0) {
      return;
    }

    int slotWidth = Math.max(1, period / worldCount);
    int globalTick = tickCounter++;

    for (int i = 0; i < worldCount; i++) {
      World world = worlds.get(i);
      int phase = (i * slotWidth) % period;
      if (((globalTick - phase) % period) != 0) {
        continue;
      }

      saveWorld(world);
    }
  }

  private void saveWorld(World world) {
    if (world == null) {
      return;
    }

    UUID worldId = world.getUID();
    Location anchor = world.getSpawnLocation();
    J.s(anchor, () -> {
      long startNanos = System.nanoTime();
      lastSaveStartNanosByWorld
          .computeIfAbsent(worldId, ignored -> new AtomicLong(0L))
          .set(startNanos);
      try {
        world.save();
      } catch (Throwable ex) {
        React.warn("Staggered save failed for world " + world.getName() + ": " + ex.getClass().getSimpleName()
            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        React.reportError(ex);
        return;
      }

      long durationMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
      lastSaveDurationMsByWorld
          .computeIfAbsent(worldId, ignored -> new AtomicLong(0L))
          .set(durationMs);
    });
  }

  private void suppressVanillaAutoSaveOnAllWorlds() {
    if (deactivating) {
      return;
    }

    for (World world : Bukkit.getWorlds()) {
      UUID worldId = world.getUID();
      if (originalAutoSaveByWorld.containsKey(worldId)) {
        continue;
      }

      if (world.isAutoSave()) {
        originalAutoSaveByWorld.put(worldId, 1L);
        world.setAutoSave(false);
      } else {
        originalAutoSaveByWorld.put(worldId, 0L);
      }
    }
  }

  private void restoreVanillaAutoSaveOnAllWorlds() {
    for (World world : Bukkit.getWorlds()) {
      if (world == null) {
        continue;
      }

      Long original = originalAutoSaveByWorld.get(world.getUID());
      if (original != null && original == 0L) {
        world.setAutoSave(false);
        continue;
      }

      world.setAutoSave(true);
    }
  }
}
