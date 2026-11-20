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

import art.arcane.react.model.ReactEntity;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.math.M;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Getter
public class EntityPriority {
  public static final double BASELINE = 100;
  private static final EntityPriority DEFAULTS = new EntityPriority();
  private static volatile Supplier<EntityPriority> source;
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
  private transient Map<String, Double> entityTypePriority = new HashMap<>();

  public EntityPriority() {
    entityTypePriority = buildPriority();
  }

  /**
   * The priority model the server runs with: the one the platform bound, or the defaults before that.
   */
  public static EntityPriority active() {
    Supplier<EntityPriority> bound = source;
    EntityPriority priority = bound == null ? null : bound.get();
    return priority == null ? DEFAULTS : priority;
  }

  /**
   * Binds the live model; null restores the defaults.
   */
  public static void bind(Supplier<EntityPriority> supplier) {
    source = supplier;
  }

  public void rebuildPriority() {
    entityTypePriority = buildPriority();
  }

  public void updateDistanceToPlayer(GameEntity e) {
    if (e == null) {
      return;
    }

    boolean folia = J.isFoliaThreading();
    if (folia && !J.isOwnedByCurrentRegion(e.handle())) {
      J.runEntity(e.handle(), () -> updateDistanceToPlayer(e));
      return;
    }

    EntityHost host = host();
    double distance = host == null
        ? Double.POSITIVE_INFINITY
        : host.nearestPlayerDistanceSquared(e, nearbyPlayerMaxDistance);
    double d = farPlayerMultiplier;
    if (distance < nearbyPlayerMaxDistance * nearbyPlayerMaxDistance) {
      d = M.lerp(nearbyPlayerMultiplier, farPlayerMultiplier, M.lerpInverse(0, nearbyPlayerMaxDistance * nearbyPlayerMaxDistance, distance));
    }

    ReactEntity.setNearestPlayer(e, Math.max(Math.min(d, nearbyPlayerMultiplier), farPlayerMultiplier));
  }

  public void updateCrowd(GameEntity e, double priority, long nowMs) {
    if (e == null) {
      return;
    }

    boolean folia = J.isFoliaThreading();
    if (folia && !J.isOwnedByCurrentRegion(e.handle())) {
      J.runEntity(e.handle(), () -> updateCrowd(e, priority, nowMs));
      return;
    }

    EntityHost host = host();
    UUID sourceId = e.id();
    List<GameEntity> ees = host == null ? List.of() : host.nearbyEntities(e, 8);
    double minPriority = priority * 0.25;
    double maxPriority = priority * 1.15;
    double count = 1;

    for (GameEntity i : ees) {
      if (i == null) {
        continue;
      }

      if (folia && !J.isOwnedByCurrentRegion(i.handle())) {
        continue;
      }

      if (sourceId.equals(i.id())) {
        continue;
      }

      double neighbourPriority = getCachedPriority(i, nowMs);

      if (neighbourPriority < minPriority || neighbourPriority > maxPriority) {
        continue;
      }

      count += M.lerp(1.2, 0.8, M.lerpInverse(minPriority, maxPriority, neighbourPriority));
    }

    ReactEntity.setCrowding(e, count);
  }

  private Map<String, Double> buildPriority() {
    Map<String, Double> p = new HashMap<>();
    put(p, BASELINE * consumableMultiplier, "ITEM");
    put(p, BASELINE * (consumableMultiplier * lowValueMultiplier), "EXPERIENCE_ORB", "EGG");
    put(p, BASELINE * (ambientMultiplier * lowTickMultiplier * ephemeralMultiplier), "AREA_EFFECT_CLOUD");
    put(p, BASELINE * (bossMultiplier * monsterMultiplier), "ELDER_GUARDIAN");
    put(p, BASELINE * monsterMultiplier, "WITHER_SKELETON", "ALLAY", "PIGLIN_BRUTE", "ZOGLIN", "HOGLIN", "RAVAGER",
        "PILLAGER", "SHULKER", "GUARDIAN", "ENDERMITE", "WITCH", "MAGMA_CUBE", "BLAZE", "SILVERFISH", "CAVE_SPIDER",
        "ENDERMAN", "ZOMBIFIED_PIGLIN", "GHAST", "SLIME", "ZOMBIE", "GIANT", "SPIDER", "SKELETON", "CREEPER",
        "ILLUSIONER", "VINDICATOR", "VEX", "EVOKER", "HUSK", "STRAY");
    put(p, BASELINE * (mechanicMultiplier * lowTickMultiplier * stationaryMultiplier), "LEASH_KNOT");
    put(p, BASELINE * (lowTickMultiplier * stationaryMultiplier * mechanicMultiplier), "PAINTING");
    put(p, BASELINE * (projectileMultiplier * lowValueMultiplier), "ARROW");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier), "SNOWBALL");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier), "FIREBALL");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier), "SMALL_FIREBALL");
    put(p, BASELINE * (projectileMultiplier * consumableMultiplier * ephemeralMultiplier), "ENDER_PEARL");
    put(p, BASELINE * (projectileMultiplier * mechanicMultiplier * ephemeralMultiplier), "EYE_OF_ENDER");
    put(p, BASELINE * (projectileMultiplier * consumableMultiplier * ephemeralMultiplier), "SPLASH_POTION");
    put(p, BASELINE * (projectileMultiplier * highValueMultiplier * ephemeralMultiplier), "EXPERIENCE_BOTTLE");
    put(p, BASELINE * (mechanicMultiplier * lowTickMultiplier * stationaryMultiplier), "ITEM_FRAME");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier), "WITHER_SKULL");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier), "TNT");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier * mechanicMultiplier), "FALLING_BLOCK");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier * ephemeralMultiplier), "FIREWORK_ROCKET");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier), "SPECTRAL_ARROW");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier), "SHULKER_BULLET");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier), "DRAGON_FIREBALL");
    put(p, BASELINE * (villageMultiplier * monsterMultiplier * lowValueMultiplier), "PIGLIN");
    put(p, BASELINE * (monsterMultiplier * villageMultiplier), "ZOMBIE_VILLAGER");
    put(p, BASELINE * (monsterMultiplier * rideMultiplier), "SKELETON_HORSE");
    put(p, BASELINE * (monsterMultiplier * rideMultiplier), "ZOMBIE_HORSE");
    put(p, BASELINE * (lowTickMultiplier * stationaryMultiplier * mechanicMultiplier), "ARMOR_STAND");
    put(p, BASELINE * (vehicleMultiplier * rideMultiplier * mechanicMultiplier), "MINECART", "ACACIA_BOAT",
        "BAMBOO_RAFT", "BIRCH_BOAT", "CHERRY_BOAT", "DARK_OAK_BOAT", "JUNGLE_BOAT", "MANGROVE_BOAT", "OAK_BOAT",
        "PALE_OAK_BOAT", "SPRUCE_BOAT");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier), "EVOKER_FANGS");
    put(p, BASELINE * (vehicleMultiplier * mechanicMultiplier), "COMMAND_BLOCK_MINECART");
    put(p, BASELINE * (vehicleMultiplier * highValueMultiplier * mechanicMultiplier), "CHEST_MINECART");
    put(p, BASELINE * (vehicleMultiplier * mechanicMultiplier * highValueMultiplier), "FURNACE_MINECART");
    put(p, BASELINE * (vehicleMultiplier * mechanicMultiplier), "TNT_MINECART");
    put(p, BASELINE * (vehicleMultiplier * highValueMultiplier * mechanicMultiplier), "HOPPER_MINECART");
    put(p, BASELINE * (vehicleMultiplier * mechanicMultiplier), "SPAWNER_MINECART");
    put(p, BASELINE * (bossMultiplier * monsterMultiplier * highValueMultiplier), "ENDER_DRAGON", "WITHER", "WARDEN");
    put(p, BASELINE * ambientMultiplier, "BAT");
    put(p, BASELINE * passiveMultiplier, "PIG", "SNIFFER", "CAMEL", "FROG", "GOAT", "BEE", "PANDA", "TURTLE",
        "POLAR_BEAR", "RABBIT", "SNOW_GOLEM", "MOOSHROOM", "CHICKEN", "COW", "SHEEP");
    put(p, BASELINE * (passiveMultiplier * waterMultiplier), "SQUID");
    put(p, BASELINE * (passiveMultiplier * tameableMultiplier), "WOLF");
    put(p, BASELINE * (passiveMultiplier * tameableMultiplier), "OCELOT");
    put(p, BASELINE * (passiveMultiplier * villageMultiplier), "IRON_GOLEM");
    put(p, BASELINE * (passiveMultiplier * rideMultiplier), "HORSE", "LLAMA", "DONKEY", "MULE");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier), "LLAMA_SPIT");
    put(p, BASELINE * (passiveMultiplier * tameableMultiplier), "PARROT");
    put(p, BASELINE * (passiveMultiplier * villageMultiplier), "VILLAGER");
    put(p, BASELINE * (bossMultiplier * lowTickMultiplier * mechanicMultiplier), "END_CRYSTAL");
    put(p, BASELINE * (monsterMultiplier * mechanicMultiplier), "PHANTOM");
    put(p, BASELINE * (projectileMultiplier * highValueMultiplier * ephemeralMultiplier * lowTickMultiplier),
        "TRIDENT");
    put(p, BASELINE * (passiveMultiplier * waterMultiplier), "COD");
    put(p, BASELINE * (passiveMultiplier * waterMultiplier), "SALMON");
    put(p, BASELINE * (passiveMultiplier * waterMultiplier), "PUFFERFISH");
    put(p, BASELINE * (passiveMultiplier * waterMultiplier), "TROPICAL_FISH");
    put(p, BASELINE * (monsterMultiplier * waterMultiplier), "DROWNED");
    put(p, BASELINE * (passiveMultiplier * waterMultiplier * tameableMultiplier), "DOLPHIN");
    put(p, BASELINE * (passiveMultiplier * tameableMultiplier), "CAT");
    put(p, BASELINE * (passiveMultiplier * vehicleMultiplier * villageMultiplier * mechanicMultiplier),
        "TRADER_LLAMA");
    put(p, BASELINE * (passiveMultiplier * villageMultiplier * mechanicMultiplier), "WANDERING_TRADER");
    put(p, BASELINE * (passiveMultiplier * tameableMultiplier), "FOX");
    put(p, BASELINE * (passiveMultiplier * waterMultiplier), "STRIDER");
    put(p, BASELINE * (passiveMultiplier * waterMultiplier), "AXOLOTL");
    put(p, BASELINE * (lowTickMultiplier * stationaryMultiplier * mechanicMultiplier), "GLOW_ITEM_FRAME");
    put(p, BASELINE * (passiveMultiplier * waterMultiplier * highValueMultiplier), "GLOW_SQUID");
    put(p, BASELINE * (lowTickMultiplier * stationaryMultiplier * mechanicMultiplier), "MARKER");
    put(p, BASELINE * (vehicleMultiplier * highValueMultiplier), "ACACIA_CHEST_BOAT", "BAMBOO_CHEST_RAFT",
        "BIRCH_CHEST_BOAT", "CHERRY_CHEST_BOAT", "DARK_OAK_CHEST_BOAT", "JUNGLE_CHEST_BOAT", "MANGROVE_CHEST_BOAT",
        "OAK_CHEST_BOAT", "PALE_OAK_CHEST_BOAT", "SPRUCE_CHEST_BOAT");
    put(p, BASELINE * (passiveMultiplier * lowValueMultiplier), "TADPOLE");
    put(p, BASELINE * (projectileMultiplier * ephemeralMultiplier * mechanicMultiplier), "FISHING_BOBBER");
    put(p, BASELINE * (mechanicMultiplier * ephemeralMultiplier), "LIGHTNING_BOLT");
    put(p, -1, "PLAYER", "ITEM_DISPLAY", "BLOCK_DISPLAY", "TEXT_DISPLAY", "INTERACTION");
    return p;
  }

  private static void put(Map<String, Double> priorities, double value, String... types) {
    for (String type : types) {
      priorities.put(type, value);
    }
  }

  public double getAgeMultipler(int ticksLived) {
    if (ticksLived > consideredNewTicks) {
      return oldMultiplier;
    }

    return M.lerp(newMultiplier, oldMultiplier, ticksLived / (double) consideredNewTicks);
  }

  /**
   * The base priority of an entity type, named as {@link GameEntity#type()} names it.
   */
  public double getPriority(String type) {
    return type == null ? BASELINE : entityTypePriority.getOrDefault(type, BASELINE);
  }

  public double getPriorityWithCrowd(GameEntity e, double priority, double c) {
    double p = priority * ReactEntity.getNearestPlayer(e);

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

  public double getPriority(GameEntity e) {
    if (e == null) {
      return BASELINE;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(e.handle())) {
      return getPriority(e.type());
    }

    if (e.isPlayer()) {
      return -1;
    }

    double buf = getPriority(e.type());
    buf *= getAgeMultipler(e.ticksLived());

    if (useItemStackValueSystem) {
      EntityHost host = host();
      if (host != null) {
        buf += host.itemValue(e) * itemStackValueMultiplier;
      }
    }

    if (e.isLiving()) {
      double d = Math.abs(e.speed());

      if (d < 1) {
        buf *= M.lerp(1, movingMultiplier, d);
      }

      buf *= livingMultiplier;

      double maxHealth = e.maxHealth();
      if (Double.isNaN(maxHealth)) {
        return buf;
      }

      double h = e.health();

      if (h >= maxHealth) {
        buf *= fullHealthMultiplier;
      } else if (h <= 0) {
        buf *= lowHealthMultiplier;
      } else {
        buf *= M.lerp(lowHealthMultiplier, fullHealthMultiplier, h / maxHealth);
      }
    }

    if (e.isTameable()) {
      buf *= tameableMultiplier;

      if (e.isTamed()) {
        buf *= tamedMultiplier;
      }
    }

    if (e.isBreedable()) {
      if (e.isAgeLocked() || e.isAdult()) {
        buf *= adultMultiplier;
      } else {
        buf *= babyMultiplier;
      }
    }

    return buf;
  }

  private double getCachedPriority(GameEntity e, long nowMs) {
    double cached = ReactEntity.getRawPriority(e, nowMs);
    if (!Double.isNaN(cached)) {
      return cached;
    }

    double priority = getPriority(e);
    ReactEntity.setRawPriority(e, priority, nowMs);
    return priority;
  }

  private static EntityHost host() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? null : platform.entityHost().orElse(null);
  }
}
