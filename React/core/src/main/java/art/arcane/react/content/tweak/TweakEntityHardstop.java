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
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.Subscription;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Entity Hardstop tweak. Hard-caps per-chunk entity population by cancelling new additions once limits are exceeded.")
public class TweakEntityHardstop extends ReactTweak {
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
  private transient volatile SpawnHost spawns;
  private transient List<Subscription> subscriptions = List.of();

  public TweakEntityHardstop() {
    super(ID);
  }

  @Override
  public void onActivate() {
    SpawnHost host = ReactPlatforms.get().spawnHost().orElse(null);
    spawns = host;
    if (host == null) {
      return;
    }
    subscriptions = List.of(
        host.onEntitySpawn(ListenerPriority.NORMAL, true, this::onEntitySpawn),
        host.onCreatureSpawn(ListenerPriority.NORMAL, true, this::onCreatureSpawn),
        host.onPlayerDropItem(ListenerPriority.NORMAL, true, this::onPlayerDropItem),
        host.onBreed(ListenerPriority.NORMAL, true, this::onEntityBreed)
    );
  }

  void onEntitySpawn(SpawnHost.EntitySpawn event) {
    GameLocation at = event.location();
    if (event.isItem() && allowItemDrops) {
      return;
    }
    if (spawnProtected(event.type(), at, null)) {
      return;
    }
    if (!canSpawnEntity(at, !event.precedesAdd())) {
      event.cancel();
    }
  }

  void onCreatureSpawn(SpawnHost.Spawn event) {
    GameLocation at = event.location();
    if (spawnProtected(event.type(), at, event.reason())) {
      return;
    }
    if (!canSpawnEntity(at, true)) {
      event.cancel();
    }
  }

  void onPlayerDropItem(SpawnHost.ItemDrop event) {
    GameLocation at = event.location();
    if (allowItemDrops) {
      return;
    }
    if (spawnProtected("ITEM", at, null)) {
      return;
    }
    if (!canSpawnEntity(at, false)) {
      event.cancel();
    }
  }

  void onEntityBreed(SpawnHost.Breed event) {
    GameLocation at = event.location();
    if (spawnProtected(event.type(), at, null)) {
      return;
    }
    if (!canSpawnEntity(at, false)) {
      event.cancel();
    }
  }

  @Override
  public void onDeactivate() {
    for (Subscription subscription : subscriptions) {
      subscription.cancel();
    }
    subscriptions = List.of();
    chunkBudgets.clear();
    nextSweepMs.set(0L);
  }

  private boolean spawnProtected(String type, GameLocation at, String reason) {
    SpawnHost host = host();
    return host != null && at != null && host.spawnProtected(type, at, reason);
  }

  private SpawnHost host() {
    SpawnHost host = spawns;
    if (host == null) {
      host = ReactPlatforms.get().spawnHost().orElse(null);
      spawns = host;
    }
    return host;
  }

  private boolean canSpawnEntity(GameLocation at, boolean reserve) {
    if (at == null || at.worldId() == null) {
      return true;
    }

    int chunkX = at.chunkX();
    int chunkZ = at.chunkZ();
    long currentTime = System.currentTimeMillis();
    long rejectionWindowMs = Math.max(0L, (long) cacheIntervalTicks * 50L);
    sweepStaleBudgets(currentTime);
    ChunkKey key = new ChunkKey(at.worldId(), chunkKey(chunkX, chunkZ));
    ChunkBudget budget = chunkBudgets.get(key);
    Admission admission = budget == null || budget.isStale(currentTime)
        ? Admission.RECOUNT
        : budget.admit(currentTime, maxEntitiesPerChunk, rejectionWindowMs, reserve);
    if (admission != Admission.RECOUNT) {
      return admission == Admission.ADMIT;
    }
    SpawnHost host = host();
    int count = host == null ? -1 : host.chunkEntityCount(at.worldId(), chunkX, chunkZ, !allowItemDrops);
    if (count < 0) {
      return true;
    }
    ChunkBudget counted = new ChunkBudget(count, saturatingAdd(currentTime, COUNT_CACHE_MS));
    chunkBudgets.put(key, counted);
    return counted.admit(currentTime, maxEntitiesPerChunk, rejectionWindowMs, reserve) == Admission.ADMIT;
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

  private enum Admission {
    ADMIT,
    REJECT,
    RECOUNT
  }

  private record ChunkKey(UUID worldId, long coordinate) {
  }

  private static final class ChunkBudget {
    private final long countExpiresAt;
    private int count;
    private boolean exact = true;
    private long rejectedUntil;

    private ChunkBudget(int count, long countExpiresAt) {
      this.count = count;
      this.countExpiresAt = countExpiresAt;
    }

    private synchronized boolean isStale(long currentTime) {
      return currentTime >= countExpiresAt && currentTime >= rejectedUntil;
    }

    private synchronized Admission admit(long currentTime, int maximum, long rejectionWindowMs, boolean reserve) {
      if (rejectedUntil > currentTime) {
        return Admission.REJECT;
      }
      if (count >= maximum) {
        if (!exact) {
          return Admission.RECOUNT;
        }
        rejectedUntil = saturatingAdd(currentTime, rejectionWindowMs);
        return Admission.REJECT;
      }
      if (reserve) {
        count++;
        exact = false;
      }
      return Admission.ADMIT;
    }
  }

}
