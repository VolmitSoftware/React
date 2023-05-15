package com.volmit.react.model;

import com.volmit.react.React;
import com.volmit.react.api.entity.EntityPriority;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class ReactEntity {
    private static final long maxTickInterval = 10000;
    private static final NamespacedKey nsPriority = new NamespacedKey(React.instance, "react-priority");
    private static final NamespacedKey nsLastTick = new NamespacedKey(React.instance, "react-last-tick");
    private static final NamespacedKey nsPaused = new NamespacedKey(React.instance, "react-paused");
    private static final NamespacedKey nsCrowding = new NamespacedKey(React.instance, "react-crowding");

    public static long getStaleness(Entity entity) {
        return System.currentTimeMillis() - getLastTick(entity);
    }

    public static void tick(Entity entity, EntityPriority p) {
        if(entity.isDead()) {
            return;
        }

        if(getStaleness(entity) > maxTickInterval) {
            double c = p.computeCrowd(entity);
            setCrowding(entity, c);
            setPriority(entity, p.getPriorityWithCrowd(entity, c));
            setLastTick(entity, System.currentTimeMillis());

            if(isPaused(entity)) {
                setPaused(entity, false);
            }
        }
    }

    public static long getLastTick(Entity entity) {
        Long d = entity.getPersistentDataContainer().get(nsLastTick, PersistentDataType.LONG);
        return d == null ? 0 : d;
    }

    public static void setLastTick(Entity entity, long lastTick) {
        entity.getPersistentDataContainer().set(nsLastTick, PersistentDataType.LONG, lastTick);
    }

    public static double getPriority(Entity entity) {
        Double d = entity.getPersistentDataContainer().get(nsPriority, PersistentDataType.DOUBLE);
        return d == null ? EntityPriority.BASELINE : d;
    }

    public static void setPriority(Entity entity, double priority) {
        entity.getPersistentDataContainer().set(nsPriority, PersistentDataType.DOUBLE, priority);
    }

    public static double getCrowding(Entity entity) {
        Double d = entity.getPersistentDataContainer().get(nsCrowding, PersistentDataType.DOUBLE);
        return d == null ? 1 : d;
    }

    public static void setCrowding(Entity entity, double crowding) {
        entity.getPersistentDataContainer().set(nsCrowding, PersistentDataType.DOUBLE, crowding);
    }

    public static boolean isPaused(Entity entity) {
        Byte d = entity.getPersistentDataContainer().get(nsPaused, PersistentDataType.BYTE);
        return d != null && d == 1;
    }

    public static void setPaused(Entity entity, boolean paused) {
        entity.getPersistentDataContainer().set(nsPaused, PersistentDataType.BYTE, (byte) (paused ? 1 : 0));

        if(paused && entity instanceof LivingEntity le) {
            le.setAI(false);
        }

        else if(!paused && entity instanceof LivingEntity le) {
            le.setAI(true);
        }
    }
}
