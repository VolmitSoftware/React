package com.volmit.react.api.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

public class EntityPriority {
    private double consumableMultiplier = 10;
    private double mechanicMultiplier = 25;
    private double livingMultiplier = 5;
    private double lowTickMultiplier = 2;
    private double bossMultiplier = 20;
    private double stationaryMultiplier = 3;
    private double highValueMultiplier = 7;
    private double ephemeralMultiplier = 30;
    private double lowValueMultiplier = 0.65;
    private double tameableMultiplier = 2.5;
    private double ambientMultiplier = 0.5;
    private double waterMultiplier = 0.75;
    private double projectileMultiplier = 1.5;
    private double vehicleMultiplier = 2.5;
    private double monsterMultiplier = 1.25;
    private double passiveMultiplier = 1.15;
    private double villageMultiplier = 6.5;
    private Map<EntityType, Double> entityTypePriority = new HashMap<>();

    private Map<EntityType, Double> buildPriority() {
        Map<EntityType, Double> p = new HashMap<>();

        for(EntityType i : EntityType.values()) {
            double v = 1000;
            switch(i) {
                case DROPPED_ITEM -> v *= consumableMultiplier;
                case EXPERIENCE_ORB -> v *= consumableMultiplier * lowValueMultiplier;
                case AREA_EFFECT_CLOUD -> v *= ambientMultiplier * lowTickMultiplier * ephemeralMultiplier;
                case ELDER_GUARDIAN -> v *= bossMultiplier * monsterMultiplier;
                case WITHER_SKELETON -> v *= monsterMultiplier;
                case STRAY -> v *= monsterMultiplier;
                case EGG -> v *= consumableMultiplier * lowValueMultiplier;
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
                case HUSK -> v *= monsterMultiplier;
                case SPECTRAL_ARROW -> v *= projectileMultiplier * ephemeralMultiplier;
                case SHULKER_BULLET -> v *= projectileMultiplier * ephemeralMultiplier;
                case DRAGON_FIREBALL -> v *= 1;
                case ZOMBIE_VILLAGER -> v *= 1;
                case SKELETON_HORSE -> v *= 1;
                case ZOMBIE_HORSE -> v *= 1;
                case ARMOR_STAND -> v *= 1;
                case DONKEY -> v *= 1;
                case MULE -> v *= 1;
                case EVOKER_FANGS -> v *= 1;
                case EVOKER -> v *= 1;
                case VEX -> v *= 1;
                case VINDICATOR -> v *= 1;
                case ILLUSIONER -> v *= 1;
                case MINECART_COMMAND -> v *= 1;
                case BOAT -> v *= 1;
                case MINECART -> v *= 1;
                case MINECART_CHEST -> v *= 1;
                case MINECART_FURNACE -> v *= 1;
                case MINECART_TNT -> v *= 1;
                case MINECART_HOPPER -> v *= 1;
                case MINECART_MOB_SPAWNER -> v *= 1;
                case CREEPER -> v *= 1;
                case SKELETON -> v *= 1;
                case SPIDER -> v *= 1;
                case GIANT -> v *= 1;
                case ZOMBIE -> v *= 1;
                case SLIME -> v *= 1;
                case GHAST -> v *= 1;
                case ZOMBIFIED_PIGLIN -> v *= 1;
                case ENDERMAN -> v *= 1;
                case CAVE_SPIDER -> v *= 1;
                case SILVERFISH -> v *= 1;
                case BLAZE -> v *= 1;
                case MAGMA_CUBE -> v *= 1;
                case ENDER_DRAGON -> v *= 1;
                case WITHER -> v *= 1;
                case BAT -> v *= 1;
                case WITCH -> v *= 1;
                case ENDERMITE -> v *= 1;
                case GUARDIAN -> v *= 1;
                case SHULKER -> v *= 1;
                case PIG -> v *= 1;
                case SHEEP -> v *= 1;
                case COW -> v *= 1;
                case CHICKEN -> v *= 1;
                case SQUID -> v *= 1;
                case WOLF -> v *= 1;
                case MUSHROOM_COW -> v *= 1;
                case SNOWMAN -> v *= 1;
                case OCELOT -> v *= 1;
                case IRON_GOLEM -> v *= 1;
                case HORSE -> v *= 1;
                case RABBIT -> v *= 1;
                case POLAR_BEAR -> v *= 1;
                case LLAMA -> v *= 1;
                case LLAMA_SPIT -> v *= 1;
                case PARROT -> v *= 1;
                case VILLAGER -> v *= 1;
                case ENDER_CRYSTAL -> v *= 1;
                case TURTLE -> v *= 1;
                case PHANTOM -> v *= 1;
                case TRIDENT -> v *= 1;
                case COD -> v *= 1;
                case SALMON -> v *= 1;
                case PUFFERFISH -> v *= 1;
                case TROPICAL_FISH -> v *= 1;
                case DROWNED -> v *= 1;
                case DOLPHIN -> v *= 1;
                case CAT -> v *= 1;
                case PANDA -> v *= 1;
                case PILLAGER -> v *= 1;
                case RAVAGER -> v *= 1;
                case TRADER_LLAMA -> v *= 1;
                case WANDERING_TRADER -> v *= 1;
                case FOX -> v *= 1;
                case BEE -> v *= 1;
                case HOGLIN -> v *= 1;
                case PIGLIN -> v *= 1;
                case STRIDER -> v *= 1;
                case ZOGLIN -> v *= 1;
                case PIGLIN_BRUTE -> v *= 1;
                case AXOLOTL -> v *= 1;
                case GLOW_ITEM_FRAME -> v *= 1;
                case GLOW_SQUID -> v *= 1;
                case GOAT -> v *= 1;
                case MARKER -> v *= 1;
                case ALLAY -> v *= 1;
                case CHEST_BOAT -> v *= 1;
                case FROG -> v *= 1;
                case TADPOLE -> v *= 1;
                case WARDEN -> v *= 1;
                case CAMEL -> v *= 1;
                case BLOCK_DISPLAY -> v *= 1;
                case INTERACTION -> v *= 1;
                case ITEM_DISPLAY -> v *= 1;
                case SNIFFER -> v *= 1;
                case TEXT_DISPLAY -> v *= 1;
                case FISHING_HOOK -> v *= 1;
                case LIGHTNING -> v *= 1;
                case PLAYER -> v *= 1;
                case UNKNOWN -> v *= 1;
            }
        }

        return p;
    }

    public static double getPriority(Entity e) {
        double buf = 0;

        return 0;
    }
}
