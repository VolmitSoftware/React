package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.NMS;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.ObserverController.LoadedChunkCursor;
import art.arcane.react.core.controller.ObserverController.LoadedChunkTarget;
import art.arcane.react.core.integration.GlossEntityOverlayIntegration;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.MobStackHost;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.project.world.CubeMobs;
import art.arcane.volmlib.util.event.ProtectionProbe;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BukkitMobStackHost implements MobStackHost, Listener {
  private static final NamespacedKey STACK_LABEL_KEY = new NamespacedKey("react", "mob-stack-label");
  private final List<Events> listeners = new CopyOnWriteArrayList<>();
  private final GlossEntityOverlayIntegration glossEntityOverlays;
  private final MobStateAccess mobState;
  private boolean registered;

  public BukkitMobStackHost(GlossEntityOverlayIntegration glossEntityOverlays, MobStateAccess mobState) {
    this.glossEntityOverlays = Objects.requireNonNull(glossEntityOverlays);
    this.mobState = Objects.requireNonNull(mobState);
  }

  public static boolean sameCubeSize(Entity source, Entity target) {
    if (CubeMobs.isCubeMob(source)) {
      return CubeMobs.isCubeMob(target) && CubeMobs.getSize(source) == CubeMobs.getSize(target);
    }

    return !CubeMobs.isCubeMob(target);
  }

  public static boolean isTamedPet(Entity entity) {
    return entity instanceof Tameable tameable && tameable.isTamed();
  }

  @Override
  public synchronized Subscription listen(Events events) {
    listeners.add(events);
    if (!registered && React.instance != null) {
      React.instance.registerListener(this);
      registered = true;
    }
    Subscription spawns = MobStackHost.spawnEvents(BukkitReactPlatform.instance().spawnHost().orElse(null), events);
    return () -> {
      spawns.cancel();
      remove(events);
    };
  }

  private synchronized void remove(Events events) {
    listeners.remove(events);
    if (listeners.isEmpty() && registered) {
      registered = false;
      if (React.instance != null) {
        React.instance.unregisterListener(this);
      }
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onEntityTame(EntityTameEvent event) {
    GameEntity entity = new BukkitGameEntity(event.getEntity());
    AnimalTamer owner = event.getOwner();
    UUID ownerId = owner == null ? null : owner.getUniqueId();
    for (Events events : listeners) {
      events.onTame(entity, ownerId);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
    if (ProtectionProbe.isProbe(event) || event.isCancelled()) {
      return;
    }
    if (event.getPlayer().isSneaking() && event.getHand().equals(EquipmentSlot.HAND)) {
      GameEntity clicked = new BukkitGameEntity(event.getRightClicked());
      for (Events events : listeners) {
        events.onSneakInteract(clicked);
      }
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onEntityDeath(EntityDeathEvent event) {
    GameEntity entity = new BukkitGameEntity(event.getEntity());
    for (Events events : listeners) {
      events.onDeath(entity);
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onCreeperPower(CreeperPowerEvent event) {
    boolean powered = event.getCause() != CreeperPowerEvent.PowerCause.SET_OFF;
    if (!event.isCancelled() && powered != event.getEntity().isPowered()) {
      beforeStateChange(event.getEntity(), event);
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onEndermanChangeBlock(EntityChangeBlockEvent event) {
    if (!event.isCancelled() && event.getEntity() instanceof Enderman enderman) {
      beforeStateChange(enderman, event);
    }
  }

  private void beforeStateChange(LivingEntity source, org.bukkit.event.Cancellable event) {
    GameEntity entity = new BukkitGameEntity(source);
    for (Events events : listeners) {
      if (!events.beforeStateChange(entity)) {
        event.setCancelled(true);
        return;
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onEntitiesLoad(EntitiesLoadEvent event) {
    List<Entity> loaded = event.getEntities();
    List<GameEntity> entities = new AbstractList<>() {
      @Override
      public GameEntity get(int index) {
        return new BukkitGameEntity(loaded.get(index));
      }

      @Override
      public int size() {
        return loaded.size();
      }
    };
    for (Events events : listeners) {
      events.onEntitiesLoad(entities);
    }
  }

  @EventHandler
  public void onEntityRemove(EntityRemoveEvent event) {
    boolean exploded = event.getCause() == EntityRemoveEvent.Cause.EXPLODE && event.getEntity() instanceof Creeper;
    GameEntity entity = new BukkitGameEntity(event.getEntity());
    for (Events events : listeners) {
      events.onRemove(entity, exploded);
    }
  }

  @Override
  public Set<String> defaultStackableTypes() {
    Set<String> types = new HashSet<>();
    for (EntityType type : EntityType.values()) {
      if (type.isAlive() && type.isSpawnable()) {
        types.add(type.name());
      }
    }
    types.remove(EntityType.PLAYER.name());
    types.remove(EntityType.ARMOR_STAND.name());
    types.remove(EntityType.VILLAGER.name());
    types.remove(EntityType.WANDERING_TRADER.name());
    types.remove(EntityType.FALLING_BLOCK.name());
    return types;
  }


  @Override
  public boolean isLiving(GameEntity entity) {
    return bukkit(entity) instanceof LivingEntity;
  }

  @Override
  public boolean isDead(GameEntity entity) {
    return bukkit(entity).isDead();
  }

  @Override
  public boolean isAlive(GameEntity entity) {
    Entity bukkit = bukkit(entity);
    return bukkit.isValid() && !bukkit.isDead();
  }

  @Override
  public boolean isSameEntity(GameEntity first, GameEntity second) {
    return bukkit(first).getEntityId() == bukkit(second).getEntityId();
  }

  @Override
  public boolean isTamedPet(GameEntity entity) {
    return isTamedPet(bukkit(entity));
  }

  @Override
  public UUID ownerId(GameEntity entity) {
    return bukkit(entity) instanceof Tameable tameable ? mobState.ownerId(tameable) : null;
  }

  @Override
  public Object mergeBucket(GameEntity entity) {
    Entity bukkit = bukkit(entity);
    int size = CubeMobs.isCubeMob(bukkit) ? CubeMobs.getSize(bukkit) : -1;
    boolean adult = !(bukkit instanceof Ageable ageable) || ageable.isAdult();
    Object color = bukkit instanceof Sheep sheep ? sheep.getColor() : null;
    Object profession = bukkit instanceof Villager villager ? villager.getProfession() : null;
    return new StackBucket(bukkit.getType(), size, adult, color, profession);
  }

  @Override
  public boolean hasSafeMergeState(GameEntity source, GameEntity target) {
    LivingEntity left = living(source);
    LivingEntity right = living(target);
    if (!hasMatchingHostileState(left, right)) {
      return false;
    }
    if (!hasMatchingFarmState(left, right)) {
      return false;
    }
    if (hasEquipment(left) || hasEquipment(right)) {
      return false;
    }
    return left.getActivePotionEffects().isEmpty() && right.getActivePotionEffects().isEmpty();
  }

  @Override
  public boolean hasSafeHealthState(GameEntity source, GameEntity target) {
    LivingEntity left = living(source);
    LivingEntity right = living(target);
    if (isVariantBearingType(left.getType()) || isVariantBearingType(right.getType())) {
      return false;
    }

    AttributeInstance sourceMaxHealth = left.getAttribute(Attribute.MAX_HEALTH);
    AttributeInstance targetMaxHealth = right.getAttribute(Attribute.MAX_HEALTH);
    if (sourceMaxHealth == null || targetMaxHealth == null) {
      return false;
    }
    if (!sourceMaxHealth.getModifiers().isEmpty() || !targetMaxHealth.getModifiers().isEmpty()) {
      return false;
    }
    if (Math.abs(sourceMaxHealth.getValue() - targetMaxHealth.getValue()) > 0.0001D) {
      return false;
    }
    return Math.abs(left.getHealth() - sourceMaxHealth.getValue()) <= 0.0001D
        && Math.abs(right.getHealth() - targetMaxHealth.getValue()) <= 0.0001D;
  }

  @Override
  public double maxHealth(GameEntity entity) {
    AttributeInstance attribute = living(entity).getAttribute(Attribute.MAX_HEALTH);
    return attribute == null ? Double.NaN : attribute.getValue();
  }

  @Override
  public double health(GameEntity entity) {
    return living(entity).getHealth();
  }

  @Override
  public void setHealth(GameEntity entity, double health) {
    living(entity).setHealth(health);
  }

  @Override
  public void restoreHealth(GameEntity entity, double health) {
    LivingEntity living = living(entity);
    living.setHealth(Math.min(living.getMaxHealth(), health));
  }

  @Override
  public GameEntity spawnCopy(GameEntity source, double raise, boolean copyName) {
    LivingEntity living = living(source);
    World world = living.getWorld();
    if (world == null) {
      return null;
    }
    Location location = raise == 0D ? living.getLocation() : living.getLocation().add(0D, raise, 0D);
    Entity created = world.spawnEntity(location, living.getType());
    if (!(created instanceof LivingEntity copy) || !copy.isValid()) {
      if (created != null) {
        created.remove();
      }
      return null;
    }
    try {
      copyState(living, copy, copyName);
    } catch (RuntimeException failure) {
      copy.remove();
      throw failure;
    }
    return new BukkitGameEntity(copy);
  }

  @Override
  public void untame(GameEntity entity) {
    if (bukkit(entity) instanceof Tameable tameable) {
      tameable.setOwner(null);
      tameable.setTamed(false);
    }
  }

  @Override
  public void discard(GameEntity entity) {
    bukkit(entity).remove();
  }

  @Override
  public void sendCollect(GameEntity collected, GameEntity collector) {
    Entity source = bukkit(collected);
    NMS.sendCollectPacket(source, 64, source.getEntityId(), bukkit(collector).getEntityId(), 1);
  }

  @Override
  public boolean hasFlag(GameEntity entity, String flag) {
    return bukkit(entity).hasMetadata(flag);
  }

  @Override
  public void setFlag(GameEntity entity, String flag, boolean value) {
    if (value) {
      bukkit(entity).setMetadata(flag, new FixedMetadataValue(React.instance, true));
    } else {
      bukkit(entity).removeMetadata(flag, React.instance);
    }
  }

  @Override
  public String customName(GameEntity entity) {
    return bukkit(entity).getCustomName();
  }

  @Override
  public void setCustomName(GameEntity entity, String name) {
    bukkit(entity).setCustomName(name);
  }

  @Override
  public String stackLabel(GameEntity entity) {
    return bukkit(entity).getPersistentDataContainer().get(STACK_LABEL_KEY, PersistentDataType.STRING);
  }

  @Override
  public void setStackLabel(GameEntity entity, String label) {
    bukkit(entity).getPersistentDataContainer().set(STACK_LABEL_KEY, PersistentDataType.STRING, label);
  }

  @Override
  public void clearStackLabel(GameEntity entity) {
    bukkit(entity).getPersistentDataContainer().remove(STACK_LABEL_KEY);
  }

  @Override
  public boolean showOverlay(GameEntity entity, int count) {
    return bukkit(entity) instanceof LivingEntity living && glossEntityOverlays.refresh(living, count);
  }

  @Override
  public boolean isChunkLoaded(ReactWorld world, int chunkX, int chunkZ) {
    return ((World) world.handle()).isChunkLoaded(chunkX, chunkZ);
  }

  @Override
  public List<GameEntity> loadedChunkEntities(ReactWorld world, int chunkX, int chunkZ) {
    Chunk chunk = ((World) world.handle()).getChunkAt(chunkX, chunkZ, false);
    if (chunk == null || !chunk.isEntitiesLoaded()) {
      return null;
    }
    Entity[] entities = chunk.getEntities();
    List<GameEntity> result = new ArrayList<>(entities.length);
    for (Entity entity : entities) {
      result.add(new BukkitGameEntity(entity));
    }
    return result;
  }

  @Override
  public ChunkCursor openLoadedChunks() {
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null || !observer.isLoadedChunkCoordinateIndexReady()) {
      return null;
    }
    LoadedChunkCursor cursor = observer.openLoadedChunkCursor();
    return maximum -> {
      List<LoadedChunkTarget> targets = cursor.next(maximum);
      List<ChunkRef> result = new ArrayList<>(targets.size());
      for (LoadedChunkTarget target : targets) {
        result.add(new ChunkRef(target.worldId(), target.chunkX(), target.chunkZ()));
      }
      return result;
    };
  }

  private static Entity bukkit(GameEntity entity) {
    return BukkitGameEntity.bukkit(entity);
  }

  private static LivingEntity living(GameEntity entity) {
    return (LivingEntity) BukkitGameEntity.bukkit(entity);
  }

  private void copyState(LivingEntity source, LivingEntity target, boolean copyName) {
    if (CubeMobs.isCubeMob(source) && CubeMobs.isCubeMob(target)) {
      CubeMobs.setSize(target, Math.max(1, CubeMobs.getSize(source)));
    }

    if (source instanceof Sheep sourceSheep && target instanceof Sheep targetSheep) {
      targetSheep.setColor(sourceSheep.getColor());
      targetSheep.setSheared(sourceSheep.isSheared());
    }

    if (source instanceof Ageable sourceAgeable && target instanceof Ageable targetAgeable) {
      if (sourceAgeable.isAdult()) {
        targetAgeable.setAdult();
      } else {
        targetAgeable.setBaby();
      }
      targetAgeable.setAge(sourceAgeable.getAge());
      targetAgeable.setAgeLock(sourceAgeable.getAgeLock());
    }

    if (source instanceof Animals sourceAnimal && target instanceof Animals targetAnimal) {
      targetAnimal.setBreedCause(sourceAnimal.getBreedCause());
      targetAnimal.setLoveModeTicks(sourceAnimal.getLoveModeTicks());
    }
    copyFarmVariant(source, target);
    copyHostileState(source, target);

    target.setAI(source.hasAI());
    target.setCollidable(source.isCollidable());
    if (copyName) {
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

    for (String flag : List.of(SPAWNED_BY_SPAWNER, DO_NOT_STACK, UNIQUE_STACK)) {
      if (source.hasMetadata(flag)) {
        target.setMetadata(flag, new FixedMetadataValue(React.instance, true));
      }
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
    return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
  }

  private boolean isVariantBearingType(EntityType type) {
    return switch (type.name()) {
      case "AXOLOTL", "BEE", "BOGGED", "CAMEL", "CAT",
           "DONKEY", "ENDER_DRAGON", "FOX", "FROG", "GHAST", "GOAT", "HOGLIN",
           "HORSE", "IRON_GOLEM", "LLAMA", "MOOSHROOM", "MULE",
           "MUSHROOM_COW", "OCELOT", "PANDA", "PARROT", "PHANTOM", "PIGLIN",
           "PIGLIN_BRUTE", "RABBIT", "SALMON", "SKELETON_HORSE",
           "SNIFFER", "SNOW_GOLEM", "STRIDER", "TRADER_LLAMA", "TROPICAL_FISH", "VEX",
           "VILLAGER", "WANDERING_TRADER", "WOLF", "ZOMBIE_HORSE",
           "ZOMBIE_NAUTILUS", "ZOMBIE_VILLAGER", "ZOMBIFIED_PIGLIN" -> true;
      default -> false;
    };
  }

  private boolean hasMatchingHostileState(LivingEntity source, LivingEntity target) {
    if ((source instanceof Creeper || source instanceof Enderman)
        && (source.isInsideVehicle() || target.isInsideVehicle()
            || !source.getPassengers().isEmpty() || !target.getPassengers().isEmpty())) {
      return false;
    }
    if (source instanceof Creeper left && target instanceof Creeper right) {
      return left.isPowered() == right.isPowered()
          && left.getMaxFuseTicks() == right.getMaxFuseTicks()
          && left.getExplosionRadius() == right.getExplosionRadius()
          && !mobState.isIgnited(left) && !mobState.isIgnited(right)
          && left.getFuseTicks() == 0 && right.getFuseTicks() == 0;
    }
    if (source instanceof Enderman left && target instanceof Enderman right) {
      return Objects.equals(left.getCarriedBlock(), right.getCarriedBlock())
          && left.getTarget() == null && right.getTarget() == null
          && !mobState.isProvoked(left) && !mobState.isProvoked(right);
    }
    return true;
  }

  private void copyHostileState(LivingEntity source, LivingEntity target) {
    if (source instanceof Creeper left && target instanceof Creeper right) {
      right.setPowered(left.isPowered());
      if (right.isPowered() != left.isPowered()) {
        throw new IllegalStateException("The replacement creeper's powered state was rejected.");
      }
      right.setMaxFuseTicks(left.getMaxFuseTicks());
      right.setExplosionRadius(left.getExplosionRadius());
      right.setFuseTicks(0);
      mobState.clearIgnition(right);
    } else if (source instanceof Enderman left && target instanceof Enderman right) {
      BlockData carried = left.getCarriedBlock();
      right.setCarriedBlock(carried == null ? null : carried.clone());
      mobState.copyProvocation(left, right);
    }
  }

  private boolean hasMatchingFarmState(LivingEntity source, LivingEntity target) {
    if (!(source instanceof Animals sourceAnimal) || !(target instanceof Animals targetAnimal)) {
      return true;
    }
    if (sourceAnimal.getAge() != targetAnimal.getAge()
        || sourceAnimal.getAgeLock() != targetAnimal.getAgeLock()
        || sourceAnimal.canBreed() != targetAnimal.canBreed()
        || sourceAnimal.getLoveModeTicks() != targetAnimal.getLoveModeTicks()
        || !Objects.equals(sourceAnimal.getBreedCause(), targetAnimal.getBreedCause())
        || sourceAnimal.isLeashed() || targetAnimal.isLeashed()
        || sourceAnimal.isInsideVehicle() || targetAnimal.isInsideVehicle()
        || !sourceAnimal.getPassengers().isEmpty() || !targetAnimal.getPassengers().isEmpty()) {
      return false;
    }
    if (source instanceof Cow left && target instanceof Cow right) {
      return Objects.equals(left.getVariant(), right.getVariant())
          && mobState.sameSoundVariant(left, right);
    }
    if (source instanceof Chicken left && target instanceof Chicken right) {
      return Objects.equals(left.getVariant(), right.getVariant())
          && mobState.sameSoundVariant(left, right)
          && mobState.isChickenJockey(left) == mobState.isChickenJockey(right);
    }
    if (source instanceof Pig left && target instanceof Pig right) {
      return Objects.equals(left.getVariant(), right.getVariant())
          && mobState.sameSoundVariant(left, right)
          && left.hasSaddle() == right.hasSaddle()
          && left.getBoostTicks() == right.getBoostTicks()
          && left.getCurrentBoostTicks() == right.getCurrentBoostTicks();
    }
    return !(source instanceof Sheep left) || !(target instanceof Sheep right)
        || left.isSheared() == right.isSheared();
  }

  private void copyFarmVariant(LivingEntity source, LivingEntity target) {
    if (source instanceof Cow left && target instanceof Cow right) {
      right.setVariant(left.getVariant());
      mobState.copySoundVariant(left, right);
    } else if (source instanceof Chicken left && target instanceof Chicken right) {
      right.setVariant(left.getVariant());
      mobState.copySoundVariant(left, right);
      mobState.copyChickenState(left, right);
    } else if (source instanceof Pig left && target instanceof Pig right) {
      right.setVariant(left.getVariant());
      mobState.copySoundVariant(left, right);
      right.setSaddle(left.hasSaddle());
      right.setBoostTicks(left.getBoostTicks());
      right.setCurrentBoostTicks(left.getCurrentBoostTicks());
    }
  }

  private record StackBucket(EntityType type, int size, boolean adult, Object color, Object profession) {
  }
}
