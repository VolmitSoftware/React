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

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Hopper Token Bucket feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureHopperTokenBucket extends ReactFeature implements Listener {
  public static final String ID = "hopper-token-bucket";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for hopper token bucket in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 3000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Bucket capacity limit used by hopper token bucket.", impact = "Higher values increase buffered work or burst allowance; lower values tighten throttling.")
  private double bucketCapacity = 120;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Token refill rate used by hopper token bucket.", impact = "Higher values replenish budget faster; lower values enforce stricter sustained throttling.")
  private double refillPerSecond = 55;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Token cost charged per hopper move in hopper token bucket.", impact = "Higher values drain budget faster and throttle sooner; lower values allow more moves before throttling.")
  private double costPerMove = 1;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Bypasses hopper token bucket handling for bypass when nearby players.", impact = "Enable this to skip enforcement in matching situations; disable it for strict handling.")
  private boolean bypassWhenNearbyPlayers = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Bypass player radius used by hopper token bucket (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double bypassPlayerRadius = 16;
  private transient volatile Map<UUID, Long2ObjectOpenHashMap<Bucket>> buckets = new ConcurrentHashMap<>();
  private transient volatile boolean active;

  public FeatureHopperTokenBucket() {
    super(ID);
  }

  @Override
  public void onActivate() {
    buckets = new ConcurrentHashMap<>();
    active = true;
  }

  @Override
  public void onDeactivate() {
    active = false;
    buckets = new ConcurrentHashMap<>();
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    long expiry = 60000L;
    Map<UUID, Long2ObjectOpenHashMap<Bucket>> currentBuckets = buckets;
    Iterator<Map.Entry<UUID, Long2ObjectOpenHashMap<Bucket>>> worldIterator = currentBuckets.entrySet().iterator();
    while (worldIterator.hasNext()) {
      Map.Entry<UUID, Long2ObjectOpenHashMap<Bucket>> worldEntry = worldIterator.next();
      Long2ObjectOpenHashMap<Bucket> chunkMap = worldEntry.getValue();
      synchronized (chunkMap) {
        ObjectIterator<Long2ObjectOpenHashMap.Entry<Bucket>> chunkIterator = chunkMap.long2ObjectEntrySet().fastIterator();
        while (chunkIterator.hasNext()) {
          if (now - chunkIterator.next().getValue().lastUse > expiry) {
            chunkIterator.remove();
          }
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void on(InventoryMoveItemEvent event) {
    Location location = resolveHopperLocation(event);
    if (location == null) {
      return;
    }

    if (!tryConsume(location)) {
      event.setCancelled(true);
    }
  }

  boolean tryConsume(Location location) {
    if (!active) {
      return true;
    }
    if (location == null || location.getWorld() == null) {
      return false;
    }
    if (bypassWhenNearbyPlayers && React.hasNearbyPlayer(location, bypassPlayerRadius)) {
      return true;
    }

    long now = System.currentTimeMillis();
    UUID worldId = location.getWorld().getUID();
    long chunkKey = packChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    Map<UUID, Long2ObjectOpenHashMap<Bucket>> currentBuckets = buckets;
    Long2ObjectOpenHashMap<Bucket> chunkMap = currentBuckets.computeIfAbsent(
        worldId,
        ignored -> new Long2ObjectOpenHashMap<>()
    );
    Bucket bucket;
    synchronized (chunkMap) {
      bucket = chunkMap.get(chunkKey);
      if (bucket == null) {
        bucket = new Bucket(now, bucketCapacity);
        chunkMap.put(chunkKey, bucket);
      }
    }
    return bucket.consume(now, bucketCapacity, refillPerSecond, costPerMove);
  }

  boolean isEnforcing() {
    return active;
  }

  private Location resolveHopperLocation(InventoryMoveItemEvent event) {
    if (event.getSource().getHolder() instanceof Hopper source) {
      Block block = source.getBlock();
      return block == null ? null : block.getLocation();
    }

    if (event.getDestination().getHolder() instanceof Hopper destination) {
      Block block = destination.getBlock();
      return block == null ? null : block.getLocation();
    }

    return null;
  }

  private static final class Bucket {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Tokens limit used by hopper token bucket.", impact = "Higher values increase buffered work or burst allowance; lower values tighten throttling.")
    private double tokens;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by hopper token bucket to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long lastRefill;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by hopper token bucket to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private volatile long lastUse;

    private Bucket(long now, double capacity) {
      tokens = Math.max(1D, capacity);
      lastRefill = now;
      lastUse = now;
    }

    private synchronized boolean consume(long now, double capacity, double refillPerSecond, double cost) {
      double elapsedSeconds = Math.max(0D, (now - lastRefill) / 1000D);
      if (elapsedSeconds > 0) {
        tokens = Math.min(capacity, tokens + (elapsedSeconds * refillPerSecond));
        lastRefill = now;
      }

      lastUse = now;
      if (tokens >= cost) {
        tokens -= cost;
        return true;
      }

      return false;
    }
  }

  private static long packChunk(int cx, int cz) {
    return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
  }
}
