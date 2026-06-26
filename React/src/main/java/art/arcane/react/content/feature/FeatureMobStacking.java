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
import art.arcane.react.content.sampler.SamplerEntities;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import art.arcane.react.core.NMS;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.CustomMobChecker;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashSet;
import java.util.Set;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Mob Stacking feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureMobStacking extends ReactFeature implements Listener {
  static boolean exceedsStackLimit(int intoCount, int sourceCount, int maxStackSize) {
    return intoCount + sourceCount > maxStackSize;
  }

  static boolean withinHealthLimit(double sourceMaxHealth, double intoMaxHealth, double maxHealth) {
    return sourceMaxHealth + intoMaxHealth <= maxHealth;
  }

  static int theoreticalMaxStackCount(double maxHealth, double entityMaxHealth, int maxStackSize) {
    return Math.min((int) Math.ceil(Math.floor(maxHealth / entityMaxHealth)), maxStackSize);
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

  private transient final Map<EntityType, String> formattedBaseNames = new ConcurrentHashMap<>();
  private transient SamplerEntities entitiesSampler;

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
    for (EntityType i : stackableTypes) {
      React.controller(EntityController.class).registerEntityTickListener(i, this::onTick);
    }
  }

  @EventHandler
  public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
    // Check for sneak and right click
    if (event.getPlayer().isSneaking() && event.getHand().equals(EquipmentSlot.HAND)) {
      Entity clickedEntity = event.getRightClicked();


      // Check if entity is stackable and has more than 1 in the stack
      if (stackableTypes.contains(clickedEntity.getType()) && getStackCount(clickedEntity) > 1) {
        // Calculate new stack counts for split entities
        int newStackCount = getStackCount(clickedEntity) / 2;
        int remainingStackCount = getStackCount(clickedEntity) - newStackCount;

        // Create new entity with half of the original stack count
        LivingEntity newEntity;
        if (clickedEntity instanceof Sheep) {
          Sheep oldSheep = (Sheep) clickedEntity;
          newEntity = (LivingEntity) clickedEntity.getWorld().spawnEntity(clickedEntity.getLocation().add(0, 0.5, 0), clickedEntity.getType());
          ((Sheep) newEntity).setColor(oldSheep.getColor()); // setting the new sheep color
        } else if (clickedEntity instanceof Slime) {
          Slime oldSlime = (Slime) clickedEntity;
          newEntity = (LivingEntity) clickedEntity.getWorld().spawnEntity(clickedEntity.getLocation().add(0, 0.5, 0), clickedEntity.getType());
          if (oldSlime.getSize() > 1) { // This is to ensure no infinite loop of slime spawning
            ((Slime) newEntity).setSize(oldSlime.getSize() / 2); // setting the new size
          } else {
            ((Slime) newEntity).setSize(oldSlime.getSize());
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
      e.setCustomName(ChatColor.GOLD + "" + count + "x UNIQUE" + ChatColor.RESET + ChatColor.GRAY + "" + Form.capitalizeWords(e.getType().name().toLowerCase().replaceAll("\\Q_\\E", " ")));
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

  private void spawnReplacement(LivingEntity source, int nextStackCount) {
    if (nextStackCount <= 0 || source.getWorld() == null) {
      return;
    }

    Location spawnLocation = source.getLocation();
    Entity created = source.getWorld().spawnEntity(spawnLocation, source.getType());
    if (!(created instanceof LivingEntity replacement)) {
      created.remove();
      return;
    }

    copyState(source, replacement);
    setStackCount(replacement, nextStackCount);
  }

  private void copyState(LivingEntity source, LivingEntity target) {
    if (source instanceof Slime sourceSlime && target instanceof Slime targetSlime) {
      targetSlime.setSize(Math.max(1, sourceSlime.getSize()));
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
    if (canMerge(a, into)) {
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

    // types that can stack
    if (!stackableTypes.contains(a.getType())) {
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

    // Check if entities are Slimes or Magma Cubes and if their sizes match
    if ((a instanceof Slime || a instanceof MagmaCube) && (into instanceof Slime || into instanceof MagmaCube)) {
      if (((Slime) a).getSize() != ((Slime) into).getSize()) {
        return false;
      }
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
    if (stackableTypes.contains(e.getEntityType())) {
      if (J.isFoliaThreading()) {
        J.runEntity(e.getEntity(), () -> onTick(e.getEntity()));
      } else {
        J.s(() -> onTick(e.getEntity()));
      }
    }
  }

  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
      event.getEntity().setMetadata("SpawnedBySpawner", new FixedMetadataValue(React.instance, true));
    }
  }

  public void onTick(Entity entity) {
    if (entity == null || entity.isDead()) {
      return;
    }

    Runnable tick = () -> {
      if (entity.isDead()) {
        return;
      }

      for (Entity nearby : entity.getNearbyEntities(searchRadius, searchRadius, searchRadius)) {
        if (merge(entity, nearby)) {
          return;
        }
      }
    };

    if (J.isFoliaThreading()) {
      if (J.isOwnedByCurrentRegion(entity)) {
        tick.run();
      } else {
        J.runEntity(entity, () -> onTick(entity));
      }
      return;
    }

    if (Bukkit.isPrimaryThread()) {
      tick.run();
      return;
    }

    J.s(tick);
  }

  @Override
  public void onDeactivate() {

  }

  @Override
  public int getTickInterval() {
    return -1;
  }

  @Override
  public void onTick() {

  }
}
