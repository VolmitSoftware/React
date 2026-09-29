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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Entity Hardstop tweak. Hard-caps per-chunk entity population by cancelling new additions once limits are exceeded.")
public class TweakEntityHardstop extends ReactTweak implements Listener {
  public static final String ID = "entity-hardstop";
  private static final long COUNT_CACHE_MS = 1000L;

  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entities allowed per chunk in entity hardstop.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxEntitiesPerChunk = 100;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Allows natural and player-dropped item entities to bypass the hardstop cap checks.", impact = "Enable to keep dropped items flowing even in crowded chunks; disable for stricter hard-capping.")
  private boolean allowItemDrops = true; // set to false to deny item drops
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cache duration for chunks recently rejected by hardstop before re-checking entity counts (ticks).", impact = "Higher values reduce repeated counting overhead but can deny spawns longer; lower values re-check sooner with more overhead.")
  private int cacheIntervalTicks = 10 * 20; // cache for 10 seconds (20 ticks per second)
  private transient final Map<ChunkKey, ChunkBudget> chunkBudgets = new ConcurrentHashMap<>();
  private transient final AtomicLong nextSweepMs = new AtomicLong(0L);

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
    if (!canSpawnEntity(at)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    Location at = event.getLocation();
    if (spawnProtected(event.getEntityType(), at, event.getSpawnReason())) {
      return;
    }
    if (!canSpawnEntity(at)) {
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
    if (!canSpawnEntity(at)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onEntityBreed(EntityBreedEvent event) {
    Location at = event.getEntity().getLocation();
    if (spawnProtected(event.getEntity().getType(), at)) {
      return;
    }
    if (!canSpawnEntity(at)) {
      event.setCancelled(true);
    }
  }

  @Override
  public void onDeactivate() {
    chunkBudgets.clear();
    nextSweepMs.set(0L);
  }

  private boolean spawnProtected(EntityType type, Location at) {
    return spawnProtected(type, at, null);
  }

  private boolean spawnProtected(EntityType type, Location at, CreatureSpawnEvent.SpawnReason reason) {
    World world = at.getWorld();
    return ProtectionGuards.spawnProtected(type, world == null ? "" : world.getName(), reason);
  }


  private boolean canSpawnEntity(Location at) {
    World world = at.getWorld();
    if (world == null) {
      return true;
    }

    int chunkX = at.getBlockX() >> 4;
    int chunkZ = at.getBlockZ() >> 4;
    long currentTime = System.currentTimeMillis();
    sweepStaleBudgets(currentTime);
    ChunkKey key = new ChunkKey(world.getUID(), chunkKey(chunkX, chunkZ));
    ChunkBudget budget = chunkBudgets.get(key);
    if (budget == null || budget.isStale(currentTime)) {
      if (!world.isChunkLoaded(chunkX, chunkZ)) {
        return true;
      }
      budget = new ChunkBudget(
          countEntities(world.getChunkAt(chunkX, chunkZ)),
          saturatingAdd(currentTime, COUNT_CACHE_MS)
      );
      chunkBudgets.put(key, budget);
    }
    return budget.admit(currentTime, maxEntitiesPerChunk, Math.max(0L, (long) cacheIntervalTicks * 50L));
  }

  private int countEntities(Chunk chunk) {
    int entityCount = 0;
    for (Entity entity : chunk.getEntities()) {
      if (!(entity instanceof Item) || !allowItemDrops) {
        entityCount++;
      }
    }
    return entityCount;
  }

  private void sweepStaleBudgets(long currentTime) {
    long due = nextSweepMs.get();
    if (currentTime < due || !nextSweepMs.compareAndSet(due, saturatingAdd(currentTime, COUNT_CACHE_MS))) {
      return;
    }
    chunkBudgets.values().removeIf(budget -> budget.isStale(currentTime));
  }

  private static long saturatingAdd(long left, long right) {
    return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
  }

  private static long chunkKey(int cx, int cz) {
    return (long) cx << 32 | (cz & 0xffffffffL);
  }

  private record ChunkKey(UUID worldId, long coordinate) {
  }

  private static final class ChunkBudget {
    private final long countExpiresAt;
    private int count;
    private long rejectedUntil;

    private ChunkBudget(int count, long countExpiresAt) {
      this.count = count;
      this.countExpiresAt = countExpiresAt;
    }

    private synchronized boolean isStale(long currentTime) {
      return currentTime >= countExpiresAt && currentTime >= rejectedUntil;
    }

    private synchronized boolean admit(long currentTime, int maximum, long rejectionWindowMs) {
      if (rejectedUntil > currentTime) {
        return false;
      }
      if (count >= maximum) {
        rejectedUntil = saturatingAdd(currentTime, rejectionWindowMs);
        return false;
      }
      count++;
      return true;
    }
  }

}
