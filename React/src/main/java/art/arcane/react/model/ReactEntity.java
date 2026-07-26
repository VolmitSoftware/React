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

package art.arcane.react.model;

import art.arcane.react.React;
import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ReactEntity {
  private static final long maxTickInterval = 10000;
  private static final long SCRATCH_RETENTION_MS = 900_000L;
  private static final NamespacedKey nsStackCount = new NamespacedKey(React.instance, "react-stack-count");
  private static final NamespacedKey nsPaused = new NamespacedKey(React.instance, "react-paused");
  private static final Map<UUID, Scratch> scratchByEntity = new ConcurrentHashMap<>();

  public static long getStaleness(Entity entity) {
    return System.currentTimeMillis() - getLastTick(entity);
  }

  public static boolean tick(Entity entity, EntityPriority p) {
    if (entity == null) {
      return false;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity)) {
      J.runEntity(entity, () -> tick(entity, p));
      return false;
    }

    if (entity.isDead()) {
      return false;
    }

    if (getStaleness(entity) > maxTickInterval) {
      p.updateCrowd(entity);
      p.updateDistanceToPlayer(entity);
      setPriority(entity, p.getPriorityWithCrowd(entity, getCrowding(entity)));
      setLastTick(entity, System.currentTimeMillis());

      if (isPaused(entity)) {
        setPaused(entity, false);
      }

      return true;
    }

    return false;
  }

  public static long getLastTick(Entity entity) {
    Scratch scratch = scratchByEntity.get(entity.getUniqueId());
    return scratch == null ? 0 : scratch.lastTick;
  }

  public static void setLastTick(Entity entity, long lastTick) {
    Scratch scratch = scratch(entity);
    scratch.lastTick = lastTick;
    scratch.touchedMs = System.currentTimeMillis();
  }

  public static double getPriority(Entity entity) {
    Scratch scratch = scratchByEntity.get(entity.getUniqueId());
    return scratch == null ? EntityPriority.BASELINE : scratch.priority;
  }

  public static void setPriority(Entity entity, double priority) {
    Scratch scratch = scratch(entity);
    scratch.priority = priority;
    scratch.touchedMs = System.currentTimeMillis();
  }

  public static int getStackCount(Entity entity) {
    Integer d = entity.getPersistentDataContainer().get(nsStackCount, PersistentDataType.INTEGER);
    return d == null ? 1 : d;
  }

  public static void setStackCount(Entity entity, int stackCount) {
    entity.getPersistentDataContainer().set(nsStackCount, PersistentDataType.INTEGER, stackCount);
  }

  public static double getCrowding(Entity entity) {
    Scratch scratch = scratchByEntity.get(entity.getUniqueId());
    return scratch == null ? 1 : scratch.crowding;
  }

  public static void setCrowding(Entity entity, double crowding) {
    Scratch scratch = scratch(entity);
    scratch.crowding = crowding;
    scratch.touchedMs = System.currentTimeMillis();
  }

  public static double getNearestPlayer(Entity entity) {
    Scratch scratch = scratchByEntity.get(entity.getUniqueId());
    return scratch == null ? 1 : scratch.nearestPlayer;
  }

  public static void setNearestPlayer(Entity entity, double d) {
    Scratch scratch = scratch(entity);
    scratch.nearestPlayer = d;
    scratch.touchedMs = System.currentTimeMillis();
  }

  public static boolean isPaused(Entity entity) {
    Byte d = entity.getPersistentDataContainer().get(nsPaused, PersistentDataType.BYTE);
    return d != null && d == 1;
  }

  public static void setPaused(Entity entity, boolean paused) {
    entity.getPersistentDataContainer().set(nsPaused, PersistentDataType.BYTE, (byte) (paused ? 1 : 0));

    if (paused && entity instanceof LivingEntity le) {
      le.setAI(false);
    } else if (!paused && entity instanceof LivingEntity le) {
      le.setAI(true);
    }
  }

  public static void sweepScratch() {
    if (scratchByEntity.isEmpty()) {
      return;
    }

    long cutoff = System.currentTimeMillis() - SCRATCH_RETENTION_MS;
    Iterator<Map.Entry<UUID, Scratch>> iterator = scratchByEntity.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<UUID, Scratch> entry = iterator.next();
      if (entry.getValue().touchedMs < cutoff) {
        iterator.remove();
      }
    }
  }

  public static void clearScratch() {
    scratchByEntity.clear();
  }

  private static Scratch scratch(Entity entity) {
    return scratchByEntity.computeIfAbsent(entity.getUniqueId(), ignored -> new Scratch());
  }

  private static final class Scratch {
    private volatile long lastTick;
    private volatile double priority = EntityPriority.BASELINE;
    private volatile double crowding = 1;
    private volatile double nearestPlayer = 1;
    private volatile long touchedMs = System.currentTimeMillis();
  }
}
