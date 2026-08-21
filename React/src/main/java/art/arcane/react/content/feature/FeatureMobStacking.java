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
import art.arcane.react.api.feature.FeatureIntegrityListener;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.core.NMS;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.CubeMobs;
import art.arcane.react.util.project.world.CustomMobChecker;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactOperations;
import art.arcane.react.api.protect.ReactProtection;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Mob Stacking feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureMobStacking extends ReactFeature implements FeatureIntegrityListener {
  static boolean exceedsStackLimit(int intoCount, int sourceCount, int maxStackSize) {
    return intoCount + sourceCount > maxStackSize;
  }

  static boolean withinHealthLimit(double sourceMaxHealth, double intoMaxHealth, double maxHealth) {
    return sourceMaxHealth + intoMaxHealth <= maxHealth;
  }

  static int theoreticalMaxStackCount(double maxHealth, double entityMaxHealth, int maxStackSize) {
    return Math.min((int) Math.ceil(Math.floor(maxHealth / entityMaxHealth)), maxStackSize);
  }

  static long packChunkKey(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
  }

  static int chunkKeyX(long key) {
    return (int) (key >> 32);
  }

  static int chunkKeyZ(long key) {
    return (int) key;
  }

  static boolean withinMergeRadius(double ax, double ay, double az, double bx, double by, double bz, double radius) {
    return Math.abs(ax - bx) <= radius && Math.abs(ay - by) <= radius && Math.abs(az - bz) <= radius;
  }

  static boolean sameCubeSize(Entity source, Entity target) {
    if (CubeMobs.isCubeMob(source)) {
      return CubeMobs.isCubeMob(target) && CubeMobs.getSize(source) == CubeMobs.getSize(target);
    }

    return !CubeMobs.isCubeMob(target);
  }

  static boolean isTamedPet(Entity entity) {
    return entity instanceof Tameable tameable && tameable.isTamed();
  }

  public static final String ID = "mob-stacking";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum stack size allowed by mob stacking.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private int maxStackSize = 10;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum health allowed by mob stacking.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxHealth = 100;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Filter definition for stackable types used by mob stacking.", impact = "Narrow this list to target fewer cases, or broaden it to include more matching entries.")
  private Set<EntityType> stackableTypes = defaultStackableTypes();
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether mob stacking applies custom names.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean customNames = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Search radius used by mob stacking (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double searchRadius = 6;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether mob stacking applies vacuum effect.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean vacuumEffect = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips skip custom mobs when mob stacking evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean skipCustomMobs = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether mob stacking applies only spawner mobs.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean onlySpawnerMobs = false;
  @art.arcane.react.util.project.config.ConfigDoc(value = "How often queued chunks are processed for stacking, in milliseconds.", impact = "Lower values stack freshly spawned mobs sooner at more scheduling cost; higher values batch more merge work per pass.")
  private int batchIntervalMs = 250;

  private transient final Map<EntityType, String> formattedBaseNames = new ConcurrentHashMap<>();
  private transient final Map<UUID, Set<Long>> dirtyChunks = new ConcurrentHashMap<>();
  private transient final Consumer<Entity> entityTickListener = this::onTick;
  private transient volatile boolean active;
  private transient SamplerEntities entitiesSampler;
  private transient volatile StackableIndex stackableIndex;

  public FeatureMobStacking() {
    super(ID);
  }

  public static Set<EntityType> defaultStackableTypes() {
    Set<EntityType> e = new HashSet<>();

    for (EntityType i : EntityType.values()) {
      if (i.isAlive() && i.isSpawnable()) {
        e.add(i);
      }
    }

    e.remove(EntityType.PLAYER);
    e.remove(EntityType.ARMOR_STAND);
    e.remove(EntityType.VILLAGER);
    e.remove(EntityType.WANDERING_TRADER);
    e.remove(EntityType.FALLING_BLOCK);

    return e;
  }

  @Override
  public void onActivate() {
    active = true;
    dirtyChunks.clear();
    rebuildStackableIndex();
    for (EntityType i : stackableTypes) {
      React.controller(EntityController.class).registerEntityTickListener(i, entityTickListener);
    }
  }

  public boolean isStackableType(EntityType type) {
    if (type == null) {
      return false;
    }

    StackableIndex index = stackableIndex;
    if (index == null || index.source() != stackableTypes) {
      index = rebuildStackableIndex();
    }

    return index.types().contains(type);
  }

  private synchronized StackableIndex rebuildStackableIndex() {
    Set<EntityType> configured = stackableTypes;
    EnumSet<EntityType> types = EnumSet.noneOf(EntityType.class);
    if (configured != null) {
      for (EntityType type : configured) {
        if (type != null) {
          types.add(type);
        }
      }
    }

    StackableIndex index = new StackableIndex(configured, types);
    stackableIndex = index;
    return index;
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onEntityTame(EntityTameEvent event) {
    LivingEntity entity = event.getEntity();
    if (getStackCount(entity) <= 1) {
      return;
    }

    UUID ownerId = event.getOwner().getUniqueId();
    J.runEntity(entity, () -> completeTamedStackSplit(entity, ownerId), 1);
  }

  @EventHandler
  public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
    // Check for sneak and right click
    if (event.getPlayer().isSneaking() && event.getHand().equals(EquipmentSlot.HAND)) {
      Entity clickedEntity = event.getRightClicked();


      // Check if entity is stackable and has more than 1 in the stack
      if (isStackableType(clickedEntity.getType()) && getStackCount(clickedEntity) > 1) {
        // Calculate new stack counts for split entities
        int newStackCount = getStackCount(clickedEntity) / 2;
        int remainingStackCount = getStackCount(clickedEntity) - newStackCount;

        // Create new entity with half of the original stack count
        LivingEntity newEntity;
        if (clickedEntity instanceof Sheep) {
          Sheep oldSheep = (Sheep) clickedEntity;
          newEntity = (LivingEntity) clickedEntity.getWorld().spawnEntity(clickedEntity.getLocation().add(0, 0.5, 0), clickedEntity.getType());
          ((Sheep) newEntity).setColor(oldSheep.getColor()); // setting the new sheep color
        } else if (CubeMobs.isCubeMob(clickedEntity)) {
          newEntity = (LivingEntity) clickedEntity.getWorld().spawnEntity(clickedEntity.getLocation().add(0, 0.5, 0), clickedEntity.getType());
          int oldSize = CubeMobs.getSize(clickedEntity);
          if (oldSize > 1) { // This is to ensure no infinite loop of cube mob spawning
            CubeMobs.setSize(newEntity, oldSize / 2); // setting the new size
          } else {
            CubeMobs.setSize(newEntity, oldSize);
          }
        } else {
          newEntity = (LivingEntity) clickedEntity.getWorld().spawnEntity(clickedEntity.getLocation().add(0, 0.5, 0), clickedEntity.getType());
        }
        setStackCount(newEntity, newStackCount);
        newEntity.setMetadata("DoNotStack", new FixedMetadataValue(React.instance, true));
        newEntity.setMetadata("UniqueMobStack", new FixedMetadataValue(React.instance, true));
        updateEntityCustomName(newEntity);

        // Update original entity with the remaining stack count
        setStackCount(clickedEntity, remainingStackCount);
        clickedEntity.setMetadata("DoNotStack", new FixedMetadataValue(React.instance, true));
        clickedEntity.setMetadata("UniqueMobStack", new FixedMetadataValue(React.instance, true));
        updateEntityCustomName(clickedEntity);
      }
    }
  }

  // Method to update entity custom name based on its stack count
  public void updateEntityCustomName(Entity e) {
    int count = getStackCount(e);
    if (count > 1) {
      e.setCustomName(ChatColor.BOLD + "" + count + "x " + ChatColor.RESET + ChatColor.GRAY + "" + Form.capitalizeWords(e.getType().name().toLowerCase().replaceAll("\\Q_\\E", " ")));
    } else {
      String uniqueLabel = ReactLanguage.plain(RuntimeMessages.MOB_STACKING_UNIQUE);
      e.setCustomName(ChatColor.GOLD + "" + count + "x " + uniqueLabel + ChatColor.RESET + ChatColor.GRAY + "" + Form.capitalizeWords(e.getType().name().toLowerCase().replaceAll("\\Q_\\E", " ")));
    }
  }


  // prevent the spam in the console that happens when a mob is killed by non-living damage
  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onEntityDeath(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    int currentStack = getStackCount(entity);

    if (currentStack > 1) {
      spawnReplacement(entity, currentStack - 1);
    }

    if (currentStack > 1 || entity.hasMetadata("UniqueMobStack")) {
      entity.setCustomName(null);
    }
  }

  private boolean spawnReplacement(LivingEntity source, int nextStackCount) {
    if (nextStackCount <= 0 || source.getWorld() == null) {
      return false;
    }

    Location spawnLocation = source.getLocation();
    Entity created = source.getWorld().spawnEntity(spawnLocation, source.getType());
    if (!(created instanceof LivingEntity replacement)) {
      created.remove();
      return false;
    }

    copyState(source, replacement);
    if (replacement instanceof Tameable tameable) {
      tameable.setOwner(null);
      tameable.setTamed(false);
    }
    setStackCount(replacement, nextStackCount);
    return true;
  }

  private void completeTamedStackSplit(LivingEntity entity, UUID ownerId) {
    if (!entity.isValid() || entity.isDead() || !isTamedPet(entity)) {
      return;
    }

    Tameable tameable = (Tameable) entity;
    if (!ownerId.equals(resolveOwnerId(tameable))) {
      return;
    }

    splitTamedStack(entity);
  }

  // Tameable#getOwnerUniqueId is paper-only; probe once, fall back to getOwner on spigot
  private static volatile boolean ownerUniqueIdUnsupported;

  private static UUID resolveOwnerId(Tameable tameable) {
    if (!ownerUniqueIdUnsupported) {
      try {
        return tameable.getOwnerUniqueId();
      } catch (NoSuchMethodError e) {
        ownerUniqueIdUnsupported = true;
      }
    }

    AnimalTamer owner = tameable.getOwner();
    return owner == null ? null : owner.getUniqueId();
  }

  private boolean splitTamedStack(LivingEntity entity) {
    int currentStack = getStackCount(entity);
    if (currentStack <= 1 || !spawnReplacement(entity, currentStack - 1)) {
      return false;
    }

    setStackCount(entity, 1);
    return true;
  }

  private void copyState(LivingEntity source, LivingEntity target) {
    if (CubeMobs.isCubeMob(source) && CubeMobs.isCubeMob(target)) {
      CubeMobs.setSize(target, Math.max(1, CubeMobs.getSize(source)));
    }

    if (source instanceof Sheep sourceSheep && target instanceof Sheep targetSheep) {
      targetSheep.setColor(sourceSheep.getColor());
    }

    if (source instanceof Ageable sourceAgeable && target instanceof Ageable targetAgeable) {
      if (sourceAgeable.isAdult()) {
        targetAgeable.setAdult();
      } else {
        targetAgeable.setBaby();
      }
    }

    target.setAI(source.hasAI());
    target.setCollidable(source.isCollidable());
    target.setCustomNameVisible(source.isCustomNameVisible());
    target.setGlowing(source.isGlowing());
    target.setGravity(source.hasGravity());
    target.setInvulnerable(source.isInvulnerable());
    target.setSilent(source.isSilent());
    target.setRemoveWhenFarAway(source.getRemoveWhenFarAway());
    target.setPersistent(source.isPersistent());
    target.setCanPickupItems(source.getCanPickupItems());
    target.setFireTicks(source.getFireTicks());
    target.setPortalCooldown(source.getPortalCooldown());
    target.setVelocity(source.getVelocity());
    target.setFallDistance(source.getFallDistance());
    target.setRemainingAir(source.getRemainingAir());
    target.setNoDamageTicks(source.getNoDamageTicks());

    if (source.hasMetadata("SpawnedBySpawner")) {
      target.setMetadata("SpawnedBySpawner", new FixedMetadataValue(React.instance, true));
    }

    if (source.hasMetadata("DoNotStack")) {
      target.setMetadata("DoNotStack", new FixedMetadataValue(React.instance, true));
    }

    if (source.hasMetadata("UniqueMobStack")) {
      target.setMetadata("UniqueMobStack", new FixedMetadataValue(React.instance, true));
    }
  }


  public boolean merge(Entity a, Entity into) {
    if (ReactProtection.isProtected(a, ReactOperation.STACK)
        || ReactProtection.isProtected(into, ReactOperation.STACK)) {
      return false;
    }

    if (active && canMerge(a, into)) {
      setStackCount(into, getStackCount(into) + getStackCount(a));
      if (vacuumEffect) {
        NMS.sendCollectPacket(a, 64, a.getEntityId(), into.getEntityId(), 1);
      }
      a.remove();
      if (entitiesSampler == null) {
        entitiesSampler = React.sampler(SamplerEntities.ID);
      }
      if (entitiesSampler != null) {
        entitiesSampler.getEntities().decrementAndGet();
      }
      return true;
    }

    return false;
  }

  public boolean canMerge(Entity a, Entity into) {
    // Check if entities are the same literal entity
    if (a.getEntityId() == into.getEntityId()) {
      return false;
    }

    // Check if entities are the same type
    if (a.getType() != into.getType()) {
      return false;
    }

    if (isTamedPet(a) || isTamedPet(into)) {
      return false;
    }

    // types that can stack
    if (!isStackableType(a.getType())) {
      return false;
    }

    // Check if entities == living entities
    if (!(a instanceof LivingEntity la)) {
      return false;
    }

    // Check if entities are a player
    if (a instanceof Player || into instanceof Player) {
      return false;
    }

    // Check if entities are dead
    if (a.isDead() || into.isDead()) {
      return false;
    }

    // Check if cube mob sizes match
    if (!sameCubeSize(a, into)) {
      return false;
    }

    // Check if entities are ageable and if both are adults or babies
    if ((a instanceof Ageable && into instanceof Ageable)) {
      if (((Ageable) a).isAdult() != ((Ageable) into).isAdult()) {
        return false;
      }
    }

    // Check if entities are Sheep and if their color matches
    if ((a instanceof Sheep && into instanceof Sheep)) {
      if (((Sheep) a).getColor() != ((Sheep) into).getColor()) {
        return false;
      }
    }

    // Check if entities are Villagers and if their professions match
    if ((a instanceof Villager && into instanceof Villager)) {
      if (((Villager) a).getProfession() != ((Villager) into).getProfession()) {
        return false;
      }
    }

    // Check if entities are stackable via config
    if (skipCustomMobs && (CustomMobChecker.isCustom(a) || CustomMobChecker.isCustom(into))) {
      return false;
    }

    // Check if entities are marked as non-stackable
    if (a.hasMetadata("DoNotStack") || into.hasMetadata("DoNotStack")) {
      return false;
    }

    // Check if entities are stackable via spawn reason
    if (onlySpawnerMobs && (!a.hasMetadata("SpawnedBySpawner") || !into.hasMetadata("SpawnedBySpawner"))) {
      return false;
    }

    // Check stack count
    if (exceedsStackLimit(getStackCount(into), getStackCount(a), maxStackSize)) {
      return false;
    }

    // Check health
    if (into instanceof LivingEntity li) {
      return withinHealthLimit(la.getAttribute(Attribute.MAX_HEALTH).getValue(), li.getAttribute(Attribute.MAX_HEALTH).getValue(), maxHealth);
    }

    return true;
  }


  public int getTheoreticalMaxStackCount(Entity entityAsType) {
    if (entityAsType instanceof LivingEntity le) {
      return theoreticalMaxStackCount(maxHealth, le.getAttribute(Attribute.MAX_HEALTH).getValue(), maxStackSize);
    }

    return maxStackSize;
  }

  public void setStackCount(Entity e, int i) {
    ReactEntity.setStackCount(e, i);

    if (customNames) {
      if (i > 1) {
        String baseName = formattedBaseNames.computeIfAbsent(e.getType(), t -> Form.capitalizeWords(t.name().toLowerCase().replaceAll("\\Q_\\E", " ")));
        e.setCustomName(ChatColor.BOLD + "" + i + "x " + ChatColor.RESET + ChatColor.GRAY + "" + baseName);
      } else {
        e.setCustomName(null);
      }
    }
  }

  public int getStackCount(Entity e) {
    return ReactEntity.getStackCount(e);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void on(EntitySpawnEvent e) {
    if (active && isStackableType(e.getEntityType())) {
      markDirty(e.getEntity());
    }
  }

  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    if (active && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
      event.getEntity().setMetadata("SpawnedBySpawner", new FixedMetadataValue(React.instance, true));
    }
  }

  public void onTick(Entity entity) {
    if (entity == null || entity.isDead()) {
      return;
    }

    if (entity instanceof LivingEntity living && isTamedPet(entity)) {
      splitTamedStack(living);
      return;
    }

    if (!active) {
      return;
    }

    markDirty(entity);
  }

  private void markDirty(Entity entity) {
    Location location = entity.getLocation();
    World world = location.getWorld();
    if (world == null) {
      return;
    }

    dirtyChunks.computeIfAbsent(world.getUID(), ignored -> ConcurrentHashMap.newKeySet())
        .add(packChunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4));
  }

  @Override
  public void onTick() {
    if (!active) {
      dirtyChunks.clear();
      return;
    }

    if (dirtyChunks.isEmpty()) {
      return;
    }

    List<WorldBatch> batches = new ArrayList<>();
    for (UUID worldId : new ArrayList<>(dirtyChunks.keySet())) {
      Set<Long> keys = dirtyChunks.remove(worldId);
      if (keys == null || keys.isEmpty()) {
        continue;
      }

      World world = Bukkit.getWorld(worldId);
      if (world == null) {
        continue;
      }

      batches.add(new WorldBatch(world, new ArrayList<>(keys)));
    }

    if (batches.isEmpty()) {
      return;
    }

    if (J.isFoliaThreading()) {
      for (WorldBatch batch : batches) {
        for (long key : batch.keys()) {
          int chunkX = chunkKeyX(key);
          int chunkZ = chunkKeyZ(key);
          J.runChunk(batch.world(), chunkX, chunkZ, () -> stackChunk(batch.world(), chunkX, chunkZ));
        }
      }
      return;
    }

    J.s(() -> {
      for (WorldBatch batch : batches) {
        for (long key : batch.keys()) {
          stackChunk(batch.world(), chunkKeyX(key), chunkKeyZ(key));
        }
      }
    });
  }

  private void stackChunk(World world, int chunkX, int chunkZ) {
    if (!active || !world.isChunkLoaded(chunkX, chunkZ)) {
      return;
    }

    double minX = (chunkX << 4) - searchRadius;
    double minZ = (chunkZ << 4) - searchRadius;
    double maxX = (chunkX << 4) + 16 + searchRadius;
    double maxZ = (chunkZ << 4) + 16 + searchRadius;
    BoundingBox box = new BoundingBox(minX, world.getMinHeight(), minZ, maxX, world.getMaxHeight(), maxZ);
    Collection<Entity> candidates = world.getNearbyEntities(box, this::isStackCandidate);
    if (candidates.size() < 2) {
      return;
    }

    boolean folia = J.isFoliaThreading();
    Map<StackBucket, List<Entity>> buckets = new HashMap<>();
    for (Entity entity : candidates) {
      if (folia && !J.isOwnedByCurrentRegion(entity)) {
        continue;
      }

      buckets.computeIfAbsent(bucketOf(entity), ignored -> new ArrayList<>()).add(entity);
    }

    for (List<Entity> bucket : buckets.values()) {
      collapseBucket(bucket);
    }
  }

  private void collapseBucket(List<Entity> bucket) {
    if (bucket.size() < 2) {
      return;
    }

    List<Entity> survivors = new ArrayList<>(bucket.size());
    List<Location> survivorLocations = new ArrayList<>(bucket.size());
    int[] survivorProtection = new int[bucket.size()];
    for (Entity entity : bucket) {
      if (entity.isDead()) {
        continue;
      }

      int protection = ReactProtection.operationsFor(entity);
      Location at = entity.getLocation();
      boolean merged = false;
      if (!ReactOperations.covers(protection, ReactOperation.STACK)) {
        for (int i = 0; i < survivors.size(); i++) {
          if (ReactOperations.covers(survivorProtection[i], ReactOperation.STACK)) {
            continue;
          }

          Entity survivor = survivors.get(i);
          if (survivor.isDead()) {
            continue;
          }

          Location to = survivorLocations.get(i);
          if (!withinMergeRadius(at.getX(), at.getY(), at.getZ(), to.getX(), to.getY(), to.getZ(), searchRadius)) {
            continue;
          }

          if (merge(entity, survivor)) {
            merged = true;
            break;
          }
        }
      }

      if (!merged) {
        survivorProtection[survivors.size()] = protection;
        survivors.add(entity);
        survivorLocations.add(at);
      }
    }
  }

  private boolean isStackCandidate(Entity entity) {
    return entity instanceof LivingEntity
        && !(entity instanceof Player)
        && isStackableType(entity.getType())
        && !entity.isDead()
        && !isTamedPet(entity);
  }

  private static StackBucket bucketOf(Entity entity) {
    int size = CubeMobs.isCubeMob(entity) ? CubeMobs.getSize(entity) : -1;
    boolean adult = !(entity instanceof Ageable ageable) || ageable.isAdult();
    Object color = entity instanceof Sheep sheep ? sheep.getColor() : null;
    Object profession = entity instanceof Villager villager ? villager.getProfession() : null;
    return new StackBucket(entity.getType(), size, adult, color, profession);
  }

  private record StackBucket(EntityType type, int size, boolean adult, Object color, Object profession) {
  }

  private record WorldBatch(World world, List<Long> keys) {
  }

  private record StackableIndex(Set<EntityType> source, EnumSet<EntityType> types) {
  }

  @Override
  public void onDeactivate() {
    active = false;
    EntityController controller = React.controller(EntityController.class);
    if (controller != null) {
      controller.unregisterEntityTickListener(entityTickListener);
    }
    dirtyChunks.clear();
  }

  @Override
  public int getTickInterval() {
    return Math.max(50, batchIntervalMs);
  }
}
