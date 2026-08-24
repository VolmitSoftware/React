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
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactProtection;
import art.arcane.react.api.protect.internal.ProtectionGuards;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.CustomMobChecker;
import art.arcane.react.util.project.world.EntityRemovalPolicy;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Entity Trimmer feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureEntityTrimmer extends ReactFeature {
  public static final String ID = "entity-trimmer";
  private static final int MAX_ANCHORS_PER_CYCLE = 24;
  private static final int MAX_INSPECTED_ENTITIES_PER_ANCHOR = 512;
  private static final int MAX_CANDIDATES_PER_GROUP = 1024;
  private static final int MAX_CANDIDATES_PER_CYCLE = 16384;
  private static final int MAX_REMOVALS_PER_CYCLE = 512;
  private transient double maxPriority = -1;
  private transient final AtomicBoolean trimQueued = new AtomicBoolean(false);
  private transient final AtomicInteger cooldown = new AtomicInteger(0);
  private transient final AtomicInteger nextAnchor = new AtomicInteger(0);
  private transient final AtomicInteger nextEntity = new AtomicInteger(0);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
  private transient volatile boolean active = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips skip custom mobs when entity trimmer evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean skipCustomMobs = false;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Protects player-named entities from entity trimmer removal.", impact = "Keep enabled to exclude entities with a custom name; disable it to make named entities eligible for trimming.")
  private boolean protectNamedEntities = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Player mob block radius used by entity trimmer (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private int playerMobBlockDistance = 32;

  /**
   * List of blacklisted entities with already blacklisted examples
   */
  private List<EntityType> blacklist = List.of(
      EntityType.ITEM_DISPLAY, EntityType.PLAYER, EntityType.ARMOR_STAND, EntityType.ITEM_FRAME, EntityType.PAINTING, EntityType.LEASH_KNOT,
      EntityType.MINECART, EntityType.CHEST_MINECART, EntityType.COMMAND_BLOCK_MINECART, EntityType.FURNACE_MINECART,
      EntityType.HOPPER_MINECART, EntityType.SPAWNER_MINECART, EntityType.TNT_MINECART,
      EntityType.ACACIA_BOAT, EntityType.BAMBOO_RAFT, EntityType.BIRCH_BOAT, EntityType.CHERRY_BOAT, EntityType.DARK_OAK_BOAT,
      EntityType.JUNGLE_BOAT, EntityType.MANGROVE_BOAT, EntityType.OAK_BOAT, EntityType.PALE_OAK_BOAT, EntityType.SPRUCE_BOAT,
      EntityType.ACACIA_CHEST_BOAT, EntityType.BAMBOO_CHEST_RAFT, EntityType.BIRCH_CHEST_BOAT, EntityType.CHERRY_CHEST_BOAT,
      EntityType.DARK_OAK_CHEST_BOAT, EntityType.JUNGLE_CHEST_BOAT, EntityType.MANGROVE_CHEST_BOAT, EntityType.OAK_CHEST_BOAT,
      EntityType.PALE_OAK_CHEST_BOAT, EntityType.SPRUCE_CHEST_BOAT,
      EntityType.FALLING_BLOCK, EntityType.ITEM, EntityType.EXPERIENCE_ORB, EntityType.FISHING_BOBBER,
      EntityType.TNT, EntityType.SPLASH_POTION, EntityType.EXPERIENCE_BOTTLE, EntityType.ENDER_PEARL,
      EntityType.EYE_OF_ENDER, EntityType.FIREWORK_ROCKET, EntityType.LIGHTNING_BOLT, EntityType.SHULKER_BULLET,
      EntityType.SMALL_FIREBALL, EntityType.SNOWBALL, EntityType.SPECTRAL_ARROW, EntityType.SPLASH_POTION,
      EntityType.EXPERIENCE_BOTTLE);
  private transient volatile Set<EntityType> blacklistedTypes = Set.of();

  /**
   * Calculates total chunks * softMax to see if we are exceeding
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Enables extra logging for print entity purge success in entity trimmer.", impact = "Enable for diagnostics; disable to reduce chat or log noise.")
  private boolean printEntityPurgeSuccess = true;

  /**
   * Calculates total chunks * softMax to see if we are exceeding
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entities allowed per chunk in entity trimmer.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int softMaxEntitiesPerChunk = 11;

  /**
   * Calculates players * softMax to see if we are exceeding
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entities allowed per player in entity trimmer.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int softMaxEntitiesPerPlayer = 100;

  /**
   * Calculates worlds * softMax to see if we are exceeding
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entities allowed per world in entity trimmer.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int softMaxEntitiesPerWorld = 1000;

  /**
   * Use the lowest X percent of entities by priority. Anything higher than the
   * cutoff wont be touched
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Priority value used when entity trimmer orders targets.", impact = "Higher values move entries earlier in processing; lower values push them later.")
  private double priorityPercentCutoff = 0.1;

  /**
   * How often to tick in ms
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for entity trimmer in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;

  /**
   * Will only run if it can take away X percent of entities. Wont take more per
   * tick either
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for opporunity in entity trimmer.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double opporunityThreshold = 0.25;

  /**
   * The minimum amount of entities to kill per cycle. Lower than this it wont
   * run
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum kill batch size required by entity trimmer.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private int minKillBatchSize = 100;

  public FeatureEntityTrimmer() {
    super(ID);
  }

  @Override
  public void onActivate() {
    lifecycleLock.writeLock().lock();
    try {
      active = false;
      lifecycleGeneration.incrementAndGet();
      trimQueued.set(false);
      cooldown.set(0);
      nextAnchor.set(0);
      nextEntity.set(0);
      EnumSet<EntityType> configuredBlacklist = EnumSet.noneOf(EntityType.class);
      if (blacklist != null) {
        for (EntityType entityType : blacklist) {
          if (entityType != null) {
            configuredBlacklist.add(entityType);
          }
        }
      }
      blacklistedTypes = configuredBlacklist;
      double highestPriority = -1;
      double lowestPriority = Double.MAX_VALUE;

      for (EntityType entityType : EntityType.values()) {
        double priority = ReactConfiguration.get().getPriority().getPriority(entityType);
        if (priority > highestPriority) {
          highestPriority = priority;
        }

        if (priority < lowestPriority) {
          lowestPriority = priority;
        }
      }

      double configuredCutoff = Double.isFinite(priorityPercentCutoff) ? priorityPercentCutoff : 0.1D;
      double cutoff = Math.max(0D, Math.min(1D, configuredCutoff));
      maxPriority = M.lerp(Math.max(lowestPriority, 0), highestPriority, cutoff);
      React.verbose("Entity Trimmer Priority Cutoff: " + maxPriority + " or lower");
      active = true;
    } finally {
      lifecycleLock.writeLock().unlock();
    }
  }

  @Override
  public void onDeactivate() {
    lifecycleLock.writeLock().lock();
    try {
      active = false;
      lifecycleGeneration.incrementAndGet();
      trimQueued.set(false);
      blacklistedTypes = Set.of();
    } finally {
      lifecycleLock.writeLock().unlock();
    }
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    if (!active) {
      return;
    }

    long generation = lifecycleGeneration.get();
    int remainingCooldown = cooldown.getAndUpdate(value -> Math.max(0, value - 1));
    if (remainingCooldown > 0 || !trimQueued.compareAndSet(false, true)) {
      return;
    }

    if (!isCurrent(generation)) {
      trimQueued.set(false);
      return;
    }

    boolean folia = J.isFoliaThreading();
    if (!folia) {
      try {
        J.s(() -> startScanCycle(false, generation));
      } catch (Throwable throwable) {
        trimQueued.set(false);
        React.reportError(throwable);
      }
      return;
    }

    startScanCycle(true, generation);
  }

  private void startScanCycle(boolean folia, long generation) {
    if (!isCurrent(generation)) {
      trimQueued.set(false);
      return;
    }

    Player[] players = capturePlayers(folia);
    if (!isCurrent(generation) || players.length == 0) {
      trimQueued.set(false);
      return;
    }

    int anchorCount = Math.min(players.length, MAX_ANCHORS_PER_CYCLE);
    int start = Math.floorMod(nextAnchor.getAndAdd(anchorCount), players.length);
    ScanFlight flight = new ScanFlight(generation, anchorCount);
    for (int i = 0; i < anchorCount; i++) {
      Player player = players[(start + i) % players.length];
      if (folia) {
        flight.scheduleScan(player);
      } else {
        flight.runScan(player, false);
      }
    }
  }

  private Player[] capturePlayers(boolean folia) {
    if (!folia) {
      return Bukkit.getOnlinePlayers().toArray(Player[]::new);
    }

    EntityController controller = React.controller(EntityController.class);
    Player[] players = controller == null ? null : controller.getFoliaPlayers();
    return players == null ? new Player[0] : players;
  }

  private void scanAroundPlayer(Player player, ScanAccumulator accumulator, long generation, boolean folia) {
    if (!isCurrent(generation) || player == null || !player.isOnline()) {
      return;
    }
    if (folia && !J.isOwnedByCurrentRegion(player)) {
      return;
    }

    int radius = Math.max(0, playerMobBlockDistance);
    Location playerLocation = player.getLocation();
    World world = playerLocation.getWorld();
    if (world == null) {
      return;
    }

    List<Entity> nearby = player.getNearbyEntities(radius, radius, radius);
    if (nearby == null || nearby.isEmpty()) {
      return;
    }

    double radiusSquared = (double) radius * radius;
    Map<ChunkKey, Integer> chunkCounts = new HashMap<>();
    int playerCount = countObservedEntities(nearby, playerLocation, radiusSquared, folia, chunkCounts, generation);
    if (!isCurrent(generation) || playerCount == 0) {
      return;
    }

    List<EntityCandidate> candidates = inspectCandidates(nearby, playerLocation, radiusSquared, folia, generation);
    if (candidates.isEmpty()) {
      return;
    }

    int playerOverflow = overflow(playerCount, softMaxEntitiesPerPlayer);
    accumulator.record(ScopeKey.player(player.getUniqueId()), playerOverflow, candidates);

    int worldOverflow = overflow(world.getEntityCount(), softMaxEntitiesPerWorld);
    accumulator.record(ScopeKey.world(world.getUID()), worldOverflow, candidates);

    Map<ChunkKey, List<EntityCandidate>> candidatesByChunk = new HashMap<>();
    for (EntityCandidate candidate : candidates) {
      candidatesByChunk.computeIfAbsent(candidate.chunk, ignored -> new ArrayList<>()).add(candidate);
    }
    for (Map.Entry<ChunkKey, Integer> entry : chunkCounts.entrySet()) {
      List<EntityCandidate> chunkCandidates = candidatesByChunk.get(entry.getKey());
      int chunkCount = exactChunkCount(chunkCandidates, entry.getValue());
      int chunkOverflow = overflow(chunkCount, softMaxEntitiesPerChunk);
      accumulator.record(ScopeKey.chunk(entry.getKey()), chunkOverflow, chunkCandidates);
    }
  }

  private int exactChunkCount(List<EntityCandidate> candidates, int observedCount) {
    if (candidates == null || candidates.isEmpty()) {
      return observedCount;
    }

    Chunk chunk = candidates.getFirst().entity.getChunk();
    return chunk == null ? observedCount : Math.max(observedCount, chunk.getEntities().length);
  }

  private int countObservedEntities(
      List<Entity> nearby,
      Location playerLocation,
      double radiusSquared,
      boolean folia,
      Map<ChunkKey, Integer> chunkCounts,
      long generation
  ) {
    int count = 0;
    for (Entity entity : nearby) {
      if (!isCurrent(generation)) {
        return count;
      }
      if (entity == null || (folia && !J.isOwnedByCurrentRegion(entity))) {
        continue;
      }

      Location location = entity.getLocation();
      if (!withinRadius(playerLocation, location, radiusSquared)) {
        continue;
      }

      count++;
      ChunkKey chunk = ChunkKey.of(location);
      chunkCounts.merge(chunk, 1, Integer::sum);
    }
    return count;
  }

  private List<EntityCandidate> inspectCandidates(
      List<Entity> nearby,
      Location playerLocation,
      double radiusSquared,
      boolean folia,
      long generation
  ) {
    int size = nearby.size();
    int start = Math.floorMod(nextEntity.getAndAdd(MAX_INSPECTED_ENTITIES_PER_ANCHOR), size);
    int inspected = 0;
    List<EntityCandidate> candidates = new ArrayList<>(Math.min(size, MAX_INSPECTED_ENTITIES_PER_ANCHOR));

    for (int offset = 0; offset < size && inspected < MAX_INSPECTED_ENTITIES_PER_ANCHOR; offset++) {
      if (!isCurrent(generation)) {
        return List.of();
      }

      Entity entity = nearby.get((start + offset) % size);
      if (entity == null || (folia && !J.isOwnedByCurrentRegion(entity))) {
        continue;
      }

      Location location = entity.getLocation();
      if (!withinRadius(playerLocation, location, radiusSquared)) {
        continue;
      }
      inspected++;

      UUID entityId = entity.getUniqueId();
      if (entityId == null || !isValidTarget(entity)) {
        continue;
      }

      double priority = ReactEntity.getPriority(entity);
      if (priority < 0D || priority > maxPriority) {
        continue;
      }

      candidates.add(new EntityCandidate(entity, entityId, priority, ChunkKey.of(location)));
    }
    return candidates;
  }

  private boolean withinRadius(Location center, Location location, double radiusSquared) {
    if (location == null || center.getWorld() != location.getWorld()) {
      return false;
    }

    double dx = center.getX() - location.getX();
    double dy = center.getY() - location.getY();
    double dz = center.getZ() - location.getZ();
    return dx * dx + dy * dy + dz * dz <= radiusSquared;
  }

  static int overflow(int count, int cap) {
    if (cap < 0 || count <= cap) {
      return 0;
    }
    return count - cap;
  }

  static int plannedRemovals(int candidates, double opportunityThreshold, int minBatch) {
    if (candidates <= 0 || !Double.isFinite(opportunityThreshold)) {
      return 0;
    }

    double threshold = Math.max(0D, Math.min(1D, opportunityThreshold));
    int planned = (int) Math.min(
        MAX_REMOVALS_PER_CYCLE,
        Math.min(candidates, Math.floor(candidates * threshold))
    );
    return planned < Math.max(0, minBatch) ? 0 : planned;
  }

  private boolean isValidTarget(Entity entity) {
    if (entity == null || entity.isDead()) {
      return false;
    }

    if (entity.getTicksLived() < 400) {
      return false;
    }

    if (blacklistedTypes.contains(entity.getType())) {
      return false;
    }

    if (EntityRemovalPolicy.protectsNamedEntity(entity, protectNamedEntities)) {
      return false;
    }

    if (ReactProtection.isProtected(entity, ReactOperation.TRIM)) {
      return false;
    }

    return !skipCustomMobs || !CustomMobChecker.isCustom(entity);
  }

  private void scheduleKill(Entity entity, ScanFlight flight, long generation) {
    int delay = ThreadLocalRandom.current().nextInt(20);
    flight.scheduleKill(entity, delay, () -> {
      if (!isCurrent(generation)
          || !isValidTarget(entity)
          || !ProtectionGuards.allows(entity, ReactOperation.TRIM)) {
        return;
      }

      lifecycleLock.readLock().lock();
      try {
        if (isCurrent(generation)) {
          React.kill(entity);
        }
      } finally {
        lifecycleLock.readLock().unlock();
      }
    });
  }

  private boolean isCurrent(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  private final class ScanFlight {
    private final long generation;
    private final AtomicInteger pendingScans;
    private final AtomicInteger pendingKills = new AtomicInteger(1);
    private final ScanAccumulator accumulator = new ScanAccumulator();

    private ScanFlight(long generation, int scans) {
      this.generation = generation;
      this.pendingScans = new AtomicInteger(scans);
    }

    private void scheduleScan(Player player) {
      AtomicBoolean completed = new AtomicBoolean(false);
      Runnable completion = () -> {
        if (completed.compareAndSet(false, true)) {
          completeScan();
        }
      };
      boolean scheduled = false;
      try {
        scheduled = J.runEntity(player, () -> {
          try {
            if (isCurrent(generation)) {
              scanAroundPlayer(player, accumulator, generation, true);
            }
          } catch (Throwable throwable) {
            React.reportError(throwable);
          } finally {
            completion.run();
          }
        }, 0, completion);
      } catch (Throwable throwable) {
        React.reportError(throwable);
      } finally {
        if (!scheduled) {
          completion.run();
        }
      }
    }

    private void runScan(Player player, boolean folia) {
      try {
        if (isCurrent(generation)) {
          scanAroundPlayer(player, accumulator, generation, folia);
        }
      } catch (Throwable throwable) {
        React.reportError(throwable);
      } finally {
        completeScan();
      }
    }

    private void completeScan() {
      if (pendingScans.decrementAndGet() != 0) {
        return;
      }

      if (!isCurrent(generation)) {
        trimQueued.set(false);
        return;
      }

      List<EntityCandidate> candidates = accumulator.candidates();
      int maxKill = plannedRemovals(candidates.size(), opporunityThreshold, minKillBatchSize);
      if (maxKill == 0) {
        if (!candidates.isEmpty()) {
          cooldown.addAndGet(3);
        }
        trimQueued.set(false);
        return;
      }

      for (int i = 0; i < maxKill; i++) {
        FeatureEntityTrimmer.this.scheduleKill(candidates.get(i).entity, this, generation);
      }
      if (printEntityPurgeSuccess && isCurrent(generation)) {
        React.verbose(() -> "Entity Trimmer: " + maxKill + " entities queued for removal");
      }
      completeKill();
    }

    private void scheduleKill(Entity entity, int delay, Runnable work) {
      pendingKills.incrementAndGet();
      AtomicBoolean completed = new AtomicBoolean(false);
      Runnable completion = () -> {
        if (completed.compareAndSet(false, true)) {
          completeKill();
        }
      };
      boolean scheduled = false;
      try {
        scheduled = J.runEntity(entity, () -> {
          try {
            if (isCurrent(generation)) {
              work.run();
            }
          } catch (Throwable throwable) {
            React.reportError(throwable);
          } finally {
            completion.run();
          }
        }, delay, completion);
      } catch (Throwable throwable) {
        React.reportError(throwable);
      } finally {
        if (!scheduled) {
          completion.run();
        }
      }
    }

    private void completeKill() {
      if (pendingKills.decrementAndGet() == 0 && isCurrent(generation)) {
        trimQueued.set(false);
      }
    }
  }

  private static final class ScanAccumulator {
    private final Map<ScopeKey, CandidateGroup> groups = new ConcurrentHashMap<>();

    private void record(ScopeKey scope, int overflow, List<EntityCandidate> candidates) {
      if (overflow <= 0 || candidates == null || candidates.isEmpty()) {
        return;
      }

      int capacity = Math.min(overflow, MAX_CANDIDATES_PER_GROUP);
      groups.computeIfAbsent(scope, ignored -> new CandidateGroup()).offer(candidates, capacity);
    }

    private List<EntityCandidate> candidates() {
      BoundedCandidates merged = new BoundedCandidates(MAX_CANDIDATES_PER_CYCLE);
      for (CandidateGroup group : groups.values()) {
        merged.offer(group.snapshot());
      }
      return merged.sorted();
    }
  }

  private static final class EntityCandidate {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Runtime reference field for entity used by entity trimmer.", impact = "This value is typically populated from live game objects and not intended for manual editing.")
    private final Entity entity;
    private final UUID id;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Priority value used when entity trimmer orders targets.", impact = "Higher values move entries earlier in processing; lower values push them later.")
    private final double priority;
    private final ChunkKey chunk;

    private EntityCandidate(Entity entity, UUID id, double priority, ChunkKey chunk) {
      this.entity = entity;
      this.id = id;
      this.priority = priority;
      this.chunk = chunk;
    }

    private double priority() {
      return priority;
    }
  }

  private static final class CandidateGroup {
    private final BoundedCandidates candidates = new BoundedCandidates(MAX_CANDIDATES_PER_GROUP);
    private int capacity;

    private synchronized void offer(List<EntityCandidate> offered, int requestedCapacity) {
      capacity = Math.max(capacity, requestedCapacity);
      candidates.offer(offered);
    }

    private synchronized List<EntityCandidate> snapshot() {
      List<EntityCandidate> sorted = candidates.sorted();
      if (sorted.size() <= capacity) {
        return sorted;
      }
      return new ArrayList<>(sorted.subList(0, capacity));
    }
  }

  private static final class BoundedCandidates {
    private static final Comparator<EntityCandidate> WORST_FIRST = Comparator
        .comparingDouble(EntityCandidate::priority)
        .reversed();
    private final int capacity;
    private final PriorityQueue<EntityCandidate> worstFirst;
    private final Set<UUID> ids;

    private BoundedCandidates(int capacity) {
      this.capacity = capacity;
      this.worstFirst = new PriorityQueue<>(Math.min(capacity, 1024), WORST_FIRST);
      this.ids = new HashSet<>(Math.min(capacity, 1024));
    }

    private void offer(List<EntityCandidate> offered) {
      for (EntityCandidate candidate : offered) {
        offer(candidate);
      }
    }

    private void offer(EntityCandidate candidate) {
      if (candidate == null || !ids.add(candidate.id)) {
        return;
      }
      if (worstFirst.size() < capacity) {
        worstFirst.offer(candidate);
        return;
      }

      EntityCandidate worst = worstFirst.peek();
      if (worst == null || candidate.priority >= worst.priority) {
        ids.remove(candidate.id);
        return;
      }

      worstFirst.poll();
      ids.remove(worst.id);
      worstFirst.offer(candidate);
    }

    private List<EntityCandidate> sorted() {
      List<EntityCandidate> sorted = new ArrayList<>(worstFirst);
      sorted.sort(Comparator.comparingDouble(EntityCandidate::priority));
      return sorted;
    }
  }

  private enum Scope {
    PLAYER,
    CHUNK,
    WORLD
  }

  private record ScopeKey(Scope scope, UUID id, int x, int z) {
    private static ScopeKey player(UUID playerId) {
      return new ScopeKey(Scope.PLAYER, playerId, 0, 0);
    }

    private static ScopeKey world(UUID worldId) {
      return new ScopeKey(Scope.WORLD, worldId, 0, 0);
    }

    private static ScopeKey chunk(ChunkKey chunk) {
      return new ScopeKey(Scope.CHUNK, chunk.world, chunk.x, chunk.z);
    }
  }

  private static final class ChunkKey {
    @art.arcane.react.util.project.config.ConfigDoc(value = "World identifier used by entity trimmer internal tracking.", impact = "This is runtime identity data and should normally be left to automatic updates.")
    private final UUID world;
    @art.arcane.react.util.project.config.ConfigDoc(value = "X-axis coordinate used by entity trimmer internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Z-axis coordinate used by entity trimmer internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int z;

    private ChunkKey(UUID world, int x, int z) {
      this.world = world;
      this.x = x;
      this.z = z;
    }

    private static ChunkKey of(World world, int x, int z) {
      return new ChunkKey(world.getUID(), x, z);
    }

    private static ChunkKey of(Location location) {
      World world = location.getWorld();
      if (world == null) {
        throw new IllegalArgumentException("Entity location has no world");
      }
      return of(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      if (!(obj instanceof ChunkKey key)) {
        return false;
      }

      return x == key.x && z == key.z && world.equals(key.world);
    }

    @Override
    public int hashCode() {
      int result = world.hashCode();
      result = 31 * result + x;
      result = 31 * result + z;
      return result;
    }
  }
}
