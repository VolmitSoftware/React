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
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SamplerChunks extends ReactCachedSampler implements Listener {
  public static final String ID = "chunks";
  private transient final AtomicInteger loadedChunks;
  private transient ChronoLatch realCheckUpdate;
  private int realityCheckMS = 10000;

  public SamplerChunks() {
    super(ID, 50);
    loadedChunks = new AtomicInteger(0);
    realCheckUpdate = new ChronoLatch(realityCheckMS);
  }

  @Override
  public Material getIcon() {
    return Material.CHEST_MINECART;
  }

  public int getRealCheck() {
    if (J.isFoliaThreading()) {
      return getFoliaApproximateRealCheck();
    }

    return executeSync(() -> {
      int m = 0;

      for (World i : Bukkit.getWorlds()) {
        m += i.getLoadedChunks().length;
      }

      return m;
    });
  }

  private int getFoliaApproximateRealCheck() {
    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    if (players.isEmpty()) {
      return Math.max(0, loadedChunks.get());
    }

    Set<String> chunks = ConcurrentHashMap.newKeySet();
    CountDownLatch latch = new CountDownLatch(players.size());
    int radius = 6;

    for (Player player : players) {
      boolean scheduled = J.runEntity(player, () -> {
        try {
          if (player == null || !player.isOnline() || !J.isOwnedByCurrentRegion(player)) {
            return;
          }

          int chunkX = player.getLocation().getBlockX() >> 4;
          int chunkZ = player.getLocation().getBlockZ() >> 4;
          String world = player.getWorld().getUID().toString();
          for (int x = chunkX - radius; x <= chunkX + radius; x++) {
            for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
              chunks.add(world + ":" + x + ":" + z);
            }
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
      latch.await(200, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      React.verbose("SamplerChunks wait interrupted while gathering Folia approximation.");
    }

    return chunks.size();
  }

  @Override
  public void start() {
    super.start();
    realCheckUpdate = new ChronoLatch(realityCheckMS);
  }

  @EventHandler
  public void on(ChunkLoadEvent e) {
    loadedChunks.incrementAndGet();
  }

  @EventHandler
  public void on(WorldUnloadEvent e) {
    loadedChunks.set(Math.max(0, loadedChunks.get()));
  }

  @EventHandler
  public void on(ChunkUnloadEvent e) {
    loadedChunks.decrementAndGet();
  }

  @Override
  public double onSample() {
    if (realCheckUpdate.flip() || loadedChunks.get() < 0) {
      if (J.isFoliaThreading()) {
        J.a(() -> loadedChunks.set(getFoliaApproximateRealCheck()));
      } else {
        sampleOnMainThread(() -> {
          int m = 0;

          for (World i : Bukkit.getWorlds()) {
            m += i.getLoadedChunks().length;
          }

          loadedChunks.set(m);
          return (double) m;
        });
      }
    }

    return loadedChunks.get();
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return "CHK";
  }
}
