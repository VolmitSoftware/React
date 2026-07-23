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

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class EntityCensusTracker {
  private static final Object LOCK = new Object();
  private static final long REFRESH_INTERVAL_MS = 2000L;
  private static final AtomicLong lastRefreshMS = new AtomicLong(0L);
  private static int references = 0;
  private static volatile int groundItems = 0;
  private static volatile int hostile = 0;
  private static volatile int animals = 0;
  private static volatile int villagers = 0;
  private static volatile int projectiles = 0;
  private static volatile int physics = 0;

  private EntityCensusTracker() {
  }

  static void acquire() {
    synchronized (LOCK) {
      references++;
    }
  }

  static void release() {
    synchronized (LOCK) {
      references = Math.max(0, references - 1);
      if (references > 0) {
        return;
      }

      lastRefreshMS.set(0L);
      groundItems = 0;
      hostile = 0;
      animals = 0;
      villagers = 0;
      projectiles = 0;
      physics = 0;
    }
  }

  static int groundItems() {
    return groundItems;
  }

  static int hostile() {
    return hostile;
  }

  static int animals() {
    return animals;
  }

  static int villagers() {
    return villagers;
  }

  static int projectiles() {
    return projectiles;
  }

  static int physics() {
    return physics;
  }

  static void refreshMainThread() {
    if (!claimWindow()) {
      return;
    }

    int ground = 0;
    int enemy = 0;
    int passive = 0;
    int trader = 0;
    int flying = 0;
    int falling = 0;

    for (World world : Bukkit.getWorlds()) {
      for (Entity entity : WorldEntitySnapshots.get(world)) {
        if (entity instanceof Item) {
          ground++;
        }
        if (entity instanceof Enemy) {
          enemy++;
        }
        if (entity instanceof Animals) {
          passive++;
        }
        if (entity instanceof AbstractVillager) {
          trader++;
        }
        if (entity instanceof Projectile) {
          flying++;
        }
        if (entity instanceof TNTPrimed || entity instanceof FallingBlock) {
          falling++;
        }
      }
    }

    store(ground, enemy, passive, trader, flying, falling);
  }

  static void refreshFolia() {
    if (!claimWindow()) {
      return;
    }

    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    if (players.isEmpty()) {
      store(0, 0, 0, 0, 0, 0);
      return;
    }

    AtomicInteger ground = new AtomicInteger();
    AtomicInteger enemy = new AtomicInteger();
    AtomicInteger passive = new AtomicInteger();
    AtomicInteger trader = new AtomicInteger();
    AtomicInteger flying = new AtomicInteger();
    AtomicInteger falling = new AtomicInteger();
    Set<UUID> seen = ConcurrentHashMap.newKeySet();
    CountDownLatch latch = new CountDownLatch(players.size());

    for (Player player : players) {
      boolean scheduled = J.runEntity(player, () -> {
        try {
          if (player == null || !player.isOnline() || !J.isOwnedByCurrentRegion(player)) {
            return;
          }

          for (Entity entity : player.getNearbyEntities(80, 48, 80)) {
            if (!seen.add(entity.getUniqueId())) {
              continue;
            }

            if (entity instanceof Item) {
              ground.incrementAndGet();
            }
            if (entity instanceof Enemy) {
              enemy.incrementAndGet();
            }
            if (entity instanceof Animals) {
              passive.incrementAndGet();
            }
            if (entity instanceof AbstractVillager) {
              trader.incrementAndGet();
            }
            if (entity instanceof Projectile) {
              flying.incrementAndGet();
            }
            if (entity instanceof TNTPrimed || entity instanceof FallingBlock) {
              falling.incrementAndGet();
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
      React.verbose("EntityCensusTracker wait interrupted while gathering Folia approximation.");
    }

    store(ground.get(), enemy.get(), passive.get(), trader.get(), flying.get(), falling.get());
  }

  private static boolean claimWindow() {
    long now = System.currentTimeMillis();
    long last = lastRefreshMS.get();
    if (now - last < REFRESH_INTERVAL_MS) {
      return false;
    }

    return lastRefreshMS.compareAndSet(last, now);
  }

  private static void store(int ground, int enemy, int passive, int trader, int flying, int falling) {
    groundItems = ground;
    hostile = enemy;
    animals = passive;
    villagers = trader;
    projectiles = flying;
    physics = falling;
  }
}
