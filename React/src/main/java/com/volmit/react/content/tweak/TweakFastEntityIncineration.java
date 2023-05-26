package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.core.controller.EntityController;
import com.volmit.react.model.ReactEntity;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Arrays;
import java.util.List;

public class TweakFastEntityIncineration extends ReactTweak implements Listener {
    public static final String ID = "fast-entity-incineration";
    private double incinerationBeyondNearestPlayer = 32;

    public TweakFastEntityIncineration() {
        super(ID);
    }

    @Override
    public void onActivate() { 

    }

    @EventHandler
    public void on(EntityDamageEvent e) {
        if(e.getCause().equals(EntityDamageEvent.DamageCause.FIRE_TICK) && e.getEntity() instanceof Monster m && React.hasNearbyPlayer(m.getLocation(), incinerationBeyondNearestPlayer)) {
            m.damage(1000);
        }
    }

    private void kill(Entity entity) {
        J.s(() -> React.kill(entity), (int) (20 * Math.random()));
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
}
