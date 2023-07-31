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

package com.volmit.react.content.tweak;

import com.volmit.react.api.tweak.ReactTweak;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.HashMap;
import java.util.Map;

public class TweakEntityHardstop extends ReactTweak implements Listener {
    public static final String ID = "entity-hardstop";

    private int maxEntitiesPerChunk = 100;
    private boolean allowItemDrops = true; // set to false to deny item drops
    private int cacheIntervalTicks = 10 * 20; // cache for 10 seconds (20 ticks per second)
    private transient Map<Chunk, Long> chunks = new HashMap<>();

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
        // check if its a dropped item and if item drops are allowed
        if (entity instanceof org.bukkit.entity.Item && allowItemDrops) {
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
        Long lastChecked = chunks.get(chunk);
        if (lastChecked != null && currentTime - lastChecked <= cacheIntervalTicks) {
            return false;
        }
        Entity[] entitiesInChunk = chunk.getEntities();
        int entityCount = 0;
        for (Entity entity : entitiesInChunk) {
            if (!(entity instanceof org.bukkit.entity.Item) || !allowItemDrops) {
                entityCount++;
            }
        }
        if (entityCount >= maxEntitiesPerChunk) {
            chunks.put(chunk, currentTime);
            return false;
        }
        return true;
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
