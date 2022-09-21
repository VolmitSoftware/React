package com.volmit.react.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.ChronoLatch;
import com.volmit.react.util.Form;
import com.volmit.react.util.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class SamplerChunksLoaded extends ReactCachedSampler implements Listener {
    public static final String ID = "chunks-loaded";
    private final ChronoLatch realCheckUpdate;
    private final AtomicInteger loadedChunks;

    public SamplerChunksLoaded() {
        super(ID, 50);
        loadedChunks = new AtomicInteger(0);
        realCheckUpdate = new ChronoLatch(10000);
    }

    public int getRealCheck() {
        return executeSync(() -> {
            int m = 0;

            for(World i : Bukkit.getWorlds()) {
                m += i.getLoadedChunks().length;
            }

            return m;
        });
    }

    @Override
    public void start() {
        React.instance.registerListener(this);
    }

    @Override
    public void stop() {
        React.instance.unregisterListener(this);
    }

    @EventHandler
    public void on(ChunkLoadEvent e) {
        loadedChunks.incrementAndGet();
    }

    @EventHandler
    public void on(WorldUnloadEvent e) {
        loadedChunks.addAndGet(-e.getWorld().getLoadedChunks().length);
    }

    @EventHandler
    public void on(ChunkUnloadEvent e) {
        loadedChunks.decrementAndGet();
    }

    @Override
    public double onSample() {
        if(realCheckUpdate.flip() || loadedChunks.get() < 0) {
            J.a(() -> loadedChunks.set(getRealCheck()));
        }

        return loadedChunks.get();
    }

    @Override
    public String format(double t) {
        return Form.f(loadedChunks.get()) + " Chunks";
    }
}
