package com.volmit.react.content.sampler;

import art.arcane.chrono.ChronoLatch;
import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class SamplerEntities extends ReactCachedSampler implements Listener {
    public static final String ID = "entities";
    private transient ChronoLatch realEntityUpdate;
    private transient final AtomicInteger entities;
    private int realityCheckMS = 10000;

    @Override
    public Material getIcon() {
        return Material.CHICKEN_SPAWN_EGG;
    }

    public SamplerEntities() {
        super(ID, 50);
        entities = new AtomicInteger(0);
        realEntityUpdate = new ChronoLatch(realityCheckMS);
    }

    public int getRealCheck() {
        return executeSync(() -> {
            int m = 0;
            for (World i : Bukkit.getWorlds()) {
                m += i.getEntities().size();
            }

            return m;
        });
    }

    @Override
    public void start() {
        React.instance.registerListener(this);
        realEntityUpdate = new ChronoLatch(realityCheckMS);
    }

    @Override
    public void stop() {
        React.instance.unregisterListener(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntitySpawnEvent e) {
        entities.incrementAndGet();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ChunkLoadEvent e) {
        entities.addAndGet(e.getChunk().getEntities().length);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ItemMergeEvent e) {
        entities.addAndGet(-1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ChunkUnloadEvent e) {
        entities.addAndGet(-e.getChunk().getEntities().length);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(WorldUnloadEvent e) {
        entities.addAndGet(-e.getWorld().getEntities().size());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDeathEvent e) {
        entities.decrementAndGet();
    }

    @Override
    public double onSample() {
        if (realEntityUpdate.flip() || entities.get() < 0) {
            J.a(() -> entities.set(getRealCheck()));
        }

        return entities.get();
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.round(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return "ENT";
    }
}