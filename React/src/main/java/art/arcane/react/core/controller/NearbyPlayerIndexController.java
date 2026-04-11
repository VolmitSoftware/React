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

package art.arcane.react.core.controller;

import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class NearbyPlayerIndexController extends TickedObject implements IController {
  private static final long STALE_SNAPSHOT_RETENTION_MS = 900_000L;
  private transient Map<UUID, Map<Long, Set<UUID>>> playersByWorldChunk;
  private transient Map<UUID, PlayerSnapshot> snapshotsByPlayer;
  private transient Map<UUID, PlayerBucketRef> bucketByPlayer;

  public NearbyPlayerIndexController() {
    super("react", "nearby-player-index", 5000L);
  }

  @Override
  public String getName() {
    return "Nearby Player Index";
  }

  @Override
  public void start() {
    playersByWorldChunk = new ConcurrentHashMap<>();
    snapshotsByPlayer = new ConcurrentHashMap<>();
    bucketByPlayer = new ConcurrentHashMap<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      updateFromPlayer(player);
    }
  }

  @Override
  public void stop() {
    if (playersByWorldChunk != null) {
      playersByWorldChunk.clear();
    }
    if (snapshotsByPlayer != null) {
      snapshotsByPlayer.clear();
    }
    if (bucketByPlayer != null) {
      bucketByPlayer.clear();
    }
  }

  @Override
  public void postStart() {

  }

  @Override
  public void onTick() {
    pruneStaleSnapshots();
  }

  public boolean hasNearbyPlayer(Location location, double blocks) {
    if (location == null || location.getWorld() == null || blocks <= 0) {
      return false;
    }
    if (playersByWorldChunk == null || snapshotsByPlayer == null) {
      return false;
    }

    UUID worldId = location.getWorld().getUID();
    Map<Long, Set<UUID>> worldBuckets = playersByWorldChunk.get(worldId);
    if (worldBuckets == null || worldBuckets.isEmpty()) {
      return false;
    }

    long now = System.currentTimeMillis();
    double radiusSquared = blocks * blocks;
    double lx = location.getX();
    double ly = location.getY();
    double lz = location.getZ();
    int minChunkX = chunkCoordinate(lx - blocks);
    int maxChunkX = chunkCoordinate(lx + blocks);
    int minChunkZ = chunkCoordinate(lz - blocks);
    int maxChunkZ = chunkCoordinate(lz + blocks);

    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        Set<UUID> playerIds = worldBuckets.get(chunkKey(chunkX, chunkZ));
        if (playerIds == null || playerIds.isEmpty()) {
          continue;
        }

        for (UUID playerId : playerIds) {
          PlayerSnapshot snapshot = snapshotsByPlayer.get(playerId);
          if (snapshot == null || !worldId.equals(snapshot.worldId)) {
            continue;
          }

          if (now - snapshot.lastUpdateMs > STALE_SNAPSHOT_RETENTION_MS) {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isOnline()) {
              continue;
            }
          }

          double dx = snapshot.x - lx;
          if (dx > blocks || dx < -blocks) {
            continue;
          }

          double dy = snapshot.y - ly;
          if (dy > blocks || dy < -blocks) {
            continue;
          }

          double dz = snapshot.z - lz;
          if (dz > blocks || dz < -blocks) {
            continue;
          }

          if ((dx * dx) + (dy * dy) + (dz * dz) <= radiusSquared) {
            return true;
          }
        }
      }
    }

    return false;
  }

  @EventHandler
  public void on(PlayerJoinEvent event) {
    updateFromPlayer(event.getPlayer());
  }

  @EventHandler
  public void on(PlayerMoveEvent event) {
    Location to = event.getTo();
    if (to == null || to.getWorld() == null) {
      return;
    }

    Location from = event.getFrom();
    if (from != null
        && from.getWorld() != null
        && from.getWorld().equals(to.getWorld())
        && from.getX() == to.getX()
        && from.getY() == to.getY()
        && from.getZ() == to.getZ()) {
      return;
    }

    updateFromLocation(event.getPlayer().getUniqueId(), to);
  }

  @EventHandler
  public void on(PlayerTeleportEvent event) {
    Location to = event.getTo();
    if (to == null || to.getWorld() == null) {
      return;
    }

    updateFromLocation(event.getPlayer().getUniqueId(), to);
  }

  @EventHandler
  public void on(PlayerChangedWorldEvent event) {
    updateFromPlayer(event.getPlayer());
  }

  @EventHandler
  public void on(PlayerRespawnEvent event) {
    Location location = event.getRespawnLocation();
    if (location == null || location.getWorld() == null) {
      return;
    }

    updateFromLocation(event.getPlayer().getUniqueId(), location);
  }

  @EventHandler
  public void on(PlayerQuitEvent event) {
    removePlayer(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void on(WorldUnloadEvent event) {
    World world = event.getWorld();
    if (world == null) {
      return;
    }

    removeWorld(world.getUID());
  }

  private void updateFromPlayer(Player player) {
    if (player == null) {
      return;
    }

    if (!J.isFoliaThreading()) {
      updateFromLocation(player.getUniqueId(), player.getLocation());
      return;
    }

    if (J.isOwnedByCurrentRegion(player)) {
      updateFromLocation(player.getUniqueId(), player.getLocation());
      return;
    }

    J.runEntity(player, () -> updateFromLocation(player.getUniqueId(), player.getLocation()));
  }

  private void updateFromLocation(UUID playerId, Location location) {
    if (playerId == null || location == null || location.getWorld() == null) {
      return;
    }
    if (playersByWorldChunk == null || snapshotsByPlayer == null || bucketByPlayer == null) {
      return;
    }

    UUID worldId = location.getWorld().getUID();
    double x = location.getX();
    double y = location.getY();
    double z = location.getZ();
    int chunkX = chunkCoordinate(x);
    int chunkZ = chunkCoordinate(z);
    long chunkKey = chunkKey(chunkX, chunkZ);
    long now = System.currentTimeMillis();
    PlayerSnapshot snapshot = new PlayerSnapshot(playerId, worldId, x, y, z, now);
    snapshotsByPlayer.put(playerId, snapshot);

    PlayerBucketRef previous = bucketByPlayer.put(playerId, new PlayerBucketRef(worldId, chunkKey));
    if (previous != null && (!previous.worldId.equals(worldId) || previous.chunkKey != chunkKey)) {
      removeFromBucket(playerId, previous.worldId, previous.chunkKey);
    }

    addToBucket(playerId, worldId, chunkKey);
  }

  private void addToBucket(UUID playerId, UUID worldId, long chunkKey) {
    Map<Long, Set<UUID>> worldBuckets = playersByWorldChunk.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>());
    Set<UUID> bucket = worldBuckets.computeIfAbsent(chunkKey, ignored -> ConcurrentHashMap.newKeySet());
    bucket.add(playerId);
  }

  private void removeFromBucket(UUID playerId, UUID worldId, long chunkKey) {
    Map<Long, Set<UUID>> worldBuckets = playersByWorldChunk.get(worldId);
    if (worldBuckets == null) {
      return;
    }

    Set<UUID> bucket = worldBuckets.get(chunkKey);
    if (bucket == null) {
      return;
    }

    bucket.remove(playerId);
    if (bucket.isEmpty()) {
      worldBuckets.remove(chunkKey, bucket);
    }
    if (worldBuckets.isEmpty()) {
      playersByWorldChunk.remove(worldId, worldBuckets);
    }
  }

  private void removePlayer(UUID playerId) {
    if (playerId == null || snapshotsByPlayer == null || bucketByPlayer == null) {
      return;
    }

    snapshotsByPlayer.remove(playerId);
    PlayerBucketRef previous = bucketByPlayer.remove(playerId);
    if (previous != null) {
      removeFromBucket(playerId, previous.worldId, previous.chunkKey);
    }
  }

  private void removeWorld(UUID worldId) {
    if (worldId == null || playersByWorldChunk == null || snapshotsByPlayer == null || bucketByPlayer == null) {
      return;
    }

    Map<Long, Set<UUID>> removedBuckets = playersByWorldChunk.remove(worldId);
    if (removedBuckets == null || removedBuckets.isEmpty()) {
      return;
    }

    for (Set<UUID> bucket : removedBuckets.values()) {
      if (bucket == null || bucket.isEmpty()) {
        continue;
      }

      for (UUID playerId : bucket) {
        PlayerSnapshot snapshot = snapshotsByPlayer.get(playerId);
        if (snapshot != null && worldId.equals(snapshot.worldId)) {
          snapshotsByPlayer.remove(playerId, snapshot);
        }

        PlayerBucketRef ref = bucketByPlayer.get(playerId);
        if (ref != null && worldId.equals(ref.worldId)) {
          bucketByPlayer.remove(playerId, ref);
        }
      }
    }
  }

  private void pruneStaleSnapshots() {
    if (snapshotsByPlayer == null || snapshotsByPlayer.isEmpty()) {
      return;
    }

    long cutoff = System.currentTimeMillis() - STALE_SNAPSHOT_RETENTION_MS;
    for (Map.Entry<UUID, PlayerSnapshot> entry : snapshotsByPlayer.entrySet()) {
      UUID playerId = entry.getKey();
      PlayerSnapshot snapshot = entry.getValue();
      if (playerId == null || snapshot == null || snapshot.lastUpdateMs >= cutoff) {
        continue;
      }

      Player online = Bukkit.getPlayer(playerId);
      if (online != null && online.isOnline()) {
        continue;
      }

      removePlayer(playerId);
    }
  }

  private int chunkCoordinate(double blockCoordinate) {
    return (int) Math.floor(blockCoordinate / 16.0D);
  }

  private long chunkKey(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
  }

  private static final class PlayerSnapshot {
    private final UUID playerId;
    private final UUID worldId;
    private final double x;
    private final double y;
    private final double z;
    private final long lastUpdateMs;

    private PlayerSnapshot(UUID playerId, UUID worldId, double x, double y, double z, long lastUpdateMs) {
      this.playerId = playerId;
      this.worldId = worldId;
      this.x = x;
      this.y = y;
      this.z = z;
      this.lastUpdateMs = lastUpdateMs;
    }
  }

  private static final class PlayerBucketRef {
    private final UUID worldId;
    private final long chunkKey;

    private PlayerBucketRef(UUID worldId, long chunkKey) {
      this.worldId = worldId;
      this.chunkKey = chunkKey;
    }
  }
}
