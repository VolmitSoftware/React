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

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.PlayerPosition;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private transient final AtomicInteger anchorRotation = new AtomicInteger(0);
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
  private List<String> blacklist = List.of(
      "ITEM_DISPLAY", "PLAYER", "ARMOR_STAND", "ITEM_FRAME", "PAINTING", "LEASH_KNOT",
      "MINECART", "CHEST_MINECART", "COMMAND_BLOCK_MINECART", "FURNACE_MINECART",
      "HOPPER_MINECART", "SPAWNER_MINECART", "TNT_MINECART",
      "ACACIA_BOAT", "BAMBOO_RAFT", "BIRCH_BOAT", "CHERRY_BOAT", "DARK_OAK_BOAT",
      "JUNGLE_BOAT", "MANGROVE_BOAT", "OAK_BOAT", "PALE_OAK_BOAT", "SPRUCE_BOAT",
      "ACACIA_CHEST_BOAT", "BAMBOO_CHEST_RAFT", "BIRCH_CHEST_BOAT", "CHERRY_CHEST_BOAT",
      "DARK_OAK_CHEST_BOAT", "JUNGLE_CHEST_BOAT", "MANGROVE_CHEST_BOAT", "OAK_CHEST_BOAT",
      "PALE_OAK_CHEST_BOAT", "SPRUCE_CHEST_BOAT",
      "FALLING_BLOCK", "ITEM", "EXPERIENCE_ORB", "FISHING_BOBBER",
      "TNT", "SPLASH_POTION", "EXPERIENCE_BOTTLE", "ENDER_PEARL",
      "EYE_OF_ENDER", "FIREWORK_ROCKET", "LIGHTNING_BOLT", "SHULKER_BULLET",
      "SMALL_FIREBALL", "SNOWBALL", "SPECTRAL_ARROW", "SPLASH_POTION",
      "EXPERIENCE_BOTTLE");
  private transient volatile Set<String> blacklistedTypes = Set.of();
  private transient volatile WorldHost worlds;
  private transient volatile EntityHost entities;

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
      anchorRotation.set(0);
      nextEntity.set(0);
      ReactPlatform platform = ReactPlatforms.get();
      WorldHost worldHost = platform.worldHost().orElse(null);
      EntityHost entityHost = platform.entityHost().orElse(null);
      if (worldHost == null || entityHost == null) {
        platform.warn("Entity Trimmer needs entity access that " + platform.platformName() + " does not provide; it stays idle.");
        return;
      }
      worlds = worldHost;
      entities = entityHost;
      Set<String> configuredBlacklist = new HashSet<>();
      if (blacklist != null) {
        for (String entityType : blacklist) {
          if (entityType != null && !entityType.isBlank()) {
            configuredBlacklist.add(entityType.trim());
          }
        }
      }
      blacklistedTypes = Set.copyOf(configuredBlacklist);
      double highestPriority = -1;
      double lowestPriority = Double.MAX_VALUE;

      Collection<String> types = entityHost.entityTypes();
      for (String entityType : types) {
        double priority = entityHost.typePriority(entityType);
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
      platform.verbose("Entity Trimmer Priority Cutoff: " + maxPriority + " or lower");
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
        reportError(throwable);
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

    WorldHost presence = worlds();
    if (presence == null || entities() == null) {
      trimQueued.set(false);
      return;
    }
    ReactPlayer[] players = presence.onlinePlayers().toArray(ReactPlayer[]::new);
    if (!isCurrent(generation) || players.length == 0) {
      trimQueued.set(false);
      return;
    }

    List<ReactPlayer> anchors = selectAnchors(players);
    if (folia) {
      ScanFlight flight = new ScanFlight(generation, anchors.size());
      for (ReactPlayer player : anchors) {
        flight.scheduleScan(player);
      }
      return;
    }

    ScanFlight flight = new ScanFlight(generation, 1);
    JobBatch batch = new JobBatch(JobBatch.scanLimits(), flight::completeScan);
    try {
      for (ReactPlayer player : anchors) {
        batch.submit(() -> flight.scanAnchor(player));
      }
    } finally {
      batch.seal();
    }
  }

  private List<ReactPlayer> selectAnchors(ReactPlayer[] players) {
    int start = Math.floorMod(nextAnchor.get(), players.length);
    int rotation = anchorRotation.getAndIncrement();
    List<List<ReactPlayer>> groups = new ArrayList<>(Math.min(players.length, MAX_ANCHORS_PER_CYCLE));
    Map<AnchorCell, List<ReactPlayer>> groupsByCell = new HashMap<>();
    int visited = 0;
    while (visited < players.length && groups.size() < MAX_ANCHORS_PER_CYCLE) {
      ReactPlayer player = players[(start + visited) % players.length];
      visited++;
      AnchorCell cell = anchorCell(player);
      List<ReactPlayer> group = cell == null ? null : groupsByCell.get(cell);
      if (group == null) {
        group = new ArrayList<>(2);
        groups.add(group);
        if (cell != null) {
          groupsByCell.put(cell, group);
        }
      }
      group.add(player);
    }
    nextAnchor.addAndGet(visited);
    List<ReactPlayer> anchors = new ArrayList<>(groups.size());
    for (List<ReactPlayer> group : groups) {
      anchors.add(group.get(Math.floorMod(rotation, group.size())));
    }
    return anchors;
  }

  private AnchorCell anchorCell(ReactPlayer player) {
    if (player == null) {
      return null;
    }
    Optional<PlayerPosition> snapshot = worlds().indexedPlayer(player.id());
    if (snapshot.isEmpty()) {
      return null;
    }
    PlayerPosition view = snapshot.get();
    return new AnchorCell(
        view.worldId(),
        (int) Math.floor(view.x()) >> 4,
        (int) Math.floor(view.y()) >> 4,
        (int) Math.floor(view.z()) >> 4
    );
  }

  private void scanAroundPlayer(ReactPlayer player, ScanAccumulator accumulator, long generation, boolean folia) {
    if (!isCurrent(generation) || player == null || !worlds().isOnline(player)) {
      return;
    }
    if (folia && !J.isOwnedByCurrentRegion(player.handle())) {
      return;
    }

    int radius = Math.max(0, playerMobBlockDistance);
    GameLocation playerLocation = worlds().location(player).orElse(null);
    if (playerLocation == null || playerLocation.worldId() == null) {
      return;
    }
    UUID world = playerLocation.worldId();

    List<GameEntity> nearby = entities().nearby(player, radius);
    if (nearby == null || nearby.isEmpty()) {
      return;
    }

    double radiusSquared = (double) radius * radius;
    ObservedEntities observed = observeEntities(nearby, playerLocation, radiusSquared, folia, generation);
    if (!isCurrent(generation) || observed.count == 0) {
      return;
    }

    List<EntityCandidate> candidates = inspectCandidates(observed, generation);
    if (candidates.isEmpty()) {
      return;
    }

    int playerOverflow = overflow(observed.count, softMaxEntitiesPerPlayer);
    accumulator.record(ScopeKey.player(player.id()), playerOverflow, candidates);

    int worldOverflow = overflow(entities().worldEntityCount(world), softMaxEntitiesPerWorld);
    accumulator.record(ScopeKey.world(world), worldOverflow, candidates);

    Map<ChunkKey, List<EntityCandidate>> candidatesByChunk = new HashMap<>();
    for (EntityCandidate candidate : candidates) {
      candidatesByChunk.computeIfAbsent(candidate.chunk, ignored -> new ArrayList<>()).add(candidate);
    }
    for (Map.Entry<ChunkKey, Integer> entry : observed.chunkCounts.entrySet()) {
      List<EntityCandidate> chunkCandidates = candidatesByChunk.get(entry.getKey());
      int chunkCount = exactChunkCount(accumulator, world, entry.getKey(), chunkCandidates, entry.getValue());
      int chunkOverflow = overflow(chunkCount, softMaxEntitiesPerChunk);
      accumulator.record(ScopeKey.chunk(entry.getKey()), chunkOverflow, chunkCandidates);
    }
  }

  private int exactChunkCount(
      ScanAccumulator accumulator,
      UUID world,
      ChunkKey chunkKey,
      List<EntityCandidate> candidates,
      int observedCount
  ) {
    if (candidates == null || candidates.isEmpty() || softMaxEntitiesPerChunk < 0) {
      return observedCount;
    }

    Integer counted = accumulator.exactChunkCounts.get(chunkKey);
    if (counted == null) {
      int loaded = entities().chunkEntityCount(world, chunkKey.x, chunkKey.z);
      counted = loaded < 0 ? observedCount : loaded;
      accumulator.exactChunkCounts.put(chunkKey, counted);
    }
    return Math.max(observedCount, counted);
  }

  private ObservedEntities observeEntities(
      List<GameEntity> nearby,
      GameLocation playerLocation,
      double radiusSquared,
      boolean folia,
      long generation
  ) {
    ObservedEntities observed = new ObservedEntities(nearby.size());
    for (GameEntity entity : nearby) {
      if (!isCurrent(generation)) {
        return observed;
      }
      if (entity == null || (folia && !J.isOwnedByCurrentRegion(entity.handle()))) {
        continue;
      }

      GameLocation location = entity.location();
      if (!withinRadius(playerLocation, location, radiusSquared)) {
        continue;
      }

      observed.add(entity, ChunkKey.of(location));
    }
    return observed;
  }

  private List<EntityCandidate> inspectCandidates(ObservedEntities observed, long generation) {
    int size = observed.count;
    int start = Math.floorMod(nextEntity.getAndAdd(MAX_INSPECTED_ENTITIES_PER_ANCHOR), size);
    int limit = Math.min(size, MAX_INSPECTED_ENTITIES_PER_ANCHOR);
    List<EntityCandidate> candidates = new ArrayList<>(limit);

    for (int offset = 0; offset < limit; offset++) {
      if (!isCurrent(generation)) {
        return List.of();
      }

      int index = (start + offset) % size;
      GameEntity entity = observed.entities[index];
      UUID entityId = entity.id();
      if (entityId == null || !isValidTarget(entity)) {
        continue;
      }

      double priority = entities().priority(entity);
      if (priority < 0D || priority > maxPriority) {
        continue;
      }

      candidates.add(new EntityCandidate(entity, entityId, priority, observed.chunks[index]));
    }
    return candidates;
  }

  private boolean withinRadius(GameLocation center, GameLocation location, double radiusSquared) {
    return location != null && center.distanceSquared(location) <= radiusSquared;
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

  private boolean isValidTarget(GameEntity entity) {
    if (entity == null || !entity.isValid()) {
      return false;
    }

    if (entity.ticksLived() < 400) {
      return false;
    }

    if (blacklistedTypes.contains(entity.type())) {
      return false;
    }

    if (entities().protectsNamed(entity, protectNamedEntities)) {
      return false;
    }

    if (entities().isProtected(entity, ReactOperation.TRIM)) {
      return false;
    }

    return !skipCustomMobs || !entities().isCustomMob(entity);
  }

  private void scheduleKill(GameEntity entity, ScanFlight flight, long generation) {
    int delay = ThreadLocalRandom.current().nextInt(20);
    flight.scheduleKill(entity, delay, () -> {
      if (!isCurrent(generation)
          || !isValidTarget(entity)
          || !entities().allows(entity, ReactOperation.TRIM)) {
        return;
      }

      lifecycleLock.readLock().lock();
      try {
        if (isCurrent(generation)) {
          entities().remove(entity);
        }
      } finally {
        lifecycleLock.readLock().unlock();
      }
    });
  }

  private boolean isCurrent(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  private WorldHost worlds() {
    WorldHost host = worlds;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.worldHost().orElse(null);
      worlds = host;
    }
    return host;
  }

  private EntityHost entities() {
    EntityHost host = entities;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.entityHost().orElse(null);
      entities = host;
    }
    return host;
  }

  private static void reportError(Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(failure);
    }
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

    private void scheduleScan(ReactPlayer player) {
      AtomicBoolean completed = new AtomicBoolean(false);
      Runnable completion = () -> {
        if (completed.compareAndSet(false, true)) {
          completeScan();
        }
      };
      boolean scheduled = false;
      try {
        scheduled = J.runEntity(player.handle(), () -> {
          try {
            if (isCurrent(generation)) {
              scanAroundPlayer(player, accumulator, generation, true);
            }
          } catch (Throwable throwable) {
            reportError(throwable);
          } finally {
            completion.run();
          }
        }, 0, completion);
      } catch (Throwable throwable) {
        reportError(throwable);
      } finally {
        if (!scheduled) {
          completion.run();
        }
      }
    }

    private void scanAnchor(ReactPlayer player) {
      if (isCurrent(generation)) {
        scanAroundPlayer(player, accumulator, generation, false);
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
        ReactPlatform platform = ReactPlatforms.getOrNull();
        if (platform != null) {
          platform.verbose(() -> "Entity Trimmer: " + maxKill + " entities queued for removal");
        }
      }
      completeKill();
    }

    private void scheduleKill(GameEntity entity, int delay, Runnable work) {
      pendingKills.incrementAndGet();
      AtomicBoolean completed = new AtomicBoolean(false);
      Runnable completion = () -> {
        if (completed.compareAndSet(false, true)) {
          completeKill();
        }
      };
      boolean scheduled = false;
      try {
        scheduled = J.runEntity(entity.handle(), () -> {
          try {
            if (isCurrent(generation)) {
              work.run();
            }
          } catch (Throwable throwable) {
            reportError(throwable);
          } finally {
            completion.run();
          }
        }, delay, completion);
      } catch (Throwable throwable) {
        reportError(throwable);
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
    private final Map<ChunkKey, Integer> exactChunkCounts = new ConcurrentHashMap<>();

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
    private final GameEntity entity;
    private final UUID id;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Priority value used when entity trimmer orders targets.", impact = "Higher values move entries earlier in processing; lower values push them later.")
    private final double priority;
    private final ChunkKey chunk;

    private EntityCandidate(GameEntity entity, UUID id, double priority, ChunkKey chunk) {
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

  private static final class ObservedEntities {
    private final GameEntity[] entities;
    private final ChunkKey[] chunks;
    private final Map<ChunkKey, Integer> chunkCounts = new HashMap<>();
    private int count;

    private ObservedEntities(int capacity) {
      entities = new GameEntity[capacity];
      chunks = new ChunkKey[capacity];
    }

    private void add(GameEntity entity, ChunkKey chunk) {
      entities[count] = entity;
      chunks[count] = chunk;
      count++;
      chunkCounts.merge(chunk, 1, Integer::sum);
    }
  }

  private record AnchorCell(UUID worldId, int x, int y, int z) {
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

    private static ChunkKey of(GameLocation location) {
      if (location.worldId() == null) {
        throw new IllegalArgumentException("Entity location has no world");
      }
      return new ChunkKey(location.worldId(), location.chunkX(), location.chunkZ());
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
