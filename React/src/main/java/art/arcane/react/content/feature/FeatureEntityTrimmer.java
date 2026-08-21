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
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.CustomMobChecker;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactProtection;
import art.arcane.react.api.protect.internal.ProtectionGuards;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Entity Trimmer feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureEntityTrimmer extends ReactFeature implements Listener {
  public static final String ID = "entity-trimmer";
  private transient double maxPriority = -1;
  private transient int cooldown = 0;
  private transient boolean trimQueued = false;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips skip custom mobs when entity trimmer evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean skipCustomMobs = false;
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
    trimQueued = false;
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

    maxPriority = M.lerp(Math.max(lowestPriority, 0), highestPriority, priorityPercentCutoff);
    React.verbose("Entity Trimmer Priority Cutoff: " + maxPriority + " or lower");
  }

  @Override
  public void onDeactivate() {

  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    if (cooldown-- > 0 || trimQueued) {
      return;
    }

    if (J.isFoliaThreading()) {
      trimEntitiesFolia();
      return;
    }

    trimQueued = true;
    J.s(() -> {
      try {
        trimEntities();
      } finally {
        trimQueued = false;
      }
    });
  }

  private void trimEntities() {
    long totalEntities = 0L;
    for (World world : Bukkit.getWorlds()) {
      totalEntities += WorldEntitySnapshots.count(world);
    }

    if (totalEntities * opporunityThreshold < minKillBatchSize) {
      return;
    }

    List<Entity> entities = collectEntities();
    if (entities.isEmpty()) {
      return;
    }

    int chunkRadius = Math.max(0, (playerMobBlockDistance + 15) >> 4);
    long maxPlayerDistanceSquared = (long) playerMobBlockDistance * playerMobBlockDistance;
    Map<ChunkKey, List<PlayerSnapshot>> playersByChunk = indexPlayersByChunk(chunkRadius);

    Map<ChunkKey, List<Entity>> entitiesPerChunk = new HashMap<>();
    Map<World, List<Entity>> entitiesPerWorld = new HashMap<>();
    Map<Player, List<Entity>> entitiesPerPlayer = new HashMap<>();
    Map<Entity, Double> priorityCache = new IdentityHashMap<>(entities.size());

    for (Entity entity : entities) {
      if (entity == null || entity.isDead()) {
        continue;
      }

      Location location = entity.getLocation();
      World world = location.getWorld();
      if (world == null) {
        continue;
      }

      ChunkKey key = ChunkKey.of(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
      entitiesPerChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
      entitiesPerWorld.computeIfAbsent(world, k -> new ArrayList<>()).add(entity);

      List<PlayerSnapshot> nearbyPlayers = playersByChunk.get(key);
      if (nearbyPlayers == null || nearbyPlayers.isEmpty()) {
        continue;
      }

      for (PlayerSnapshot playerSnapshot : nearbyPlayers) {
        if (playerSnapshot.location.distanceSquared(location) <= maxPlayerDistanceSquared) {
          entitiesPerPlayer.computeIfAbsent(playerSnapshot.player, k -> new ArrayList<>()).add(entity);
        }
      }
    }

    Comparator<Entity> byPriority = Comparator.comparingDouble(entity -> priorityOf(entity, priorityCache));
    Set<Entity> candidates = new LinkedHashSet<>();
    addEntitiesToRemove(candidates, entitiesPerChunk.values(), softMaxEntitiesPerChunk, byPriority);
    addEntitiesToRemove(candidates, entitiesPerPlayer.values(), softMaxEntitiesPerPlayer, byPriority);
    addEntitiesToRemove(candidates, entitiesPerWorld.values(), softMaxEntitiesPerWorld, byPriority);

    if (candidates.isEmpty()) {
      return;
    }

    List<EntityCandidate> killList = new ArrayList<>(candidates.size());
    for (Entity entity : candidates) {
      if (!isValidTarget(entity)) {
        continue;
      }

      double priority = priorityOf(entity, priorityCache);
      if (priority < 0 || priority > maxPriority) {
        continue;
      }

      killList.add(new EntityCandidate(entity, priority));
    }

    if (killList.isEmpty()) {
      return;
    }

    killList.sort(Comparator.comparingDouble(EntityCandidate::priority));
    int maxKill = Math.min(killList.size(), (int) (killList.size() * opporunityThreshold));

    if (maxKill < minKillBatchSize) {
      cooldown += 3;
      return;
    }

    for (int i = 0; i < maxKill; i++) {
      kill(killList.get(i).entity);
    }

    if (printEntityPurgeSuccess) {
      React.success("Entity Trimmer: " + maxKill + " entities removed");
    }
  }

  private void trimEntitiesFolia() {
    trimQueued = true;
    try {
      List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
      if (players.isEmpty()) {
        return;
      }

      ThreadLocalRandom random = ThreadLocalRandom.current();
      int sampleCount = Math.min(players.size(), Math.max(1, Math.min(24, softMaxEntitiesPerWorld / Math.max(1, softMaxEntitiesPerPlayer))));
      for (int i = 0; i < sampleCount; i++) {
        Player player = players.get(random.nextInt(players.size()));
        J.runEntity(player, () -> trimAroundPlayer(player));
      }
    } finally {
      trimQueued = false;
    }
  }

  private void trimAroundPlayer(Player player) {
    if (player == null || !player.isOnline() || !J.isOwnedByCurrentRegion(player)) {
      return;
    }

    int radius = Math.max(8, playerMobBlockDistance);
    List<Entity> nearby = player.getNearbyEntities(radius, Math.max(24, radius), radius);
    if (nearby.isEmpty()) {
      return;
    }

    List<EntityCandidate> killList = new ArrayList<>();
    for (Entity entity : nearby) {
      if (entity == null || !J.isOwnedByCurrentRegion(entity) || !isValidTarget(entity)) {
        continue;
      }

      double priority = ReactEntity.getPriority(entity);
      if (priority < 0D || priority > maxPriority) {
        continue;
      }

      killList.add(new EntityCandidate(entity, priority));
    }

    int softCap = Math.max(1, softMaxEntitiesPerPlayer);
    if (killList.size() <= softCap) {
      return;
    }

    killList.sort(Comparator.comparingDouble(EntityCandidate::priority));
    int overflow = killList.size() - softCap;
    int maxKill = Math.max(1, (int) Math.floor(killList.size() * opporunityThreshold));
    maxKill = Math.min(maxKill, overflow);
    for (int i = 0; i < maxKill; i++) {
      kill(killList.get(i).entity);
    }
  }

  private List<Entity> collectEntities() {
    List<Entity> entities = new ArrayList<>();
    for (World world : Bukkit.getWorlds()) {
      entities.addAll(WorldEntitySnapshots.get(world));
    }

    return entities;
  }

  private Map<ChunkKey, List<PlayerSnapshot>> indexPlayersByChunk(int chunkRadius) {
    Map<ChunkKey, List<PlayerSnapshot>> chunks = new HashMap<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      Location playerLocation = player.getLocation();
      World world = playerLocation.getWorld();
      if (world == null) {
        continue;
      }

      PlayerSnapshot snapshot = new PlayerSnapshot(player, playerLocation.clone());
      int playerChunkX = playerLocation.getBlockX() >> 4;
      int playerChunkZ = playerLocation.getBlockZ() >> 4;

      for (int x = playerChunkX - chunkRadius; x <= playerChunkX + chunkRadius; x++) {
        for (int z = playerChunkZ - chunkRadius; z <= playerChunkZ + chunkRadius; z++) {
          chunks.computeIfAbsent(ChunkKey.of(world, x, z), k -> new ArrayList<>()).add(snapshot);
        }
      }
    }

    return chunks;
  }

  private boolean isValidTarget(Entity entity) {
    if (entity == null || entity.isDead()) {
      return false;
    }

    if (entity.getTicksLived() < 400) {
      return false;
    }

    if (blacklist.contains(entity.getType())) {
      return false;
    }

    if (ReactProtection.isProtected(entity, ReactOperation.TRIM)) {
      return false;
    }

    return !skipCustomMobs || !CustomMobChecker.isCustom(entity);
  }

  private double priorityOf(Entity entity, Map<Entity, Double> cache) {
    return cache.computeIfAbsent(entity, ReactEntity::getPriority);
  }

  private void addEntitiesToRemove(Set<Entity> entitiesToRemove, Collection<List<Entity>> groups, int maxEntities, Comparator<Entity> byPriority) {
    if (maxEntities < 0) {
      return;
    }

    for (List<Entity> entities : groups) {
      if (entities.size() <= maxEntities) {
        continue;
      }

      entities.sort(byPriority);
      for (int i = maxEntities; i < entities.size(); i++) {
        entitiesToRemove.add(entities.get(i));
      }
    }
  }

  private void kill(Entity entity) {
    if (!ProtectionGuards.allows(entity, ReactOperation.TRIM)) {
      return;
    }

    int delay = ThreadLocalRandom.current().nextInt(20);
    if (!J.runEntity(entity, () -> React.kill(entity), delay)) {
      React.kill(entity);
    }
  }

  private static final class PlayerSnapshot {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Runtime reference field for player used by entity trimmer.", impact = "This value is typically populated from live game objects and not intended for manual editing.")
    private final Player player;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Runtime reference field for location used by entity trimmer.", impact = "This value is typically populated from live game objects and not intended for manual editing.")
    private final Location location;

    private PlayerSnapshot(Player player, Location location) {
      this.player = player;
      this.location = location;
    }
  }

  private static final class EntityCandidate {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Runtime reference field for entity used by entity trimmer.", impact = "This value is typically populated from live game objects and not intended for manual editing.")
    private final Entity entity;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Priority value used when entity trimmer orders targets.", impact = "Higher values move entries earlier in processing; lower values push them later.")
    private final double priority;

    private EntityCandidate(Entity entity, double priority) {
      this.entity = entity;
      this.priority = priority;
    }

    private double priority() {
      return priority;
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
