package com.volmit.react.core.controller;

import com.volmit.react.React;
import com.volmit.react.api.entity.EntityPriority;
import com.volmit.react.model.ReactConfiguration;
import com.volmit.react.model.ReactEntity;
import com.volmit.react.util.math.M;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.scheduling.Looper;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityPoseChangeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.List;

@Data
public class EntityController implements IController, Listener {
    private int perWorldUpdatesPerTick = 10;
    private Looper looper;

    public EntityController() {
        looper = new Looper() {
            @Override
            protected long loop() {
                onTick();
                return 50;
            }
        };
        start();
    }

    @Override
    public String getName() {
        return "Event";
    }

    @Override
    public void start() {
        ReactConfiguration.get().getPriority().rebuildPriority();
        React.instance.registerListener(this);
        looper.start();
    }

    @EventHandler
    public void on(EntitySpawnEvent e) {
        ReactEntity.tick(e.getEntity(), ReactConfiguration.get().getPriority());
    }


    @EventHandler
    public void on(EntityDamageEvent e) {
        ReactEntity.tick(e.getEntity(), ReactConfiguration.get().getPriority());
    }

    @EventHandler
    public void on(EntityTargetEvent e) {
        ReactEntity.tick(e.getEntity(), ReactConfiguration.get().getPriority());

        if(e.getTarget() != null) {
            ReactEntity.tick(e.getTarget(), ReactConfiguration.get().getPriority());
        }
    }

    @EventHandler
    public void on(EntityInteractEvent e) {
        ReactEntity.tick(e.getEntity(), ReactConfiguration.get().getPriority());
    }

    @EventHandler
    public void on(PlayerInteractAtEntityEvent e) {
        ReactEntity.tick(e.getRightClicked(), ReactConfiguration.get().getPriority());
    }

    @EventHandler
    public void on(EntityPoseChangeEvent e) {
        ReactEntity.tick(e.getEntity(), ReactConfiguration.get().getPriority());
    }

    @EventHandler
    public void on(EntityRegainHealthEvent e) {
        ReactEntity.tick(e.getEntity(), ReactConfiguration.get().getPriority());
    }

    @EventHandler
    public void on(EntityTameEvent e) {
        ReactEntity.tick(e.getEntity(), ReactConfiguration.get().getPriority());
    }

    @EventHandler
    public void on(EntityPlaceEvent e) {
        ReactEntity.tick(e.getEntity(), ReactConfiguration.get().getPriority());
    }

    @EventHandler
    public void on(EntityDropItemEvent e) {
        ReactEntity.tick(e.getEntity(), ReactConfiguration.get().getPriority());
    }

    @EventHandler
    public void on(PlayerDropItemEvent e) {
        ReactEntity.tick(e.getItemDrop(), ReactConfiguration.get().getPriority());
    }

    @EventHandler
    public void on(ItemMergeEvent e) {
        ReactEntity.tick(e.getTarget(), ReactConfiguration.get().getPriority());
    }

    @EventHandler
    public void on(EntityBreedEvent e) {
        ReactEntity.tick(e.getMother(), ReactConfiguration.get().getPriority());
        ReactEntity.tick(e.getFather(), ReactConfiguration.get().getPriority());
    }

    @Override
    public void stop() {
        React.instance.unregisterListener(this);
        looper.interrupt();
    }

    public void onTick() {
        for(World i : Bukkit.getWorlds()) {
            List<Entity> e = J.sResult(i::getEntities);

            if(e.size() < 3) {
                continue;
            }

            for(int j = 0; j < perWorldUpdatesPerTick; j++) {
                ReactEntity.tick(e.get(M.irand(0, e.size() - 1)), ReactConfiguration.get().getPriority());
            }
        }
    }
}
