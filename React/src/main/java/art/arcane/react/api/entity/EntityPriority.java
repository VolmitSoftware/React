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

package art.arcane.react.api.entity;

import art.arcane.react.React;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.value.MaterialValue;
import art.arcane.volmlib.util.math.M;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public class EntityPriority {
  public static final double BASELINE = 100;
  private int consideredNewTicks = 20 * 60; // 1 minute
  private double oldMultiplier = 0.6;
  private double crowdMultiplier = 0.9;
  private double newMultiplier = 1.15;
  private double consumableMultiplier = 10;
  private double mechanicMultiplier = 25;
  private double lowTickMultiplier = 2;
  private double bossMultiplier = 20;
  private double movingMultiplier = 1.1;
  private double stationaryMultiplier = 3;
  private double highValueMultiplier = 7;
  private double ephemeralMultiplier = 30;
  private double lowValueMultiplier = 0.65;
  private double tameableMultiplier = 2.5;
  private double tamedMultiplier = 15;
  private double babyMultiplier = 1.4;
  private double adultMultiplier = 0.9;
  private double ambientMultiplier = 0.5;
  private double waterMultiplier = 0.75;
  private double projectileMultiplier = 1.5;
  private double vehicleMultiplier = 4.5;
  private double rideMultiplier = 1.08;
  private double monsterMultiplier = 1.25;
  private double passiveMultiplier = 1.15;
  private double villageMultiplier = 6.5;
  private double livingMultiplier = 5;
  private double fullHealthMultiplier = 0.75;
  private double lowHealthMultiplier = 1.35;
  private double nearbyPlayerMaxDistance = 64;
  private double nearbyPlayerMultiplier = 1.65;
  private double farPlayerMultiplier = 0.7;
  private double itemStackValueMultiplier = 1.5;
  private boolean useItemStackValueSystem = true;
  private transient Map<EntityType, Double> entityTypePriority = new HashMap<>();

  public EntityPriority() {
    entityTypePriority = buildPriority();
  }

  public void rebuildPriority() {
    entityTypePriority = buildPriority();
  }

  public void updateDistanceToPlayer(Entity e) {
    if (e == null) {
      return;
    }

    boolean folia = J.isFoliaThreading();
    if (folia && !J.isOwnedByCurrentRegion(e)) {
      J.runEntity(e, () -> updateDistanceToPlayer(e));
      return;
    }

    Location location = e.getLocation();
    NearbyPlayerIndexController playerIndex = React.controller(NearbyPlayerIndexController.class);
    double distance = playerIndex == null
        ? Double.POSITIVE_INFINITY
        : playerIndex.nearestDistanceSquared(location, nearbyPlayerMaxDistance);
    double d = farPlayerMultiplier;
    if (distance < nearbyPlayerMaxDistance * nearbyPlayerMaxDistance) {
      d = M.lerp(nearbyPlayerMultiplier, farPlayerMultiplier, M.lerpInverse(0, nearbyPlayerMaxDistance * nearbyPlayerMaxDistance, distance));
    }


    ReactEntity.setNearestPlayer(e, Math.max(Math.min(d, nearbyPlayerMultiplier), farPlayerMultiplier));
  }

  public void updateCrowd(Entity e) {
    if (e == null) {
      return;
    }

    boolean folia = J.isFoliaThreading();
    if (folia && !J.isOwnedByCurrentRegion(e)) {
      J.runEntity(e, () -> updateCrowd(e));
      return;
    }

    UUID sourceId = e.getUniqueId();
    List<Entity> ees = e.getNearbyEntities(8, 8, 8);
    double priority = getPriority(e);
    double minPriority = priority * 0.25;
    double maxPriority = priority * 1.15;
    double count = 1;

    for (Entity i : ees) {
      if (i == null) {
        continue;
      }

      if (folia && !J.isOwnedByCurrentRegion(i)) {
        continue;
      }

      if (sourceId.equals(i.getUniqueId())) {
        continue;
      }

      priority = getPriority(i);

      if (priority < minPriority || priority > maxPriority) {
        continue;
      }

      count += M.lerp(1.2, 0.8, M.lerpInverse(minPriority, maxPriority, priority));
    }

    ReactEntity.setCrowding(e, count);
  }

  private Map<EntityType, Double> buildPriority() {
    Map<EntityType, Double> p = new HashMap<>();

    for (EntityType i : EntityType.values()) {
      double v = BASELINE;
      switch (i) {
        case ITEM -> v *= consumableMultiplier;
        case EXPERIENCE_ORB, EGG ->
            v *= consumableMultiplier * lowValueMultiplier;
        case AREA_EFFECT_CLOUD ->
            v *= ambientMultiplier * lowTickMultiplier * ephemeralMultiplier;
        case ELDER_GUARDIAN -> v *= bossMultiplier * monsterMultiplier;
        case WITHER_SKELETON, ALLAY, PIGLIN_BRUTE, ZOGLIN, HOGLIN, RAVAGER,
             PILLAGER, SHULKER, GUARDIAN,
             ENDERMITE, WITCH, MAGMA_CUBE, BLAZE, SILVERFISH, CAVE_SPIDER,
             ENDERMAN, ZOMBIFIED_PIGLIN, GHAST, SLIME, ZOMBIE,
             GIANT, SPIDER, SKELETON, CREEPER, ILLUSIONER, VINDICATOR, VEX,
             EVOKER, HUSK, STRAY -> v *= monsterMultiplier;
        case LEASH_KNOT ->
            v *= mechanicMultiplier * lowTickMultiplier * stationaryMultiplier;
        case PAINTING ->
            v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
        case ARROW -> v *= projectileMultiplier * lowValueMultiplier;
        case SNOWBALL -> v *= projectileMultiplier * ephemeralMultiplier;
        case FIREBALL -> v *= projectileMultiplier * ephemeralMultiplier;
        case SMALL_FIREBALL -> v *= projectileMultiplier * ephemeralMultiplier;
        case ENDER_PEARL ->
            v *= projectileMultiplier * consumableMultiplier * ephemeralMultiplier;
        case EYE_OF_ENDER ->
            v *= projectileMultiplier * mechanicMultiplier * ephemeralMultiplier;
        case SPLASH_POTION ->
            v *= projectileMultiplier * consumableMultiplier * ephemeralMultiplier;
        case EXPERIENCE_BOTTLE ->
            v *= projectileMultiplier * highValueMultiplier * ephemeralMultiplier;
        case ITEM_FRAME ->
            v *= mechanicMultiplier * lowTickMultiplier * stationaryMultiplier;
        case WITHER_SKULL -> v *= projectileMultiplier * ephemeralMultiplier;
        case TNT -> v *= projectileMultiplier * ephemeralMultiplier;
        case FALLING_BLOCK ->
            v *= projectileMultiplier * ephemeralMultiplier * mechanicMultiplier;
        case FIREWORK_ROCKET ->
            v *= projectileMultiplier * ephemeralMultiplier * ephemeralMultiplier;
        case SPECTRAL_ARROW -> v *= projectileMultiplier * ephemeralMultiplier;
        case SHULKER_BULLET -> v *= projectileMultiplier * ephemeralMultiplier;
        case DRAGON_FIREBALL -> v *= projectileMultiplier * ephemeralMultiplier;
        case PIGLIN ->
            v *= villageMultiplier * monsterMultiplier * lowValueMultiplier;
        case ZOMBIE_VILLAGER -> v *= monsterMultiplier * villageMultiplier;
        case SKELETON_HORSE -> v *= monsterMultiplier * rideMultiplier;
        case ZOMBIE_HORSE -> v *= monsterMultiplier * rideMultiplier;
        case ARMOR_STAND ->
            v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
        case MINECART, ACACIA_BOAT, BAMBOO_RAFT, BIRCH_BOAT, CHERRY_BOAT, DARK_OAK_BOAT,
             JUNGLE_BOAT, MANGROVE_BOAT, OAK_BOAT, PALE_OAK_BOAT, SPRUCE_BOAT ->
            v *= vehicleMultiplier * rideMultiplier * mechanicMultiplier;
        case EVOKER_FANGS -> v *= projectileMultiplier * ephemeralMultiplier;
        case COMMAND_BLOCK_MINECART -> v *= vehicleMultiplier * mechanicMultiplier;
        case CHEST_MINECART ->
            v *= vehicleMultiplier * highValueMultiplier * mechanicMultiplier;
        case FURNACE_MINECART ->
            v *= vehicleMultiplier * mechanicMultiplier * highValueMultiplier;
        case TNT_MINECART -> v *= vehicleMultiplier * mechanicMultiplier;
        case HOPPER_MINECART ->
            v *= vehicleMultiplier * highValueMultiplier * mechanicMultiplier;
        case SPAWNER_MINECART ->
            v *= vehicleMultiplier * mechanicMultiplier;
        case ENDER_DRAGON, WITHER, WARDEN ->
            v *= bossMultiplier * monsterMultiplier * highValueMultiplier;
        case BAT -> v *= ambientMultiplier;
        case PIG, SNIFFER, CAMEL, FROG, GOAT, BEE, PANDA, TURTLE, POLAR_BEAR,
             RABBIT, SNOW_GOLEM, MOOSHROOM, CHICKEN, COW, SHEEP ->
            v *= passiveMultiplier;
        case SQUID -> v *= passiveMultiplier * waterMultiplier;
        case WOLF -> v *= passiveMultiplier * tameableMultiplier;
        case OCELOT -> v *= passiveMultiplier * tameableMultiplier;
        case IRON_GOLEM -> v *= passiveMultiplier * villageMultiplier;
        case HORSE, LLAMA, DONKEY, MULE ->
            v *= passiveMultiplier * rideMultiplier;
        case LLAMA_SPIT -> v *= projectileMultiplier * ephemeralMultiplier;
        case PARROT -> v *= passiveMultiplier * tameableMultiplier;
        case VILLAGER -> v *= passiveMultiplier * villageMultiplier;
        case END_CRYSTAL ->
            v *= bossMultiplier * lowTickMultiplier * mechanicMultiplier;
        case PHANTOM -> v *= monsterMultiplier * mechanicMultiplier;
        case TRIDENT ->
            v *= projectileMultiplier * highValueMultiplier * ephemeralMultiplier * lowTickMultiplier;
        case COD -> v *= passiveMultiplier * waterMultiplier;
        case SALMON -> v *= passiveMultiplier * waterMultiplier;
        case PUFFERFISH -> v *= passiveMultiplier * waterMultiplier;
        case TROPICAL_FISH -> v *= passiveMultiplier * waterMultiplier;
        case DROWNED -> v *= monsterMultiplier * waterMultiplier;
        case DOLPHIN ->
            v *= passiveMultiplier * waterMultiplier * tameableMultiplier;
        case CAT -> v *= passiveMultiplier * tameableMultiplier;
        case TRADER_LLAMA ->
            v *= passiveMultiplier * vehicleMultiplier * villageMultiplier * mechanicMultiplier;
        case WANDERING_TRADER ->
            v *= passiveMultiplier * villageMultiplier * mechanicMultiplier;
        case FOX -> v *= passiveMultiplier * tameableMultiplier;
        case STRIDER -> v *= passiveMultiplier * waterMultiplier;
        case AXOLOTL -> v *= passiveMultiplier * waterMultiplier;
        case GLOW_ITEM_FRAME ->
            v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
        case GLOW_SQUID ->
            v *= passiveMultiplier * waterMultiplier * highValueMultiplier;
        case MARKER ->
            v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
        case ACACIA_CHEST_BOAT, BAMBOO_CHEST_RAFT, BIRCH_CHEST_BOAT, CHERRY_CHEST_BOAT,
             DARK_OAK_CHEST_BOAT, JUNGLE_CHEST_BOAT, MANGROVE_CHEST_BOAT, OAK_CHEST_BOAT,
             PALE_OAK_CHEST_BOAT, SPRUCE_CHEST_BOAT -> v *= vehicleMultiplier * highValueMultiplier;
        case TADPOLE -> v *= passiveMultiplier * lowValueMultiplier;
        case FISHING_BOBBER ->
            v *= projectileMultiplier * ephemeralMultiplier * mechanicMultiplier;
        case LIGHTNING_BOLT -> v *= mechanicMultiplier * ephemeralMultiplier;
        case PLAYER, ITEM_DISPLAY, BLOCK_DISPLAY, TEXT_DISPLAY, INTERACTION ->
            v = -1;
        case UNKNOWN -> v *= 1;
      }

      p.put(i, v);
    }

    return p;
  }

  public double getAgeMultipler(int ticksLived) {
    if (ticksLived > consideredNewTicks) {
      return oldMultiplier;
    }

    return M.lerp(newMultiplier, oldMultiplier, ticksLived / (double) consideredNewTicks);
  }


  public double getPriority(EntityType e) {
    return entityTypePriority.getOrDefault(e, BASELINE);
  }

  public double getPriorityWithCrowd(Entity e, double c) {
    double p = getPriority(e) * ReactEntity.getNearestPlayer(e);

    if (c <= 1) {
      return p;
    }

    c -= 1;

    if (c < 1) {
      return p * crowdMultiplier;
    }

    if (c > 1) {
      return p * Math.pow(crowdMultiplier, c);
    }

    return p * crowdMultiplier;
  }

  public double getPriority(Entity e) {
    if (e == null) {
      return BASELINE;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(e)) {
      return getPriority(e.getType());
    }

    if (e instanceof Player) {
      return -1;
    }

    double buf = getPriority(e.getType());
    buf *= getAgeMultipler(e.getTicksLived());

    if (useItemStackValueSystem && e instanceof Item it) {
      ItemStack is = it.getItemStack();
      buf += MaterialValue.getValue(is.getType()) * is.getAmount() * itemStackValueMultiplier;
    }

    if (e instanceof LivingEntity l) {
      double d = Math.abs(l.getVelocity().length());

      if (d < 1) {
        buf *= M.lerp(1, movingMultiplier, d);
      }

      buf *= livingMultiplier;

      AttributeInstance maxHealthAttribute = l.getAttribute(Attribute.MAX_HEALTH);
      if (maxHealthAttribute == null) {
        return buf;
      }

      double maxHealth = maxHealthAttribute.getValue();
      double h = l.getHealth();

      if (h >= maxHealth) {
        buf *= fullHealthMultiplier;
      } else if (h <= 0) {
        buf *= lowHealthMultiplier;
      } else {
        buf *= M.lerp(lowHealthMultiplier, fullHealthMultiplier, h / maxHealth);
      }
    }

    if (e instanceof Tameable t) {
      buf *= tameableMultiplier;

      if (t.isTamed()) {
        buf *= tamedMultiplier;
      }
    }

    if (e instanceof Breedable a) {
      if (a.getAgeLock() || a.isAdult()) {
        buf *= adultMultiplier;
      } else if (!a.isAdult()) {
        buf *= babyMultiplier;
      }
    }

    return buf;
  }
}
