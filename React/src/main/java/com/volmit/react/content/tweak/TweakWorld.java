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
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class TweakWorld extends ReactTweak implements Listener {
    public static final String ID = "tweak-world";
    private boolean disableWeatherWhenOnlyCreativePlayersOnline = true;
    private double gravityMultiplier = 1.0;
    private Map<EntityType, Double> perEntityGravityMultiplier = defaultMultipliers();
    private transient Map<EntityType, Vector> perEntityGravityAmount;
    private transient Vector gravityAmount;
    private transient int gravityTick;

    public TweakWorld() {
        super(ID);
    }

    public boolean hasGravityTweak() {
        return gravityMultiplier != 1.0 || perEntityGravityMultiplier.values().stream().anyMatch(i -> i != 1.0);
    }

    @Override
    public void onActivate() {
        if(hasGravityTweak()) {
            perEntityGravityAmount = new HashMap<>();
            gravityAmount = new Vector(0, -0.1 * gravityMultiplier, 0);

            for(EntityType i : perEntityGravityMultiplier.keySet()) {
                perEntityGravityAmount.put(i, gravityAmount.clone().multiply(perEntityGravityMultiplier.get(i)));
            }
            gravityTick = J.sr(this::tickGravity, 0);
        }
    }

    public void tickGravity(Entity e) {
        if(gravityMultiplier == 1.0 && perEntityGravityMultiplier.getOrDefault(e.getType(), 1.0) == 1.0) {
            return;
        }

        if(e instanceof Player p && p.isFlying()) {
            e.setGravity(false);
            return;
        }

        if(e.getLocation().clone().add(0, -0.1, 0).getBlock().isPassable()) {
            e.setGravity(false);
            e.setVelocity(e.getVelocity().add(perEntityGravityAmount.getOrDefault(e.getType(), gravityAmount)));
        }

        else {
            e.setGravity(true);
        }
    }

    public void tickGravity() {
        for(World i : Bukkit.getWorlds()) {
            for(Entity j : i.getEntities()) {
                tickGravity(j);
            }
        }
    }

    @Override
    public void onDeactivate() {
        if(hasGravityTweak()) {
            J.csr(gravityTick);
        }
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
            map.put(i, 1.0);
        }

        return map;
    }
}
