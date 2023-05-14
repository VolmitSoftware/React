package com.volmit.react.api.entity;

import com.volmit.react.util.math.M;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class EntityPriority {
    private static final double baseline = 100;
    private int consideredNewTicks = 20 * 60 * 5; // 5 minutes
    private double oldMultiplier = 0.85;
    private double newMultiplier = 2.5;
    private double consumableMultiplier = 10;
    private double mechanicMultiplier = 25;
    private double lowTickMultiplier = 2;
    private double bossMultiplier = 20;
    private double movingMultiplier = 2.5;
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
    private double vehicleMultiplier = 2.5;
    private double monsterMultiplier = 1.25;
    private double passiveMultiplier = 1.15;
    private double villageMultiplier = 6.5;
    private double livingMultiplier = 5;
    private double fullHealthMultiplier = 0.75;
    private double lowHealthMultiplier = 1.35;
    private Map<EntityType, Double> entityTypePriority = new HashMap<>();

    public EntityPriority() {
        entityTypePriority = buildPriority();
    }

    public void rebuildPriority() {
        entityTypePriority = buildPriority();
    }

    private Map<EntityType, Double> buildPriority() {
        Map<EntityType, Double> p = new HashMap<>();

        for(EntityType i : EntityType.values()) {
            double v = baseline;
            switch(i) {
                case DROPPED_ITEM -> v *= consumableMultiplier;
                case EXPERIENCE_ORB, EGG -> v *= consumableMultiplier * lowValueMultiplier;
                case AREA_EFFECT_CLOUD -> v *= ambientMultiplier * lowTickMultiplier * ephemeralMultiplier;
                case ELDER_GUARDIAN -> v *= bossMultiplier * monsterMultiplier;
                case WITHER_SKELETON, WARDEN, ALLAY, PIGLIN_BRUTE, ZOGLIN, PIGLIN, HOGLIN, RAVAGER, PILLAGER, SHULKER, GUARDIAN,
                    ENDERMITE, WITCH, MAGMA_CUBE, BLAZE, SILVERFISH, CAVE_SPIDER, ENDERMAN, ZOMBIFIED_PIGLIN, GHAST, SLIME, ZOMBIE,
                    GIANT, SPIDER, SKELETON, CREEPER, ILLUSIONER, VINDICATOR, VEX, EVOKER, HUSK, STRAY -> v *= monsterMultiplier;
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
                case ZOMBIE_VILLAGER -> v *= monsterMultiplier * villageMultiplier;
                case SKELETON_HORSE -> v *= monsterMultiplier * vehicleMultiplier;
                case ZOMBIE_HORSE -> v *= monsterMultiplier * vehicleMultiplier;
                case ARMOR_STAND -> v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
                case DONKEY, MINECART, BOAT, MULE -> v *= vehicleMultiplier;
                case EVOKER_FANGS -> v *= projectileMultiplier * ephemeralMultiplier;
                case MINECART_COMMAND -> v *= vehicleMultiplier * mechanicMultiplier;
                case MINECART_CHEST -> v *= vehicleMultiplier * highValueMultiplier * mechanicMultiplier;
                case MINECART_FURNACE -> v *= vehicleMultiplier * mechanicMultiplier * highValueMultiplier;
                case MINECART_TNT -> v *= vehicleMultiplier * mechanicMultiplier;
                case MINECART_HOPPER -> v *= vehicleMultiplier * highValueMultiplier * mechanicMultiplier;
                case MINECART_MOB_SPAWNER -> v *= vehicleMultiplier * mechanicMultiplier;
                case ENDER_DRAGON, WITHER -> v *= bossMultiplier;
                case BAT -> v *= ambientMultiplier;
                case PIG, SNIFFER, CAMEL, FROG, GOAT, BEE, PANDA, TURTLE, POLAR_BEAR,
                    RABBIT, SNOWMAN, MUSHROOM_COW, CHICKEN, COW, SHEEP -> v *= passiveMultiplier;
                case SQUID -> v *= passiveMultiplier * waterMultiplier;
                case WOLF -> v *= passiveMultiplier * tameableMultiplier;
                case OCELOT -> v *= passiveMultiplier * tameableMultiplier;
                case IRON_GOLEM -> v *= passiveMultiplier * villageMultiplier;
                case HORSE -> v *= passiveMultiplier * vehicleMultiplier;
                case LLAMA -> v *= passiveMultiplier * vehicleMultiplier;
                case LLAMA_SPIT -> v *= projectileMultiplier * ephemeralMultiplier;
                case PARROT -> v *= passiveMultiplier * tameableMultiplier;
                case VILLAGER -> v *= passiveMultiplier * villageMultiplier;
                case ENDER_CRYSTAL -> v *= bossMultiplier * lowTickMultiplier * mechanicMultiplier;
                case PHANTOM -> v *= monsterMultiplier * mechanicMultiplier;
                case TRIDENT -> v *= projectileMultiplier * highValueMultiplier * ephemeralMultiplier * lowTickMultiplier;
                case COD -> v *= passiveMultiplier * waterMultiplier;
                case SALMON -> v *= passiveMultiplier * waterMultiplier;
                case PUFFERFISH -> v *= passiveMultiplier * waterMultiplier;
                case TROPICAL_FISH -> v *= passiveMultiplier * waterMultiplier;
                case DROWNED -> v *= monsterMultiplier * waterMultiplier;
                case DOLPHIN -> v *= passiveMultiplier * waterMultiplier * tameableMultiplier;
                case CAT -> v *= passiveMultiplier * tameableMultiplier;
                case TRADER_LLAMA -> v *= passiveMultiplier * vehicleMultiplier * villageMultiplier * mechanicMultiplier;
                case WANDERING_TRADER -> v *= passiveMultiplier * villageMultiplier * mechanicMultiplier;
                case FOX -> v *= passiveMultiplier * tameableMultiplier;
                case STRIDER -> v *= passiveMultiplier * waterMultiplier;
                case AXOLOTL -> v *= passiveMultiplier * waterMultiplier;
                case GLOW_ITEM_FRAME -> v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
                case GLOW_SQUID -> v *= passiveMultiplier * waterMultiplier * highValueMultiplier;
                case MARKER -> v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
                case CHEST_BOAT -> v *= vehicleMultiplier * highValueMultiplier;
                case TADPOLE -> v *= passiveMultiplier * lowValueMultiplier;
                case BLOCK_DISPLAY -> v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
                case INTERACTION -> v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
                case ITEM_DISPLAY -> v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
                case TEXT_DISPLAY -> v *= lowTickMultiplier * stationaryMultiplier * mechanicMultiplier;
                case FISHING_HOOK -> v *= projectileMultiplier * ephemeralMultiplier * mechanicMultiplier;
                case LIGHTNING -> v *= mechanicMultiplier * ephemeralMultiplier;
                case PLAYER -> v *= mechanicMultiplier * ephemeralMultiplier;
                case UNKNOWN -> v *= 1;
            }
        }

        return p;
    }

    public double getAgeMultipler(int ticksLived)
    {
        if(ticksLived > consideredNewTicks) {
            return oldMultiplier;
        }

        return M.lerp(newMultiplier, oldMultiplier, ticksLived / (double) consideredNewTicks);
    }

    public double getPriority(Entity e) {
        double buf = entityTypePriority.getOrDefault(e.getType(), baseline);
        buf *= getAgeMultipler(e.getTicksLived());

        if(e instanceof Player) {
            return -1;
        }


        if(e instanceof LivingEntity l) {
            double d = Math.abs(l.getVelocity().length());

            if(d < 1) {
                buf *= M.lerp(1, movingMultiplier, d);
            }
        }

        if(e instanceof LivingEntity l) {
            buf *= livingMultiplier;

            double maxHealth = l.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            double h = l.getHealth();

            if(h >= maxHealth) {
                buf *= fullHealthMultiplier;
            }

            else if(h <= 0){
                buf *= lowHealthMultiplier;
            }

            else {
                buf *= M.lerp(lowHealthMultiplier, fullHealthMultiplier, h / maxHealth);
            }
        }

        if(e instanceof Tameable t) {
            buf *= tameableMultiplier;

            if(t.isTamed()) {
                buf *= tamedMultiplier;
            }
        }

        if(e instanceof Breedable a ) {
            if(a.getAgeLock() || a.isAdult()) {
                buf *= adultMultiplier;
            }

            else if(!a.isAdult()) {
                buf *= babyMultiplier;
            }
        }

        return buf;
    }
}
