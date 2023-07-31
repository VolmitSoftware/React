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

package com.volmit.react.api.entity;

import com.volmit.react.model.ReactConfiguration;
import com.volmit.react.model.ReactEntity;
import com.volmit.react.util.math.M;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.value.MaterialValue;
import lombok.Getter;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        double distance = Double.MAX_VALUE;
        double d;
        for (Player i : e.getWorld().getPlayers()) {
            d = i.getLocation().distanceSquared(e.getLocation());

            if (d < distance) {
                distance = d;
            }
        }

        d = farPlayerMultiplier;
        if (distance < nearbyPlayerMaxDistance * nearbyPlayerMaxDistance) {
            d = M.lerp(nearbyPlayerMultiplier, farPlayerMultiplier, M.lerpInverse(0, nearbyPlayerMaxDistance * nearbyPlayerMaxDistance, distance));
        }


        ReactEntity.setNearestPlayer(e, Math.max(Math.min(d, nearbyPlayerMultiplier), farPlayerMultiplier));
    }

    public void updateCrowd(Entity e) {
        J.s(() -> {
            List<Entity> ees = e.getNearbyEntities(8, 8, 8);

            J.a(() -> {
                double priority = getPriority(e);
                double minPriority = priority * 0.25;
                double maxPriority = priority * 1.15;
                double count = 1;

                for (Entity i : ees) {
                    if (i.getUniqueId().equals(e.getUniqueId())) {
                        continue;
                    }

                    priority = getPriority(i);

                    if (priority < minPriority || priority > maxPriority) {
                        continue;
                    }

                    count += M.lerp(1.2, 0.8, M.lerpInverse(minPriority, maxPriority, priority));
                }

                ReactEntity.setCrowding(e, count);
            });
        });
    }

    private Map<EntityType, Double> buildPriority() {
        Map<EntityType, Double> p = new HashMap<>();

        for (EntityType i : EntityType.values()) {
            double v = BASELINE;
            switch (i) {
                case DROPPED_ITEM -> v *= consumableMultiplier;
                case EXPERIENCE_ORB, EGG -> v *= consumableMultiplier * lowValueMultiplier;
                case AREA_EFFECT_CLOUD -> v *= ambientMultiplier * lowTickMultiplier * ephemeralMultiplier;
                case ELDER_GUARDIAN -> v *= bossMultiplier * monsterMultiplier;
                case WITHER_SKELETON, ALLAY, PIGLIN_BRUTE, ZOGLIN, HOGLIN, RAVAGER, PILLAGER, SHULKER, GUARDIAN,
                        ENDERMITE, WITCH, MAGMA_CUBE, BLAZE, SILVERFISH, CAVE_SPIDER, ENDERMAN, ZOMBIFIED_PIGLIN, GHAST, SLIME, ZOMBIE,
                        GIANT, SPIDER, SKELETON, CREEPER, ILLUSIONER, VINDICATOR, VEX, EVOKER, HUSK, STRAY ->
                        v *= monsterMultiplier;
                case LEASH_HITCH -> v *= mechanicMultiplier * lowTickMultiplier * stationaryMultiplier;
                case PAINTING -> v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
                case ARROW -> v *= projectileMultiplier * lowValueMultiplier;
                case SNOWBALL -> v *= projectileMultiplier * ephemeralMultiplier;
                case FIREBALL -> v *= projectileMultiplier * ephemeralMultiplier;
                case SMALL_FIREBALL -> v *= projectileMultiplier * ephemeralMultiplier;
                case ENDER_PEARL -> v *= projectileMultiplier * consumableMultiplier * ephemeralMultiplier;
                case ENDER_SIGNAL -> v *= projectileMultiplier * mechanicMultiplier * ephemeralMultiplier;
                case SPLASH_POTION -> v *= projectileMultiplier * consumableMultiplier * ephemeralMultiplier;
                case THROWN_EXP_BOTTLE -> v *= projectileMultiplier * highValueMultiplier * ephemeralMultiplier;
                case ITEM_FRAME -> v *= mechanicMultiplier * lowTickMultiplier * stationaryMultiplier;
                case WITHER_SKULL -> v *= projectileMultiplier * ephemeralMultiplier;
                case PRIMED_TNT -> v *= projectileMultiplier * ephemeralMultiplier;
                case FALLING_BLOCK -> v *= projectileMultiplier * ephemeralMultiplier * mechanicMultiplier;
                case FIREWORK -> v *= projectileMultiplier * ephemeralMultiplier * ephemeralMultiplier;
                case SPECTRAL_ARROW -> v *= projectileMultiplier * ephemeralMultiplier;
                case SHULKER_BULLET -> v *= projectileMultiplier * ephemeralMultiplier;
                case DRAGON_FIREBALL -> v *= projectileMultiplier * ephemeralMultiplier;
                case PIGLIN -> v *= villageMultiplier * monsterMultiplier * lowValueMultiplier;
                case ZOMBIE_VILLAGER -> v *= monsterMultiplier * villageMultiplier;
                case SKELETON_HORSE -> v *= monsterMultiplier * rideMultiplier;
                case ZOMBIE_HORSE -> v *= monsterMultiplier * rideMultiplier;
                case ARMOR_STAND -> v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
                case MINECART, BOAT -> v *= vehicleMultiplier * rideMultiplier * mechanicMultiplier;
                case EVOKER_FANGS -> v *= projectileMultiplier * ephemeralMultiplier;
                case MINECART_COMMAND -> v *= vehicleMultiplier * mechanicMultiplier;
                case MINECART_CHEST -> v *= vehicleMultiplier * highValueMultiplier * mechanicMultiplier;
                case MINECART_FURNACE -> v *= vehicleMultiplier * mechanicMultiplier * highValueMultiplier;
                case MINECART_TNT -> v *= vehicleMultiplier * mechanicMultiplier;
                case MINECART_HOPPER -> v *= vehicleMultiplier * highValueMultiplier * mechanicMultiplier;
                case MINECART_MOB_SPAWNER -> v *= vehicleMultiplier * mechanicMultiplier;
                case ENDER_DRAGON, WITHER, WARDEN -> v *= bossMultiplier * monsterMultiplier * highValueMultiplier;
                case BAT -> v *= ambientMultiplier;
                case PIG, SNIFFER, CAMEL, FROG, GOAT, BEE, PANDA, TURTLE, POLAR_BEAR,
                        RABBIT, SNOWMAN, MUSHROOM_COW, CHICKEN, COW, SHEEP -> v *= passiveMultiplier;
                case SQUID -> v *= passiveMultiplier * waterMultiplier;
                case WOLF -> v *= passiveMultiplier * tameableMultiplier;
                case OCELOT -> v *= passiveMultiplier * tameableMultiplier;
                case IRON_GOLEM -> v *= passiveMultiplier * villageMultiplier;
                case HORSE, LLAMA, DONKEY, MULE -> v *= passiveMultiplier * rideMultiplier;
                case LLAMA_SPIT -> v *= projectileMultiplier * ephemeralMultiplier;
                case PARROT -> v *= passiveMultiplier * tameableMultiplier;
                case VILLAGER -> v *= passiveMultiplier * villageMultiplier;
                case ENDER_CRYSTAL -> v *= bossMultiplier * lowTickMultiplier * mechanicMultiplier;
                case PHANTOM -> v *= monsterMultiplier * mechanicMultiplier;
                case TRIDENT ->
                        v *= projectileMultiplier * highValueMultiplier * ephemeralMultiplier * lowTickMultiplier;
                case COD -> v *= passiveMultiplier * waterMultiplier;
                case SALMON -> v *= passiveMultiplier * waterMultiplier;
                case PUFFERFISH -> v *= passiveMultiplier * waterMultiplier;
                case TROPICAL_FISH -> v *= passiveMultiplier * waterMultiplier;
                case DROWNED -> v *= monsterMultiplier * waterMultiplier;
                case DOLPHIN -> v *= passiveMultiplier * waterMultiplier * tameableMultiplier;
                case CAT -> v *= passiveMultiplier * tameableMultiplier;
                case TRADER_LLAMA ->
                        v *= passiveMultiplier * vehicleMultiplier * villageMultiplier * mechanicMultiplier;
                case WANDERING_TRADER -> v *= passiveMultiplier * villageMultiplier * mechanicMultiplier;
                case FOX -> v *= passiveMultiplier * tameableMultiplier;
                case STRIDER -> v *= passiveMultiplier * waterMultiplier;
                case AXOLOTL -> v *= passiveMultiplier * waterMultiplier;
                case GLOW_ITEM_FRAME -> v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
                case GLOW_SQUID -> v *= passiveMultiplier * waterMultiplier * highValueMultiplier;
                case MARKER -> v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
                case CHEST_BOAT -> v *= vehicleMultiplier * highValueMultiplier;
                case TADPOLE -> v *= passiveMultiplier * lowValueMultiplier;
                case FISHING_HOOK -> v *= projectileMultiplier * ephemeralMultiplier * mechanicMultiplier;
                case LIGHTNING -> v *= mechanicMultiplier * ephemeralMultiplier;
                case PLAYER, ITEM_DISPLAY, BLOCK_DISPLAY, TEXT_DISPLAY, INTERACTION -> v = -1;
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
        double buf = getPriority(e.getType());
        buf *= getAgeMultipler(e.getTicksLived());

        if (e instanceof Player) {
            return -1;
        }

        if (ReactConfiguration.get().getPriority().isUseItemStackValueSystem() && e instanceof Item it) {
            ItemStack is = it.getItemStack();
            double s = MaterialValue.getValue(is.getType()) * is.getAmount() * ReactConfiguration.get().getPriority().itemStackValueMultiplier;
            buf += s;
        }

        if (e instanceof LivingEntity l) {
            double d = Math.abs(l.getVelocity().length());

            if (d < 1) {
                buf *= M.lerp(1, movingMultiplier, d);
            }
        }

        if (e instanceof LivingEntity l) {
            buf *= livingMultiplier;

            double maxHealth = l.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
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
