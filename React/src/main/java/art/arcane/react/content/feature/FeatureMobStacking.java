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
import art.arcane.react.core.NMS;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.integration.GlossEntityOverlayIntegration;
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
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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
  private static final NamespacedKey STACK_LABEL_KEY = new NamespacedKey("react", "mob-stack-label");
  private static final long PRESENTATION_REFRESH_MS = 5_000L;
  private static final int MAX_CHUNK_SUBMISSIONS_PER_TICK = 64;
  static final int MAX_CHUNK_INSPECTIONS_PER_TICK = 128;
  private static final int MAX_INDEXED_ENTITIES = 65_536;
  private static final int MAX_NEIGHBOR_CHUNKS_PER_WORK = 64;
  private static final int MAX_NEIGHBOR_ENTITIES_PER_WORK = 256;
  static final int MAX_ENTITIES_PER_CHUNK_CALLBACK = 256;
  static final int MAX_COMPARISONS_PER_CHUNK_CALLBACK = 4096;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum stack size allowed by mob stacking.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private int maxStackSize = 10;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum health allowed by mob stacking.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxHealth = 100;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Filter definition for stackable types used by mob stacking.", impact = "Narrow this list to target fewer cases, or broaden it to include more matching entries.")
  private Set<EntityType> stackableTypes = defaultStackableTypes();
  @art.arcane.react.util.project.config.ConfigDoc(value = "Shows native stack names when Gloss entity overlays are unavailable.", impact = "Gloss receives stack counts regardless of this setting and owns the combined entity overlay when enabled.")
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
  private transient final GlossEntityOverlayIntegration glossEntityOverlays;
  private transient final Map<UUID, Long> presentationRefreshes = new ConcurrentHashMap<>();
  private transient final Map<UUID, Set<Long>> dirtyChunks = new ConcurrentHashMap<>();
  private transient final Map<UUID, Set<Long>> inFlightChunks = new ConcurrentHashMap<>();
  private transient final Map<ChunkWorkKey, ChunkWork> chunkWork = new ConcurrentHashMap<>();
  private transient final Map<UUID, IndexedStackEntity> indexedEntities = new ConcurrentHashMap<>();
  private transient final Map<ChunkWorkKey, Map<UUID, IndexedStackEntity>> indexedChunks = new ConcurrentHashMap<>();
  private transient final Consumer<Entity> entityTickListener = this::onTick;
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient volatile boolean active;
  private transient volatile StackableIndex stackableIndex;

  public FeatureMobStacking() {
    this(new GlossEntityOverlayIntegration());
  }

  FeatureMobStacking(GlossEntityOverlayIntegration glossEntityOverlays) {
    super(ID);
    this.glossEntityOverlays = Objects.requireNonNull(glossEntityOverlays);
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
    lifecycleGeneration.incrementAndGet();
    active = true;
    dirtyChunks.clear();
    inFlightChunks.clear();
    chunkWork.clear();
    indexedEntities.clear();
    indexedChunks.clear();
    presentationRefreshes.clear();
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
    refreshStackPresentation(e, getStackCount(e));
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
      clearStackName(entity, currentStack);
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
    if (hasUserCustomName(source)) {
      target.setCustomName(source.getCustomName());
    }
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

    copyEquipment(source.getEquipment(), target.getEquipment());
    target.addPotionEffects(source.getActivePotionEffects());

    double sourceMaxHealth = source.getMaxHealth();
    if (Double.isFinite(sourceMaxHealth) && sourceMaxHealth > 0D) {
      target.setMaxHealth(sourceMaxHealth);
      target.setHealth(sourceMaxHealth);
    }

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

  private void copyEquipment(EntityEquipment source, EntityEquipment target) {
    if (source == null || target == null) {
      return;
    }

    target.setArmorContents(cloneItems(source.getArmorContents()));
    target.setItemInMainHand(cloneItem(source.getItemInMainHand()));
    target.setItemInOffHand(cloneItem(source.getItemInOffHand()));
    target.setItemInMainHandDropChance(source.getItemInMainHandDropChance());
    target.setItemInOffHandDropChance(source.getItemInOffHandDropChance());
    target.setHelmetDropChance(source.getHelmetDropChance());
    target.setChestplateDropChance(source.getChestplateDropChance());
    target.setLeggingsDropChance(source.getLeggingsDropChance());
    target.setBootsDropChance(source.getBootsDropChance());
  }

  private ItemStack[] cloneItems(ItemStack[] items) {
    ItemStack[] cloned = new ItemStack[items.length];
    for (int i = 0; i < items.length; i++) {
      cloned[i] = cloneItem(items[i]);
    }
    return cloned;
  }

  private ItemStack cloneItem(ItemStack item) {
    return item == null ? null : item.clone();
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
    if (!(a instanceof LivingEntity la) || !(into instanceof LivingEntity li)) {
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

    if (!hasSafeMergeState(la, li)) {
      return false;
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
    return withinHealthLimit(
        la.getAttribute(Attribute.MAX_HEALTH).getValue(),
        li.getAttribute(Attribute.MAX_HEALTH).getValue(),
        maxHealth
    );
  }

  private boolean hasSafeMergeState(LivingEntity source, LivingEntity target) {
    if (hasEquipment(source) || hasEquipment(target)) {
      return false;
    }
    if (!source.getActivePotionEffects().isEmpty() || !target.getActivePotionEffects().isEmpty()) {
      return false;
    }
    if (hasUserCustomName(source) || hasUserCustomName(target)) {
      return false;
    }
    if (isVariantBearingType(source.getType()) || isVariantBearingType(target.getType())) {
      return false;
    }

    AttributeInstance sourceMaxHealth = source.getAttribute(Attribute.MAX_HEALTH);
    AttributeInstance targetMaxHealth = target.getAttribute(Attribute.MAX_HEALTH);
    if (sourceMaxHealth == null || targetMaxHealth == null) {
      return false;
    }
    if (!sourceMaxHealth.getModifiers().isEmpty() || !targetMaxHealth.getModifiers().isEmpty()) {
      return false;
    }
    if (Math.abs(sourceMaxHealth.getValue() - targetMaxHealth.getValue()) > 0.0001D) {
      return false;
    }
    return Math.abs(source.getHealth() - sourceMaxHealth.getValue()) <= 0.0001D
        && Math.abs(target.getHealth() - targetMaxHealth.getValue()) <= 0.0001D;
  }

  private boolean hasEquipment(LivingEntity entity) {
    EntityEquipment equipment = entity.getEquipment();
    if (equipment == null) {
      return false;
    }
    if (isItem(equipment.getItemInMainHand()) || isItem(equipment.getItemInOffHand())) {
      return true;
    }
    for (ItemStack item : equipment.getArmorContents()) {
      if (isItem(item)) {
        return true;
      }
    }
    return false;
  }

  private boolean isItem(ItemStack item) {
    return item != null && item.getType() != Material.AIR && !item.isEmpty();
  }

  private boolean hasUserCustomName(LivingEntity entity) {
    String name = entity.getCustomName();
    return name != null && !name.isBlank() && !ownsStackName(entity, name, getStackCount(entity));
  }

  private boolean isVariantBearingType(EntityType type) {
    return switch (type.name()) {
      case "AXOLOTL", "BEE", "BOGGED", "CAMEL", "CAT", "CHICKEN", "COW", "CREEPER",
           "DONKEY", "ENDER_DRAGON", "ENDERMAN", "FOX", "FROG", "GHAST", "GOAT", "HOGLIN",
           "HORSE", "IRON_GOLEM", "LLAMA", "MOOSHROOM", "MULE",
           "MUSHROOM_COW", "OCELOT", "PANDA", "PARROT", "PHANTOM", "PIG", "PIGLIN",
           "PIGLIN_BRUTE", "RABBIT", "SALMON", "SHEEP", "SKELETON_HORSE",
           "SNIFFER", "SNOW_GOLEM", "STRIDER", "TRADER_LLAMA", "TROPICAL_FISH", "VEX",
           "VILLAGER", "WANDERING_TRADER", "WOLF", "ZOMBIE_HORSE",
           "ZOMBIE_NAUTILUS", "ZOMBIE_VILLAGER", "ZOMBIFIED_PIGLIN" -> true;
      default -> false;
    };
  }


  public int getTheoreticalMaxStackCount(Entity entityAsType) {
    if (entityAsType instanceof LivingEntity le) {
      return theoreticalMaxStackCount(maxHealth, le.getAttribute(Attribute.MAX_HEALTH).getValue(), maxStackSize);
    }

    return maxStackSize;
  }

  public void setStackCount(Entity e, int i) {
    clearStackName(e, getStackCount(e));
    ReactEntity.setStackCount(e, i);
    refreshStackPresentation(e, i);
  }

  void refreshStackPresentation(Entity entity, int count) {
    boolean handled = entity instanceof LivingEntity living && glossEntityOverlays.refresh(living, count);
    String currentName = entity.getCustomName();
    if (handled || !customNames || (count <= 1 && !entity.hasMetadata("UniqueMobStack"))) {
      clearStackName(entity, count);
    } else if (currentName == null || currentName.isBlank() || ownsStackName(entity, currentName, count)) {
      String stackName = nativeStackName(entity, count);
      if (!stackName.equals(currentName)) {
        entity.setCustomName(stackName);
      }
      entity.getPersistentDataContainer().set(STACK_LABEL_KEY, PersistentDataType.STRING, stackName);
    }
    UUID entityId = entity.getUniqueId();
    if (entityId != null) {
      presentationRefreshes.put(entityId, System.currentTimeMillis());
    }
  }

  private void clearStackName(Entity entity, int count) {
    String name = entity.getCustomName();
    if (name == null) {
      return;
    }
    if (ownsStackName(entity, name, count)) {
      entity.setCustomName(null);
    }
    entity.getPersistentDataContainer().remove(STACK_LABEL_KEY);
  }

  private boolean ownsStackName(Entity entity, String name, int count) {
    String storedName = entity.getPersistentDataContainer().get(STACK_LABEL_KEY, PersistentDataType.STRING);
    return name.equals(storedName)
        || ((count > 1 || entity.hasMetadata("UniqueMobStack")) && name.equals(nativeStackName(entity, count)));
  }

  private String nativeStackName(Entity entity, int count) {
    String baseName = formattedBaseNames.computeIfAbsent(entity.getType(),
        type -> Form.capitalizeWords(type.name().toLowerCase().replace('_', ' ')));
    if (count > 1) {
      return ChatColor.BOLD + "" + count + "x " + ChatColor.RESET + ChatColor.GRAY + "" + baseName;
    }
    return ChatColor.GOLD + "" + count + "x " + ReactLanguage.plain(RuntimeMessages.MOB_STACKING_UNIQUE)
        + ChatColor.RESET + ChatColor.GRAY + "" + baseName;
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
    if (entity == null) {
      return;
    }
    if (entity.isDead()) {
      removeIndexed(entity);
      return;
    }

    if (entity instanceof LivingEntity living && isTamedPet(entity)) {
      removeIndexed(entity);
      splitTamedStack(living);
      return;
    }

    if (!active) {
      return;
    }

    if (entity instanceof LivingEntity && (getStackCount(entity) > 1 || entity.hasMetadata("UniqueMobStack"))) {
      Long lastRefresh = presentationRefreshes.get(entity.getUniqueId());
      if (lastRefresh == null || System.currentTimeMillis() - lastRefresh >= PRESENTATION_REFRESH_MS) {
        updateEntityCustomName(entity);
      }
    }
    markDirty(entity);
  }

  private void markDirty(Entity entity) {
    Location location = entity.getLocation();
    World world = location.getWorld();
    if (world == null) {
      return;
    }

    UUID worldId = world.getUID();
    long chunkKey = packChunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    indexEntity(entity, worldId, chunkKey);
    Set<Long> pending = dirtyChunks.computeIfAbsent(worldId, ignored -> ConcurrentHashMap.newKeySet());
    pending.add(chunkKey);
    addCanonicalNeighborAnchors(worldId, chunkKey, pending);
  }

  @EventHandler
  public void on(EntityRemoveEvent event) {
    removeIndexed(event.getEntity());
  }

  private synchronized void indexEntity(Entity entity, UUID worldId, long chunkKey) {
    UUID entityId = entity.getUniqueId();
    IndexedStackEntity previous = indexedEntities.get(entityId);
    ChunkWorkKey workKey = new ChunkWorkKey(worldId, chunkKey);
    if (previous != null && previous.chunkKey().equals(workKey) && previous.reference().get() == entity) {
      return;
    }
    if (previous == null && indexedEntities.size() >= MAX_INDEXED_ENTITIES) {
      return;
    }

    IndexedStackEntity indexed = new IndexedStackEntity(entityId, workKey, new WeakReference<>(entity));
    indexedEntities.put(entityId, indexed);
    if (previous != null && !previous.chunkKey().equals(workKey)) {
      removeIndexedFromChunk(previous);
    }
    indexedChunks.computeIfAbsent(workKey, ignored -> new ConcurrentHashMap<>()).put(entityId, indexed);
  }

  private synchronized void removeIndexed(Entity entity) {
    UUID entityId = entity.getUniqueId();
    if (entityId == null) {
      return;
    }
    presentationRefreshes.remove(entityId);
    IndexedStackEntity indexed = indexedEntities.remove(entityId);
    if (indexed != null) {
      removeIndexedFromChunk(indexed);
    }
  }

  private void removeIndexedFromChunk(IndexedStackEntity indexed) {
    Map<UUID, IndexedStackEntity> chunk = indexedChunks.get(indexed.chunkKey());
    if (chunk == null) {
      return;
    }
    chunk.remove(indexed.entityId(), indexed);
    if (chunk.isEmpty()) {
      indexedChunks.remove(indexed.chunkKey(), chunk);
    }
  }

  private void addCanonicalNeighborAnchors(UUID worldId, long chunkKey, Set<Long> pending) {
    int chunkRadius = neighborChunkRadius();
    if (chunkRadius == 0) {
      return;
    }

    int chunkX = chunkKeyX(chunkKey);
    int chunkZ = chunkKeyZ(chunkKey);
    int inspected = 0;
    for (int distance = 1; distance <= chunkRadius && inspected < MAX_NEIGHBOR_CHUNKS_PER_WORK; distance++) {
      for (int offsetX = -distance; offsetX <= distance && inspected < MAX_NEIGHBOR_CHUNKS_PER_WORK; offsetX++) {
        for (int offsetZ = -distance; offsetZ <= distance && inspected < MAX_NEIGHBOR_CHUNKS_PER_WORK; offsetZ++) {
          if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != distance) {
            continue;
          }
          inspected++;
          long neighborKey = packChunkKey(chunkX + offsetX, chunkZ + offsetZ);
          if (Long.compare(neighborKey, chunkKey) >= 0) {
            continue;
          }
          Map<UUID, IndexedStackEntity> neighbor = indexedChunks.get(new ChunkWorkKey(worldId, neighborKey));
          if (neighbor != null && !neighbor.isEmpty()) {
            pending.add(neighborKey);
          }
        }
      }
    }
  }

  @Override
  public void onTick() {
    long generation = lifecycleGeneration.get();
    if (!isActive(generation)) {
      dirtyChunks.clear();
      return;
    }

    if (dirtyChunks.isEmpty()) {
      return;
    }

    int availableSubmissions = MAX_CHUNK_SUBMISSIONS_PER_TICK - countInFlightChunks();
    if (availableSubmissions <= 0) {
      return;
    }

    boolean folia = J.isFoliaThreading();
    List<ChunkClaim> claims = new ArrayList<>(availableSubmissions);
    int submitted = 0;
    int inspected = 0;
    for (Map.Entry<UUID, Set<Long>> entry : dirtyChunks.entrySet()) {
      if (!isActive(generation)
          || submitted >= availableSubmissions
          || inspected >= MAX_CHUNK_INSPECTIONS_PER_TICK) {
        break;
      }

      UUID worldId = entry.getKey();
      Set<Long> pending = entry.getValue();
      if (pending == null || pending.isEmpty()) {
        continue;
      }

      World world = Bukkit.getWorld(worldId);
      if (world == null) {
        pending.clear();
        chunkWork.keySet().removeIf(workKey -> workKey.worldId().equals(worldId));
        continue;
      }

      Iterator<Long> pendingIterator = pending.iterator();
      while (isActive(generation)
          && submitted < availableSubmissions
          && inspected < MAX_CHUNK_INSPECTIONS_PER_TICK
          && pendingIterator.hasNext()) {
        Long boxedKey = pendingIterator.next();
        inspected++;
        if (boxedKey == null) {
          continue;
        }

        long key = boxedKey;
        if (!claimChunk(worldId, pending, key)) {
          continue;
        }

        claims.add(new ChunkClaim(worldId, world, key, generation));
        submitted++;
      }
    }

    if (claims.isEmpty()) {
      return;
    }
    if (folia) {
      for (ChunkClaim claim : claims) {
        submitFoliaChunkClaim(claim);
      }
      return;
    }

    try {
      J.s(() -> {
        for (ChunkClaim claim : claims) {
          runChunkClaim(claim);
        }
      });
    } catch (Throwable throwable) {
      React.reportError(throwable);
      for (ChunkClaim claim : claims) {
        completeChunkClaim(claim, false);
      }
    }
  }

  private int countInFlightChunks() {
    int count = 0;
    for (Set<Long> inFlight : inFlightChunks.values()) {
      count += inFlight.size();
      if (count >= MAX_CHUNK_SUBMISSIONS_PER_TICK) {
        return MAX_CHUNK_SUBMISSIONS_PER_TICK;
      }
    }
    return count;
  }

  private boolean claimChunk(UUID worldId, Set<Long> pending, long key) {
    Set<Long> existing = inFlightChunks.get(worldId);
    if (existing != null && existing.contains(key)) {
      return false;
    }
    if (!pending.remove(key)) {
      return false;
    }

    Set<Long> inFlight = inFlightChunks.computeIfAbsent(worldId, ignored -> ConcurrentHashMap.newKeySet());
    if (inFlight.add(key)) {
      return true;
    }

    pending.add(key);
    return false;
  }

  private void submitFoliaChunkClaim(ChunkClaim claim) {
    boolean scheduled = false;
    try {
      scheduled = J.runChunk(
          claim.world(),
          chunkKeyX(claim.key()),
          chunkKeyZ(claim.key()),
          () -> runChunkClaim(claim)
      );
    } catch (Throwable throwable) {
      React.reportError(throwable);
    } finally {
      if (!scheduled) {
        completeChunkClaim(claim, false);
      }
    }
  }

  private void runChunkClaim(ChunkClaim claim) {
    boolean completed = false;
    try {
      if (!isActive(claim.generation())) {
        return;
      }
      completed = stackChunk(claim.world(), chunkKeyX(claim.key()), chunkKeyZ(claim.key()));
    } catch (Throwable throwable) {
      chunkWork.remove(new ChunkWorkKey(claim.worldId(), claim.key()));
      React.reportError(throwable);
    } finally {
      completeChunkClaim(claim, completed);
    }
  }

  private void completeChunkClaim(ChunkClaim claim, boolean completed) {
    if (claim.generation() != lifecycleGeneration.get()) {
      return;
    }

    Set<Long> inFlight = inFlightChunks.get(claim.worldId());
    if (inFlight != null) {
      inFlight.remove(claim.key());
    }
    if (!completed && isActive(claim.generation())) {
      dirtyChunks.computeIfAbsent(claim.worldId(), ignored -> ConcurrentHashMap.newKeySet())
          .add(claim.key());
    }
  }

  private boolean stackChunk(World world, int chunkX, int chunkZ) {
    ChunkWorkKey workKey = new ChunkWorkKey(world.getUID(), packChunkKey(chunkX, chunkZ));
    if (!active || !world.isChunkLoaded(chunkX, chunkZ)) {
      chunkWork.remove(workKey);
      return true;
    }

    ChunkWork work = chunkWork.get(workKey);
    if (work == null) {
      Chunk chunk = world.getChunkAt(chunkX, chunkZ);
      Entity[] chunkEntities = chunk.getEntities();
      Entity[] candidates = collectChunkCandidates(workKey, chunkX, chunkZ, chunkEntities);
      if (candidates.length < 2) {
        return true;
      }

      ChunkWork created = new ChunkWork(candidates);
      ChunkWork existing = chunkWork.putIfAbsent(workKey, created);
      work = existing == null ? created : existing;
    }

    boolean completed = advanceChunkWork(work, J.isFoliaThreading());
    if (completed) {
      chunkWork.remove(workKey, work);
    }
    return completed;
  }

  private Entity[] collectChunkCandidates(
      ChunkWorkKey anchorKey,
      int chunkX,
      int chunkZ,
      Entity[] exactChunkEntities
  ) {
    int chunkRadius = neighborChunkRadius();
    if (chunkRadius == 0) {
      return exactChunkEntities;
    }

    List<Entity> candidates = new ArrayList<>(exactChunkEntities.length + MAX_NEIGHBOR_ENTITIES_PER_WORK);
    Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Entity entity : exactChunkEntities) {
      if (seen.add(entity)) {
        candidates.add(entity);
      }
    }

    int inspectedChunks = 0;
    int addedEntities = 0;
    for (int distance = 1;
         distance <= chunkRadius
             && inspectedChunks < MAX_NEIGHBOR_CHUNKS_PER_WORK
             && addedEntities < MAX_NEIGHBOR_ENTITIES_PER_WORK;
         distance++) {
      for (int offsetX = -distance;
           offsetX <= distance
               && inspectedChunks < MAX_NEIGHBOR_CHUNKS_PER_WORK
               && addedEntities < MAX_NEIGHBOR_ENTITIES_PER_WORK;
           offsetX++) {
        for (int offsetZ = -distance;
             offsetZ <= distance
                 && inspectedChunks < MAX_NEIGHBOR_CHUNKS_PER_WORK
                 && addedEntities < MAX_NEIGHBOR_ENTITIES_PER_WORK;
             offsetZ++) {
          if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != distance) {
            continue;
          }
          inspectedChunks++;
          long neighbor = packChunkKey(chunkX + offsetX, chunkZ + offsetZ);
          if (Long.compare(neighbor, anchorKey.key()) <= 0) {
            continue;
          }

          ChunkWorkKey neighborKey = new ChunkWorkKey(anchorKey.worldId(), neighbor);
          Map<UUID, IndexedStackEntity> indexed = indexedChunks.get(neighborKey);
          if (indexed == null) {
            continue;
          }
          for (IndexedStackEntity candidate : indexed.values()) {
            if (addedEntities >= MAX_NEIGHBOR_ENTITIES_PER_WORK) {
              break;
            }
            if (!candidate.chunkKey().equals(neighborKey)) {
              continue;
            }
            Entity entity = candidate.reference().get();
            if (entity == null) {
              indexed.remove(candidate.entityId(), candidate);
              indexedEntities.remove(candidate.entityId(), candidate);
              continue;
            }
            if (seen.add(entity)) {
              candidates.add(entity);
              addedEntities++;
            }
          }
          if (indexed.isEmpty()) {
            indexedChunks.remove(neighborKey, indexed);
          }
        }
      }
    }
    return candidates.toArray(Entity[]::new);
  }

  private int neighborChunkRadius() {
    return Math.max(0, (int) Math.ceil(Math.max(0D, searchRadius) / 16D));
  }

  private boolean advanceChunkWork(ChunkWork work, boolean folia) {
    int entitiesRemaining = MAX_ENTITIES_PER_CHUNK_CALLBACK;
    while (work.scanCursor < work.chunkEntities.length && entitiesRemaining > 0) {
      Entity entity = work.chunkEntities[work.scanCursor++];
      entitiesRemaining--;
      if (folia && !J.isOwnedByCurrentRegion(entity)) {
        continue;
      }
      if (!isStackCandidate(entity)) {
        continue;
      }

      work.buildingBuckets.computeIfAbsent(bucketOf(entity), ignored -> new ArrayList<>()).add(entity);
    }
    if (work.scanCursor < work.chunkEntities.length) {
      return false;
    }

    if (work.buckets == null) {
      work.buckets = new ArrayList<>(work.buildingBuckets.values());
      work.buildingBuckets.clear();
      work.startCollapse();
    }

    int comparisonsRemaining = MAX_COMPARISONS_PER_CHUNK_CALLBACK;
    while (work.bucketCursor < work.buckets.size()) {
      List<Entity> bucket = work.buckets.get(work.bucketCursor);
      if (bucket.size() < 2) {
        work.nextBucket();
        continue;
      }

      if (work.entityCursor >= bucket.size()) {
        work.nextBucket();
        continue;
      }

      if (work.currentEntity == null) {
        if (entitiesRemaining <= 0) {
          return false;
        }
        entitiesRemaining--;
        Entity entity = bucket.get(work.entityCursor);
        if (folia && !J.isOwnedByCurrentRegion(entity)) {
          work.finishCurrent(false);
          continue;
        }
        if (entity.isDead()) {
          work.finishCurrent(false);
          continue;
        }

        int protection = ReactProtection.operationsFor(entity);
        Location location = entity.getLocation();
        work.beginCurrent(entity, location, protection);
        if (ReactOperations.covers(protection, ReactOperation.STACK)) {
          work.finishCurrent(true);
          continue;
        }
      }

      if (folia && !J.isOwnedByCurrentRegion(work.currentEntity)) {
        work.finishCurrent(false);
        continue;
      }
      if (work.currentEntity.isDead()) {
        work.finishCurrent(false);
        continue;
      }

      boolean merged = false;
      while (work.survivorCursor < work.survivors.size()) {
        if (comparisonsRemaining <= 0) {
          return false;
        }
        comparisonsRemaining--;

        int survivorIndex = work.survivorCursor++;
        if (ReactOperations.covers(work.survivorProtection[survivorIndex], ReactOperation.STACK)) {
          continue;
        }

        Entity survivor = work.survivors.get(survivorIndex);
        if (folia && !J.isOwnedByCurrentRegion(survivor)) {
          continue;
        }
        if (survivor.isDead()) {
          continue;
        }

        Location to = work.survivorLocations.get(survivorIndex);
        if (!withinMergeRadius(
            work.currentLocation.getX(),
            work.currentLocation.getY(),
            work.currentLocation.getZ(),
            to.getX(),
            to.getY(),
            to.getZ(),
            searchRadius
        )) {
          continue;
        }

        if (merge(work.currentEntity, survivor)) {
          merged = true;
          break;
        }
      }

      work.finishCurrent(!merged);
    }

    return true;
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

  private boolean isActive(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  private record ChunkClaim(UUID worldId, World world, long key, long generation) {
  }

  private record ChunkWorkKey(UUID worldId, long key) {
  }

  private record IndexedStackEntity(
      UUID entityId,
      ChunkWorkKey chunkKey,
      WeakReference<Entity> reference
  ) {
  }

  private static final class ChunkWork {
    private final Entity[] chunkEntities;
    private final Map<StackBucket, List<Entity>> buildingBuckets;
    private List<List<Entity>> buckets;
    private List<Entity> survivors;
    private List<Location> survivorLocations;
    private int[] survivorProtection;
    private int scanCursor;
    private int bucketCursor;
    private int entityCursor;
    private int survivorCursor;
    private Entity currentEntity;
    private Location currentLocation;
    private int currentProtection;

    private ChunkWork(Entity[] chunkEntities) {
      this.chunkEntities = chunkEntities;
      this.buildingBuckets = new HashMap<>();
      this.survivors = new ArrayList<>();
      this.survivorLocations = new ArrayList<>();
      this.survivorProtection = new int[0];
    }

    private void beginCurrent(Entity entity, Location location, int protection) {
      currentEntity = entity;
      currentLocation = location;
      currentProtection = protection;
      survivorCursor = 0;
    }

    private void startCollapse() {
      survivorProtection = buckets.isEmpty() ? new int[0] : new int[buckets.getFirst().size()];
    }

    private void finishCurrent(boolean keep) {
      if (keep && currentEntity != null) {
        int survivorIndex = survivors.size();
        survivorProtection[survivorIndex] = currentProtection;
        survivors.add(currentEntity);
        survivorLocations.add(currentLocation);
      }
      currentEntity = null;
      currentLocation = null;
      currentProtection = 0;
      survivorCursor = 0;
      entityCursor++;
    }

    private void nextBucket() {
      bucketCursor++;
      entityCursor = 0;
      survivorCursor = 0;
      currentEntity = null;
      currentLocation = null;
      currentProtection = 0;
      survivors = new ArrayList<>();
      survivorLocations = new ArrayList<>();
      survivorProtection = bucketCursor < buckets.size()
          ? new int[buckets.get(bucketCursor).size()]
          : new int[0];
    }
  }

  private record StackableIndex(Set<EntityType> source, EnumSet<EntityType> types) {
  }

  @Override
  public void onDeactivate() {
    active = false;
    lifecycleGeneration.incrementAndGet();
    EntityController controller = React.controller(EntityController.class);
    if (controller != null) {
      controller.unregisterEntityTickListener(entityTickListener);
    }
    dirtyChunks.clear();
    inFlightChunks.clear();
    chunkWork.clear();
    indexedEntities.clear();
    indexedChunks.clear();
    presentationRefreshes.clear();
  }

  @Override
  public int getTickInterval() {
    return Math.max(50, batchIntervalMs);
  }
}
