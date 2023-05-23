package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.util.Vector;

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


    @EventHandler
    public void on(EntitySpawnEvent e) {
        if(spawnCancelChance.getOrDefault(e.getEntityType(), 0.0) > 0.0) {
            if(Math.random() < spawnCancelChance.get(e.getEntityType())) {
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

        for(EntityType i : EntityType.values()) {
            if(i.isAlive()) {
                map.put(i, 0.0);
            }
        }

        return map;
    }
}
