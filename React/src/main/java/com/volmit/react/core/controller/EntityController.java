package com.volmit.react.core.controller;

import art.arcane.chrono.ChronoLatch;
import com.volmit.react.React;
import com.volmit.react.model.ReactConfiguration;
import com.volmit.react.model.ReactEntity;
import com.volmit.react.util.math.M;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.scheduling.Looper;
import com.volmit.react.util.value.MaterialValue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Data
public class EntityController implements IController, Listener {
    private int perWorldUpdatesPerTick = 15;
    private Looper looper;
    private ChronoLatch valueSaver = new ChronoLatch(60000);
    private Map<EntityType, List<Consumer<Entity>>> entityTickListeners;

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

    public void registerEntityTickListener(EntityType type, Consumer<Entity> listener) {
        entityTickListeners.computeIfAbsent(type, (t) -> new ArrayList<>()).add(listener);
    }

    @Override
    public String getName() {
        return "Event";
    }

    @Override
    public void start() {
        entityTickListeners = new HashMap<>();
        ReactConfiguration.get().getPriority().rebuildPriority();
        React.instance.registerListener(this);
        looper.start();
    }

    public void tickEntity(Entity e) {
        List<Consumer<Entity>> tickers = entityTickListeners.get(e.getType());

        if(tickers != null) {
            for(Consumer<Entity> i : tickers) {
                i.accept(e);
            }
        }
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
        if(valueSaver.flip() && ReactConfiguration.get().getPriority().isUseItemStackValueSystem()) {
            MaterialValue.save();
        }

        for(World i : Bukkit.getWorlds()) {
            J.s(() -> {
                List<Entity> e = i.getEntities();

                if(e.size() < 3) {
                    return;
                }

                J.a(() -> {
                    for(int j = 0; j < perWorldUpdatesPerTick; j++) {
                        Entity ee = e.get(M.irand(0, e.size() - 1));
                        if(ReactEntity.tick(ee, ReactConfiguration.get().getPriority())) {
                            J.s(() -> {
                                tickEntity(ee);
                            });
                        }
                    }
                });
            });
        }
    }
}
