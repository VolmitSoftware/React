package com.volmit.react.content.tweak;

import com.volmit.react.api.tweak.ReactTweak;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

import java.util.HashMap;
import java.util.Map;

public class TweakEntities extends ReactTweak implements Listener {
    public static final String ID = "tweak-entities";
    private Map<EntityType, Double> spawnCancelChance = defaultMultipliers();

    public TweakEntities() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    /**
     * This tweaks the spawn rate of entities.
     * If the random Spawn is less than the spawn cancel chance, the entity is cancelled.
     */
    @EventHandler
    public void on(EntitySpawnEvent e) {
        if (spawnCancelChance.getOrDefault(e.getEntityType(), 0.0) > 0.0) {
            if (Math.random() < spawnCancelChance.get(e.getEntityType())) {
                e.setCancelled(true);
            }
        }
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

    public static Map<EntityType, Double> defaultMultipliers() {
        Map<EntityType, Double> map = new HashMap<>();

        for (EntityType i : EntityType.values()) {
            if (i.isAlive()) {
                map.put(i, 0.0);
            }
        }

        return map;
    }
}
