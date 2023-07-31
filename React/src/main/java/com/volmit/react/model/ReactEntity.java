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

package com.volmit.react.model;

import com.volmit.react.React;
import com.volmit.react.api.entity.EntityPriority;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

public class ReactEntity {
    private static final long maxTickInterval = 10000;
    private static final NamespacedKey nsStackCount = new NamespacedKey(React.instance, "react-stack-count");
    private static final NamespacedKey nsPriority = new NamespacedKey(React.instance, "react-priority");
    private static final NamespacedKey nsLastTick = new NamespacedKey(React.instance, "react-last-tick");
    private static final NamespacedKey nsPaused = new NamespacedKey(React.instance, "react-paused");
    private static final NamespacedKey nsCrowding = new NamespacedKey(React.instance, "react-crowding");
    private static final NamespacedKey nsDistancePlayer = new NamespacedKey(React.instance, "react-player-distance");

    public static long getStaleness(Entity entity) {
        return System.currentTimeMillis() - getLastTick(entity);
    }

    public static boolean tick(Entity entity, EntityPriority p) {
        if (entity.isDead()) {
            return false;
        }

        if (getStaleness(entity) > maxTickInterval) {
            p.updateCrowd(entity);
            p.updateDistanceToPlayer(entity);
            setPriority(entity, p.getPriorityWithCrowd(entity, getCrowding(entity)));
            setLastTick(entity, System.currentTimeMillis());

            if (isPaused(entity)) {
                setPaused(entity, false);
            }

            return true;
        }

        return false;
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

    public static int getStackCount(Entity entity) {
        Integer d = entity.getPersistentDataContainer().get(nsStackCount, PersistentDataType.INTEGER);
        return d == null ? 1 : d;
    }

    public static void setStackCount(Entity entity, int stackCount) {
        entity.getPersistentDataContainer().set(nsStackCount, PersistentDataType.INTEGER, stackCount);
    }

    public static double getCrowding(Entity entity) {
        Double d = entity.getPersistentDataContainer().get(nsCrowding, PersistentDataType.DOUBLE);
        return d == null ? 1 : d;
    }

    public static void setCrowding(Entity entity, double crowding) {
        entity.getPersistentDataContainer().set(nsCrowding, PersistentDataType.DOUBLE, crowding);
    }

    public static double getNearestPlayer(Entity entity) {
        Double d = entity.getPersistentDataContainer().get(nsDistancePlayer, PersistentDataType.DOUBLE);
        return d == null ? 1 : d;
    }

    public static void setNearestPlayer(Entity entity, double d) {
        entity.getPersistentDataContainer().set(nsDistancePlayer, PersistentDataType.DOUBLE, d);
    }

    public static boolean isPaused(Entity entity) {
        Byte d = entity.getPersistentDataContainer().get(nsPaused, PersistentDataType.BYTE);
        return d != null && d == 1;
    }

    public static void setPaused(Entity entity, boolean paused) {
        entity.getPersistentDataContainer().set(nsPaused, PersistentDataType.BYTE, (byte) (paused ? 1 : 0));

        if (paused && entity instanceof LivingEntity le) {
            le.setAI(false);
        } else if (!paused && entity instanceof LivingEntity le) {
            le.setAI(true);
        }
    }
}
