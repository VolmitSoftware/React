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

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

@Data
public class NearbyPlayerIndexController implements IController, Listener {
  private static final int INITIAL_SEED_RETRY_BATCH = 128;
  private transient Map<UUID, Map<Long, Set<UUID>>> playersByWorldChunk;
  private transient Map<UUID, PlayerSnapshot> snapshotsByPlayer;
  private transient Map<UUID, PlayerBucketRef> bucketByPlayer;
  private transient volatile InitialSeedState initialSeedState;
  private transient StampedLock topologyLock;

  @Override
  public String getId() {
    return "nearby-player-index";
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
    topologyLock = new StampedLock();
    List<Player> initialPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
    InitialSeedState seedState = new InitialSeedState(initialPlayers);
    initialSeedState = seedState;
    if (initialPlayers.isEmpty()) {
      seedState.ready.set(true);
      return;
    }

    boolean folia = J.isFoliaThreading();
    for (Player player : initialPlayers) {
      seedInitialPlayer(player, seedState, folia);
    }
  }

  @Override
  public void stop() {
    StampedLock lock = topologyLock;
    long stamp = lock == null ? 0L : lock.writeLock();
    try {
      if (playersByWorldChunk != null) {
        playersByWorldChunk.clear();
      }
      if (snapshotsByPlayer != null) {
        snapshotsByPlayer.clear();
      }
      if (bucketByPlayer != null) {
        bucketByPlayer.clear();
      }
      initialSeedState = null;
    } finally {
      if (lock != null) {
        lock.unlockWrite(stamp);
      }
    }
  }

  @Override
  public void postStart() {

  }

  public boolean hasNearbyPlayer(Location location, double blocks) {
    if (location == null || location.getWorld() == null || blocks <= 0) {
      return false;
    }
    if (!isInitialSeedReady()) {
      return true;
    }
    if (playersByWorldChunk == null || snapshotsByPlayer == null) {
      return false;
    }

    UUID worldId = location.getWorld().getUID();
    Map<Long, Set<UUID>> worldBuckets = playersByWorldChunk.get(worldId);
    if (worldBuckets == null || worldBuckets.isEmpty()) {
      return false;
    }

    double lx = location.getX();
    double ly = location.getY();
    double lz = location.getZ();
    int minChunkX = chunkCoordinate(lx - blocks);
    int maxChunkX = chunkCoordinate(lx + blocks);
    int minChunkZ = chunkCoordinate(lz - blocks);
    int maxChunkZ = chunkCoordinate(lz + blocks);
    long cells = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);

    if (snapshotsByPlayer.size() <= cells) {
      for (PlayerSnapshot snapshot : snapshotsByPlayer.values()) {
        if (withinRange(snapshot, worldId, lx, ly, lz, blocks)) {
          return true;
        }
      }

      return false;
    }

    StampedLock lock = topologyLock;
    long stamp = lock.readLock();
    try {
      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
          Set<UUID> playerIds = worldBuckets.get(chunkKey(chunkX, chunkZ));
          if (playerIds == null || playerIds.isEmpty()) {
            continue;
          }

          for (UUID playerId : playerIds) {
            if (withinRange(snapshotsByPlayer.get(playerId), worldId, lx, ly, lz, blocks)) {
              return true;
            }
          }
        }
      }
    } finally {
      lock.unlockRead(stamp);
    }

    return false;
  }

  public double nearestDistanceSquared(Location location, double blocks) {
    if (location == null || location.getWorld() == null || blocks <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    if (!isInitialSeedReady()) {
      return 0D;
    }
    if (playersByWorldChunk == null || snapshotsByPlayer == null) {
      return Double.POSITIVE_INFINITY;
    }

    UUID worldId = location.getWorld().getUID();
    Map<Long, Set<UUID>> worldBuckets = playersByWorldChunk.get(worldId);
    if (worldBuckets == null || worldBuckets.isEmpty()) {
      return Double.POSITIVE_INFINITY;
    }

    double lx = location.getX();
    double ly = location.getY();
    double lz = location.getZ();
    int minChunkX = chunkCoordinate(lx - blocks);
    int maxChunkX = chunkCoordinate(lx + blocks);
    int minChunkZ = chunkCoordinate(lz - blocks);
    int maxChunkZ = chunkCoordinate(lz + blocks);
    long cells = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
    double nearest = Double.POSITIVE_INFINITY;

    if (snapshotsByPlayer.size() <= cells) {
      for (PlayerSnapshot snapshot : snapshotsByPlayer.values()) {
        nearest = Math.min(nearest, snapshot.distanceSquared(worldId, lx, ly, lz));
      }
      return nearest <= blocks * blocks ? nearest : Double.POSITIVE_INFINITY;
    }

    StampedLock lock = topologyLock;
    long stamp = lock.readLock();
    try {
      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
          Set<UUID> playerIds = worldBuckets.get(chunkKey(chunkX, chunkZ));
          if (playerIds == null || playerIds.isEmpty()) {
            continue;
          }

          for (UUID playerId : playerIds) {
            PlayerSnapshot snapshot = snapshotsByPlayer.get(playerId);
            if (snapshot != null) {
              nearest = Math.min(nearest, snapshot.distanceSquared(worldId, lx, ly, lz));
            }
          }
        }
      }
    } finally {
      lock.unlockRead(stamp);
    }

    return nearest <= blocks * blocks ? nearest : Double.POSITIVE_INFINITY;
  }

  public boolean hasNearbyPlayerInColumn(World world, double x, double z, double blocks) {
    if (world == null || blocks <= 0) {
      return false;
    }
    if (!isInitialSeedReady()) {
      return true;
    }
    if (playersByWorldChunk == null || snapshotsByPlayer == null) {
      return false;
    }

    UUID worldId = world.getUID();
    Map<Long, Set<UUID>> worldBuckets = playersByWorldChunk.get(worldId);
    if (worldBuckets == null || worldBuckets.isEmpty()) {
      return false;
    }

    int minChunkX = chunkCoordinate(x - blocks);
    int maxChunkX = chunkCoordinate(x + blocks);
    int minChunkZ = chunkCoordinate(z - blocks);
    int maxChunkZ = chunkCoordinate(z + blocks);
    long cells = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);

    if (snapshotsByPlayer.size() <= cells) {
      for (PlayerSnapshot snapshot : snapshotsByPlayer.values()) {
        if (snapshot.horizontalDistanceSquared(worldId, x, z) <= blocks * blocks) {
          return true;
        }
      }
      return false;
    }

    StampedLock lock = topologyLock;
    long stamp = lock.readLock();
    try {
      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
          Set<UUID> playerIds = worldBuckets.get(chunkKey(chunkX, chunkZ));
          if (playerIds == null || playerIds.isEmpty()) {
            continue;
          }

          for (UUID playerId : playerIds) {
            PlayerSnapshot snapshot = snapshotsByPlayer.get(playerId);
            if (snapshot != null && snapshot.horizontalDistanceSquared(worldId, x, z) <= blocks * blocks) {
              return true;
            }
          }
        }
      }
    } finally {
      lock.unlockRead(stamp);
    }

    return false;
  }

  public List<PlayerViewSnapshot> playerSnapshotsInColumn(
      World world,
      double x,
      double z,
      double blocks
  ) {
    return playerSnapshotsInColumn(world == null ? null : world.getUID(), x, z, blocks, Integer.MAX_VALUE);
  }

  public List<PlayerViewSnapshot> playerSnapshotsInColumn(
      UUID worldId,
      double x,
      double z,
      double blocks
  ) {
    return playerSnapshotsInColumn(worldId, x, z, blocks, Integer.MAX_VALUE);
  }

  public List<PlayerViewSnapshot> playerSnapshotsInColumn(
      World world,
      double x,
      double z,
      double blocks,
      int maximum
  ) {
    return playerSnapshotsInColumn(world == null ? null : world.getUID(), x, z, blocks, maximum);
  }

  public List<PlayerViewSnapshot> playerSnapshotsInColumn(
      UUID worldId,
      double x,
      double z,
      double blocks,
      int maximum
  ) {
    int limit = Math.max(0, maximum);
    if (worldId == null
        || blocks <= 0
        || limit == 0
        || playersByWorldChunk == null
        || snapshotsByPlayer == null) {
      return List.of();
    }

    Map<Long, Set<UUID>> worldBuckets = playersByWorldChunk.get(worldId);
    if (worldBuckets == null || worldBuckets.isEmpty()) {
      return List.of();
    }

    int minChunkX = chunkCoordinate(x - blocks);
    int maxChunkX = chunkCoordinate(x + blocks);
    int minChunkZ = chunkCoordinate(z - blocks);
    int maxChunkZ = chunkCoordinate(z + blocks);
    long cells = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
    double maximumDistanceSquared = blocks * blocks;
    List<PlayerViewSnapshot> result = new ArrayList<>();
    if (snapshotsByPlayer.size() <= cells) {
      for (Map.Entry<UUID, PlayerSnapshot> entry : snapshotsByPlayer.entrySet()) {
        if (result.size() >= limit) {
          break;
        }
        PlayerSnapshot snapshot = entry.getValue();
        if (snapshot != null
            && snapshot.horizontalDistanceSquared(worldId, x, z) <= maximumDistanceSquared) {
          result.add(snapshot.view(entry.getKey()));
        }
      }
      return result;
    }

    Set<UUID> visited = new HashSet<>();
    StampedLock lock = topologyLock;
    long stamp = lock.readLock();
    try {
      boolean full = false;
      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
          Set<UUID> playerIds = worldBuckets.get(chunkKey(chunkX, chunkZ));
          if (playerIds == null || playerIds.isEmpty()) {
            continue;
          }

          for (UUID playerId : playerIds) {
            if (!visited.add(playerId)) {
              continue;
            }

            PlayerSnapshot snapshot = snapshotsByPlayer.get(playerId);
            if (snapshot == null
                || snapshot.horizontalDistanceSquared(worldId, x, z) > maximumDistanceSquared) {
              continue;
            }
            result.add(snapshot.view(playerId));
            if (result.size() >= limit) {
              full = true;
              break;
            }
          }
          if (full) {
            break;
          }
        }
        if (full) {
          break;
        }
      }
    } finally {
      lock.unlockRead(stamp);
    }
    return result;
  }

  private boolean withinRange(
      PlayerSnapshot snapshot,
      UUID worldId,
      double lx,
      double ly,
      double lz,
      double blocks
  ) {
    return snapshot != null && snapshot.withinRange(worldId, lx, ly, lz, blocks);
  }

  @EventHandler
  public void on(PlayerJoinEvent event) {
    updateFromPlayer(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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

    updateFromPlayerLocation(event.getPlayer(), to);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent event) {
    Location to = event.getTo();
    if (to == null || to.getWorld() == null) {
      return;
    }

    updateFromPlayerLocation(event.getPlayer(), to);
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

    updateFromPlayerLocation(event.getPlayer(), location);
  }

  @EventHandler
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    removePlayer(playerId);
    resolveInitialSeed(playerId);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(WorldUnloadEvent event) {
    if (event.isCancelled()) {
      return;
    }
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
      updateFromPlayerLocation(player, player.getLocation());
      return;
    }

    if (J.isOwnedByCurrentRegion(player)) {
      updateFromPlayerLocation(player, player.getLocation());
      return;
    }

    J.runEntity(player, () -> updateFromPlayerLocation(player, player.getLocation()));
  }

  public boolean isInitialSeedReady() {
    InitialSeedState seedState = initialSeedState;
    return seedState != null && seedState.ready.get();
  }

  private void seedInitialPlayer(Player player, InitialSeedState seedState, boolean folia) {
    attemptInitialSeed(player, seedState, folia);
  }

  private void attemptInitialSeed(Player player, InitialSeedState seedState, boolean folia) {
    if (player == null
        || initialSeedState != seedState
        || seedState.completed(player.getUniqueId())) {
      return;
    }

    Runnable retired = () -> queueInitialSeedRetry(player, seedState, folia);
    Runnable update = () -> {
      try {
        updateFromPlayerLocation(player, player.getLocation());
      } catch (Throwable failure) {
        React.reportError(new IllegalStateException(
            "Failed to seed nearby-player state for " + player.getUniqueId(),
            failure
        ));
        queueInitialSeedRetry(player, seedState, folia);
      }
    };

    if (!folia || J.isOwnedByCurrentRegion(player)) {
      update.run();
      return;
    }

    if (!J.runEntity(player, update, 0, retired)) {
      queueInitialSeedRetry(player, seedState, folia);
    }
  }

  private void queueInitialSeedRetry(Player player, InitialSeedState seedState, boolean folia) {
    if (player == null
        || initialSeedState != seedState
        || seedState.completed(player.getUniqueId())
        || !seedState.retryIds.add(player.getUniqueId())) {
      return;
    }
    seedState.retryQueue.offer(player);
    scheduleInitialSeedRetryDrain(seedState, folia);
  }

  private void scheduleInitialSeedRetryDrain(InitialSeedState seedState, boolean folia) {
    if (!seedState.retryDrainScheduled.compareAndSet(false, true)) {
      return;
    }
    J.a(() -> drainInitialSeedRetries(seedState, folia), 1);
  }

  private void drainInitialSeedRetries(InitialSeedState seedState, boolean folia) {
    try {
      if (initialSeedState != seedState) {
        seedState.retryQueue.clear();
        seedState.retryIds.clear();
        return;
      }
      for (int processed = 0; processed < INITIAL_SEED_RETRY_BATCH; processed++) {
        Player player = seedState.retryQueue.poll();
        if (player == null) {
          break;
        }
        seedState.retryIds.remove(player.getUniqueId());
        attemptInitialSeed(player, seedState, folia);
      }
    } finally {
      seedState.retryDrainScheduled.set(false);
      if (initialSeedState == seedState && !seedState.retryQueue.isEmpty()) {
        scheduleInitialSeedRetryDrain(seedState, folia);
      }
    }
  }

  private void resolveInitialSeed(UUID playerId) {
    InitialSeedState seedState = initialSeedState;
    if (seedState == null || playerId == null || !seedState.complete(playerId)) {
      return;
    }
    seedState.pending.decrementAndGet();
    publishInitialSeedReady(seedState);
  }

  private void publishInitialSeedReady(InitialSeedState seedState) {
    if (seedState.pending.get() == 0
        && seedState.unresolved.isEmpty()
        && initialSeedState == seedState) {
      seedState.ready.set(true);
    }
  }

  public void injectSynthetic(UUID syntheticId, Location location) {
    updateFromLocation(syntheticId, location);
  }

  public void clearSynthetic(UUID syntheticId) {
    removePlayer(syntheticId);
  }

  private void updateFromLocation(UUID playerId, Location location) {
    updateFromLocation(playerId, "", location, 0D, false, false);
  }

  private void updateFromPlayerLocation(Player player, Location location) {
    if (player == null) {
      return;
    }

    updateFromLocation(
        player.getUniqueId(),
        player.getName(),
        location,
        player.getVelocity().length(),
        player.isGliding(),
        player.getVehicle() != null
    );
    resolveInitialSeed(player.getUniqueId());
  }

  private void updateFromLocation(
      UUID playerId,
      String playerName,
      Location location,
      double speed,
      boolean gliding,
      boolean mounted
  ) {
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
    PlayerBucketRef previous = bucketByPlayer.get(playerId);
    boolean sameBucket = previous != null && previous.worldId.equals(worldId) && previous.chunkKey == chunkKey;

    if (sameBucket) {
      PlayerSnapshot existing = snapshotsByPlayer.get(playerId);
      if (existing != null && worldId.equals(existing.worldId)) {
        existing.update(playerName, x, y, z, speed, gliding, mounted);
        return;
      }
    }

    StampedLock lock = topologyLock;
    long stamp = lock.writeLock();
    try {
      previous = bucketByPlayer.get(playerId);
      sameBucket = previous != null && previous.worldId.equals(worldId) && previous.chunkKey == chunkKey;
      if (sameBucket) {
        PlayerSnapshot existing = snapshotsByPlayer.get(playerId);
        if (existing != null && worldId.equals(existing.worldId)) {
          existing.update(playerName, x, y, z, speed, gliding, mounted);
          return;
        }
      }

      addToBucket(playerId, worldId, chunkKey);
      snapshotsByPlayer.put(
          playerId,
          new PlayerSnapshot(worldId, playerName, x, y, z, speed, gliding, mounted)
      );
      bucketByPlayer.put(playerId, new PlayerBucketRef(worldId, chunkKey));
      if (previous != null && (!previous.worldId.equals(worldId) || previous.chunkKey != chunkKey)) {
        removeFromBucket(playerId, previous.worldId, previous.chunkKey);
      }
    } finally {
      lock.unlockWrite(stamp);
    }
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

    StampedLock lock = topologyLock;
    long stamp = lock.writeLock();
    try {
      snapshotsByPlayer.remove(playerId);
      PlayerBucketRef previous = bucketByPlayer.remove(playerId);
      if (previous != null) {
        removeFromBucket(playerId, previous.worldId, previous.chunkKey);
      }
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  private void removeWorld(UUID worldId) {
    if (worldId == null || playersByWorldChunk == null || snapshotsByPlayer == null || bucketByPlayer == null) {
      return;
    }

    StampedLock lock = topologyLock;
    long stamp = lock.writeLock();
    try {
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
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  private int chunkCoordinate(double blockCoordinate) {
    return (int) Math.floor(blockCoordinate / 16.0D);
  }

  private long chunkKey(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
  }

  private static final class InitialSeedState {
    private final AtomicInteger pending;
    private final AtomicBoolean ready;
    private final Set<UUID> unresolved;
    private final Map<UUID, AtomicBoolean> terminals;
    private final ConcurrentLinkedQueue<Player> retryQueue;
    private final Set<UUID> retryIds;
    private final AtomicBoolean retryDrainScheduled;

    private InitialSeedState(List<Player> players) {
      this.pending = new AtomicInteger(players.size());
      this.ready = new AtomicBoolean(false);
      this.unresolved = ConcurrentHashMap.newKeySet();
      this.terminals = new ConcurrentHashMap<>();
      this.retryQueue = new ConcurrentLinkedQueue<>();
      this.retryIds = ConcurrentHashMap.newKeySet();
      this.retryDrainScheduled = new AtomicBoolean(false);
      for (Player player : players) {
        UUID playerId = player.getUniqueId();
        unresolved.add(playerId);
        terminals.put(playerId, new AtomicBoolean(false));
      }
    }

    private boolean complete(UUID playerId) {
      AtomicBoolean terminal = terminals.get(playerId);
      if (terminal == null || !terminal.compareAndSet(false, true)) {
        return false;
      }
      unresolved.remove(playerId);
      retryIds.remove(playerId);
      return true;
    }

    private boolean completed(UUID playerId) {
      AtomicBoolean terminal = terminals.get(playerId);
      return terminal == null || terminal.get();
    }
  }

  private static final class PlayerSnapshot {
    private final UUID worldId;
    private final StampedLock positionLock;
    private String name;
    private double x;
    private double y;
    private double z;
    private double speed;
    private boolean gliding;
    private boolean mounted;

    private PlayerSnapshot(
        UUID worldId,
        String name,
        double x,
        double y,
        double z,
        double speed,
        boolean gliding,
        boolean mounted
    ) {
      this.worldId = worldId;
      this.positionLock = new StampedLock();
      this.name = name == null ? "" : name;
      this.x = x;
      this.y = y;
      this.z = z;
      this.speed = speed;
      this.gliding = gliding;
      this.mounted = mounted;
    }

    private void update(
        String name,
        double x,
        double y,
        double z,
        double speed,
        boolean gliding,
        boolean mounted
    ) {
      long stamp = positionLock.writeLock();
      try {
        this.name = name == null ? "" : name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.speed = speed;
        this.gliding = gliding;
        this.mounted = mounted;
      } finally {
        positionLock.unlockWrite(stamp);
      }
    }

    private boolean withinRange(UUID expectedWorldId, double lx, double ly, double lz, double blocks) {
      return distanceSquared(expectedWorldId, lx, ly, lz) <= blocks * blocks;
    }

    private double distanceSquared(UUID expectedWorldId, double lx, double ly, double lz) {
      if (!worldId.equals(expectedWorldId)) {
        return Double.POSITIVE_INFINITY;
      }

      long stamp = positionLock.tryOptimisticRead();
      double snapshotX = x;
      double snapshotY = y;
      double snapshotZ = z;
      if (!positionLock.validate(stamp)) {
        stamp = positionLock.readLock();
        try {
          snapshotX = x;
          snapshotY = y;
          snapshotZ = z;
        } finally {
          positionLock.unlockRead(stamp);
        }
      }

      double dx = snapshotX - lx;
      double dy = snapshotY - ly;
      double dz = snapshotZ - lz;
      return (dx * dx) + (dy * dy) + (dz * dz);
    }

    private double horizontalDistanceSquared(UUID expectedWorldId, double lx, double lz) {
      if (!worldId.equals(expectedWorldId)) {
        return Double.POSITIVE_INFINITY;
      }

      long stamp = positionLock.tryOptimisticRead();
      double snapshotX = x;
      double snapshotZ = z;
      if (!positionLock.validate(stamp)) {
        stamp = positionLock.readLock();
        try {
          snapshotX = x;
          snapshotZ = z;
        } finally {
          positionLock.unlockRead(stamp);
        }
      }

      double dx = snapshotX - lx;
      double dz = snapshotZ - lz;
      return (dx * dx) + (dz * dz);
    }

    private PlayerViewSnapshot view(UUID playerId) {
      long stamp = positionLock.readLock();
      try {
        return new PlayerViewSnapshot(
            playerId,
            name,
            worldId,
            x,
            y,
            z,
            speed,
            gliding,
            mounted
        );
      } finally {
        positionLock.unlockRead(stamp);
      }
    }
  }

  public record PlayerViewSnapshot(
      UUID playerId,
      String name,
      UUID worldId,
      double x,
      double y,
      double z,
      double speed,
      boolean gliding,
      boolean mounted
  ) {
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
