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

import art.arcane.react.api.tweak.ReactTweak;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.Iterator;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Entity Hardstop tweak. Hard-caps per-chunk entity population by cancelling new additions once limits are exceeded.")
public class TweakEntityHardstop extends ReactTweak implements Listener {
  public static final String ID = "entity-hardstop";

  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entities allowed per chunk in entity hardstop.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxEntitiesPerChunk = 100;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Allows natural and player-dropped item entities to bypass the hardstop cap checks.", impact = "Enable to keep dropped items flowing even in crowded chunks; disable for stricter hard-capping.")
  private boolean allowItemDrops = true; // set to false to deny item drops
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cache duration for chunks recently rejected by hardstop before re-checking entity counts (ticks).", impact = "Higher values reduce repeated counting overhead but can deny spawns longer; lower values re-check sooner with more overhead.")
  private int cacheIntervalTicks = 10 * 20; // cache for 10 seconds (20 ticks per second)
  private transient final Long2LongOpenHashMap chunks = new Long2LongOpenHashMap();

  public TweakEntityHardstop() {
    super(ID);
  }

  @Override
  public void onActivate() {
  }

  @EventHandler
  public void onEntitySpawn(EntitySpawnEvent event) {
    Entity entity = event.getEntity();
    Chunk chunk = entity.getLocation().getChunk();
    if (entity instanceof Item && allowItemDrops) {
      return;
    }
    if (!canSpawnEntity(chunk)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    Chunk chunk = event.getLocation().getChunk();
    if (!canSpawnEntity(chunk)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onPlayerDropItem(PlayerDropItemEvent event) {
    Chunk chunk = event.getPlayer().getLocation().getChunk();
    if (!allowItemDrops && !canSpawnEntity(chunk)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onEntityBreed(EntityBreedEvent event) {
    Chunk chunk = event.getEntity().getLocation().getChunk();
    if (!canSpawnEntity(chunk)) {
      event.setCancelled(true);
    }
  }


  private boolean canSpawnEntity(Chunk chunk) {
    long currentTime = System.currentTimeMillis();
    long cacheWindowMs = (long) cacheIntervalTicks * 50L;
    long key = chunkKey(chunk.getX(), chunk.getZ());
    synchronized (chunks) {
      evictExpired(currentTime, cacheWindowMs);
      if (chunks.containsKey(key) && currentTime - chunks.get(key) <= cacheWindowMs) {
        return false;
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
      synchronized (chunks) {
        chunks.put(key, currentTime);
      }
      return false;
    }
    return true;
  }

  private void evictExpired(long currentTime, long cacheWindowMs) {
    Iterator<Long2LongMap.Entry> iterator = chunks.long2LongEntrySet().fastIterator();
    while (iterator.hasNext()) {
      Long2LongMap.Entry entry = iterator.next();
      if (currentTime - entry.getLongValue() > cacheWindowMs) {
        iterator.remove();
      }
    }
  }

  private static long chunkKey(int cx, int cz) {
    return (long) cx << 32 | (cz & 0xffffffffL);
  }


  @Override
  public void onDeactivate() {
  }

  @Override
  public int getTickInterval() {
    return -1;
  }

  @Override
  public void onTick() {
  }
}
