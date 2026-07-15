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

package art.arcane.react.util.project.world;

import art.arcane.chrono.ChronoLatch;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldEntitySnapshots {
  public static final long DEFAULT_TTL_MS = 250L;
  private static final Map<UUID, Snapshot> CACHE = new ConcurrentHashMap<>();
  private static final ChronoLatch PRUNE_LATCH = new ChronoLatch(30_000L);
  private static volatile boolean paperEntityCountAvailable = true;
  private static volatile boolean paperChunkCountAvailable = true;

  private WorldEntitySnapshots() {
  }

  public static List<Entity> get(World world) {
    return get(world, DEFAULT_TTL_MS);
  }

  public static List<Entity> get(World world, long maxAgeMs) {
    if (world == null) {
      return List.of();
    }

    long now = System.currentTimeMillis();
    UUID worldId = world.getUID();
    Snapshot snapshot = CACHE.get(worldId);
    if (snapshot != null && now - snapshot.at <= maxAgeMs) {
      return snapshot.entities;
    }

    List<Entity> fresh = world.getEntities();
    CACHE.put(worldId, new Snapshot(now, fresh));
    prune();
    return fresh;
  }

  public static int count(World world) {
    if (world == null) {
      return 0;
    }

    if (paperEntityCountAvailable) {
      try {
        return world.getEntityCount();
      } catch (Throwable ignored) {
        paperEntityCountAvailable = false;
      }
    }

    return get(world).size();
  }

  public static int chunkCount(World world) {
    if (world == null) {
      return 0;
    }

    if (paperChunkCountAvailable) {
      try {
        return world.getChunkCount();
      } catch (Throwable ignored) {
        paperChunkCountAvailable = false;
      }
    }

    try {
      return world.getLoadedChunks().length;
    } catch (Throwable ignored) {
      return 0;
    }
  }

  public static void invalidate() {
    CACHE.clear();
  }

  private static void prune() {
    if (!PRUNE_LATCH.flip()) {
      return;
    }

    List<World> worlds = Bukkit.getWorlds();
    if (CACHE.size() <= worlds.size()) {
      return;
    }

    Set<UUID> live = new HashSet<>(worlds.size());
    for (World world : worlds) {
      live.add(world.getUID());
    }

    CACHE.keySet().retainAll(live);
  }

  private record Snapshot(long at, List<Entity> entities) {
  }
}
