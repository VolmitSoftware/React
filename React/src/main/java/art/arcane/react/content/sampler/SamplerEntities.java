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

package art.arcane.react.content.sampler;

import art.arcane.chrono.ChronoLatch;
import art.arcane.react.React;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.util.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SamplerEntities extends ReactCachedSampler implements Listener {
  public static final String ID = "entities";
  @Getter
  private transient final AtomicInteger entities;
  private transient ChronoLatch realEntityUpdate;
  private int realityCheckMS = 10000;

  public SamplerEntities() {
    super(ID, 50);
    entities = new AtomicInteger(0);
    realEntityUpdate = new ChronoLatch(realityCheckMS);
  }

  @Override
  public Material getIcon() {
    return Material.CHICKEN_SPAWN_EGG;
  }

  public int getRealCheck() {
    if (J.isFoliaThreading()) {
      return getFoliaApproximateRealCheck();
    }

    return executeSync(() -> {
      int m = 0;
      for (World i : Bukkit.getWorlds()) {
        for (Chunk j : i.getLoadedChunks()) {
          m += j.getEntities().length;
          getChunkCounter(j).set(j.getEntities().length);
        }
      }

      return m;
    });
  }

  private int getFoliaApproximateRealCheck() {
    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    if (players.isEmpty()) {
      return Math.max(0, entities.get());
    }

    Set<UUID> seen = ConcurrentHashMap.newKeySet();
    CountDownLatch latch = new CountDownLatch(players.size());
    for (Player player : players) {
      boolean scheduled = J.runEntity(player, () -> {
        try {
          if (player == null || !player.isOnline() || !J.isOwnedByCurrentRegion(player)) {
            return;
          }

          for (org.bukkit.entity.Entity entity : player.getNearbyEntities(96, 64, 96)) {
            seen.add(entity.getUniqueId());
          }
        } finally {
          latch.countDown();
        }
      });

      if (!scheduled) {
        latch.countDown();
      }
    }

    try {
      latch.await(250, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      React.verbose("SamplerEntities wait interrupted while gathering Folia approximation.");
    }

    return seen.size();
  }

  @Override
  public void start() {
    super.start();
    realEntityUpdate = new ChronoLatch(realityCheckMS);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntitySpawnEvent e) {
    entities.incrementAndGet();
    getChunkCounter(e.getEntity().getLocation().getChunk()).addAndGet(1D);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ChunkLoadEvent e) {
    entities.addAndGet(e.getChunk().getEntities().length);
    getChunkCounter(e.getChunk()).addAndGet(e.getChunk().getEntities().length);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ItemMergeEvent e) {
    entities.addAndGet(-1);
    getChunkCounter(e.getEntity().getLocation().getChunk()).addAndGet(-1D);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkUnloadEvent e) {
    entities.addAndGet(-e.getChunk().getEntities().length);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(WorldUnloadEvent e) {
    entities.set(Math.max(0, entities.get()));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    entities.decrementAndGet();
    getChunkCounter(e.getEntity().getLocation().getChunk()).addAndGet(-1D);
  }

  @Override
  public double onSample() {
    if (realEntityUpdate.flip() || entities.get() < 0) {
      J.a(() -> entities.set(getRealCheck()));
    }

    return entities.get();
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return "ENT";
  }
}
