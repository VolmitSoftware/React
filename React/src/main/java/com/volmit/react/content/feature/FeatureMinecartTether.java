package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.content.sampler.SamplerChunksLoaded;
import com.volmit.react.model.ReactConfiguration;
import com.volmit.react.model.ReactEntity;
import com.volmit.react.util.math.M;
import com.volmit.react.util.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class FeatureMinecartTether extends ReactFeature implements Listener {
    public static final String ID = "minecart-tether";
    private double maxBlockDistance = 32;

    public FeatureMinecartTether() {
        super(ID);
    }

    @Override
    public void onActivate() {
        React.instance.registerListener(this);
        React.instance.getEntityController().registerEntityTickListener(EntityType.MINECART, (e) -> onMinecart((Minecart) e));
        React.instance.getEntityController().registerEntityTickListener(EntityType.MINECART_CHEST, (e) -> onMinecart((Minecart) e));
        React.instance.getEntityController().registerEntityTickListener(EntityType.MINECART_FURNACE, (e) -> onMinecart((Minecart) e));
        React.instance.getEntityController().registerEntityTickListener(EntityType.MINECART_COMMAND, (e) -> onMinecart((Minecart) e));
        React.instance.getEntityController().registerEntityTickListener(EntityType.MINECART_HOPPER, (e) -> onMinecart((Minecart) e));
        React.instance.getEntityController().registerEntityTickListener(EntityType.MINECART_TNT, (e) -> onMinecart((Minecart) e));
        React.instance.getEntityController().registerEntityTickListener(EntityType.MINECART_MOB_SPAWNER, (e) -> onMinecart((Minecart) e));
    }

    public void onMinecart(Minecart entity) {
        if(entity.getVelocity().getX() != 0 || entity.getVelocity().getY() != 0 || entity.getVelocity().getZ() != 0
            ||entity.getFlyingVelocityMod().getX() != 0 || entity.getFlyingVelocityMod().getY() != 0 || entity.getFlyingVelocityMod().getZ() != 0
            ||entity.getDerailedVelocityMod().getX() != 0 || entity.getDerailedVelocityMod().getY() != 0 || entity.getDerailedVelocityMod().getZ() != 0
        ) {
            if(!React.hasNearbyPlayer(entity.getLocation(), maxBlockDistance)) {
                entity.setVelocity(new Vector(0,0,0));
                entity.setDerailedVelocityMod(new Vector(0,0,0));
                entity.setFlyingVelocityMod(new Vector(0,0,0));
            }
        }
    }

    @Override
    public void onDeactivate() {
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
