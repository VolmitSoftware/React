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

package art.arcane.react.content.tweak;

import art.arcane.react.api.protect.internal.ProtectionGuards;
import art.arcane.react.api.tweak.ReactTweak;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Entity Hardstop tweak. Hard-caps per-chunk entity population by cancelling new additions once limits are exceeded.")
public class TweakEntityHardstop extends ReactTweak implements Listener {
  public static final String ID = "entity-hardstop";
  private static final int MAX_CACHED_REJECTIONS = 65536;
  private static final int MAX_CACHE_MAINTENANCE_PER_CHECK = 8;

  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entities allowed per chunk in entity hardstop.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxEntitiesPerChunk = 100;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Allows natural and player-dropped item entities to bypass the hardstop cap checks.", impact = "Enable to keep dropped items flowing even in crowded chunks; disable for stricter hard-capping.")
  private boolean allowItemDrops = true; // set to false to deny item drops
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cache duration for chunks recently rejected by hardstop before re-checking entity counts (ticks).", impact = "Higher values reduce repeated counting overhead but can deny spawns longer; lower values re-check sooner with more overhead.")
  private int cacheIntervalTicks = 10 * 20; // cache for 10 seconds (20 ticks per second)
  private transient final Map<ChunkKey, Long> rejectedUntil = new HashMap<>();
  private transient final Queue<Rejection> rejectionOrder = new ArrayDeque<>();

  public TweakEntityHardstop() {
    super(ID);
  }

  @EventHandler
  public void onEntitySpawn(EntitySpawnEvent event) {
    if (event instanceof CreatureSpawnEvent) {
      return;
    }
    Entity entity = event.getEntity();
    Location at = entity.getLocation();
    if (entity instanceof Item && allowItemDrops) {
      return;
    }
    if (spawnProtected(entity.getType(), at)) {
      return;
    }
    if (!canSpawnEntity(at.getChunk())) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    Location at = event.getLocation();
    if (spawnProtected(event.getEntityType(), at, event.getSpawnReason())) {
      return;
    }
    if (!canSpawnEntity(at.getChunk())) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onPlayerDropItem(PlayerDropItemEvent event) {
    Location at = event.getPlayer().getLocation();
    if (allowItemDrops) {
      return;
    }
    if (spawnProtected(EntityType.ITEM, at)) {
      return;
    }
    if (!canSpawnEntity(at.getChunk())) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onEntityBreed(EntityBreedEvent event) {
    Location at = event.getEntity().getLocation();
    if (spawnProtected(event.getEntity().getType(), at)) {
      return;
    }
    if (!canSpawnEntity(at.getChunk())) {
      event.setCancelled(true);
    }
  }

  @Override
  public void onDeactivate() {
    synchronized (rejectedUntil) {
      rejectedUntil.clear();
      rejectionOrder.clear();
    }
  }

  private boolean spawnProtected(EntityType type, Location at) {
    return spawnProtected(type, at, null);
  }

  private boolean spawnProtected(EntityType type, Location at, CreatureSpawnEvent.SpawnReason reason) {
    World world = at.getWorld();
    return ProtectionGuards.spawnProtected(type, world == null ? "" : world.getName(), reason);
  }


  private boolean canSpawnEntity(Chunk chunk) {
    long currentTime = System.currentTimeMillis();
    long cacheWindowMs = Math.max(0L, (long) cacheIntervalTicks * 50L);
    ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunkKey(chunk.getX(), chunk.getZ()));
    synchronized (rejectedUntil) {
      maintainCache(currentTime);
      Long deadline = rejectedUntil.get(key);
      if (deadline != null && deadline > currentTime) {
        return false;
      }
      if (deadline != null) {
        rejectedUntil.remove(key, deadline);
      }
    }
    Entity[] entitiesInChunk = chunk.getEntities();
    int entityCount = 0;
    for (Entity entity : entitiesInChunk) {
      if (!(entity instanceof Item) || !allowItemDrops) {
        entityCount++;
      }
    }
    if (entityCount >= maxEntitiesPerChunk) {
      if (cacheWindowMs > 0L) {
        cacheRejection(key, saturatingAdd(currentTime, cacheWindowMs));
      }
      return false;
    }
    return true;
  }

  private void cacheRejection(ChunkKey key, long deadline) {
    synchronized (rejectedUntil) {
      if (!rejectedUntil.containsKey(key)) {
        while (rejectedUntil.size() >= MAX_CACHED_REJECTIONS) {
          if (!evictOldest()) {
            rejectedUntil.clear();
            rejectionOrder.clear();
            break;
          }
        }
      }
      rejectedUntil.put(key, deadline);
      rejectionOrder.offer(new Rejection(key, deadline));
    }
  }

  private void maintainCache(long currentTime) {
    int checked = 0;
    while (checked++ < MAX_CACHE_MAINTENANCE_PER_CHECK) {
      Rejection rejection = rejectionOrder.poll();
      if (rejection == null) {
        return;
      }
      Long currentDeadline = rejectedUntil.get(rejection.key());
      if (currentDeadline == null || currentDeadline.longValue() != rejection.deadline()) {
        continue;
      }
      if (currentDeadline <= currentTime) {
        rejectedUntil.remove(rejection.key(), currentDeadline);
      } else {
        rejectionOrder.offer(rejection);
      }
    }
  }

  private boolean evictOldest() {
    while (true) {
      Rejection rejection = rejectionOrder.poll();
      if (rejection == null) {
        return false;
      }
      Long currentDeadline = rejectedUntil.get(rejection.key());
      if (currentDeadline != null
          && currentDeadline.longValue() == rejection.deadline()
          && rejectedUntil.remove(rejection.key(), currentDeadline)) {
        return true;
      }
    }
  }

  private static long saturatingAdd(long left, long right) {
    return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
  }

  private static long chunkKey(int cx, int cz) {
    return (long) cx << 32 | (cz & 0xffffffffL);
  }

  private record ChunkKey(UUID worldId, long coordinate) {
  }

  private record Rejection(ChunkKey key, long deadline) {
  }

}
