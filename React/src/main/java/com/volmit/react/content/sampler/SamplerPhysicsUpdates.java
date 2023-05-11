package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RollingSequence;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class SamplerPhysicsUpdates extends ReactCachedSampler implements Listener {
    public static final String ID = "physics";
    private static final double D1_OVER_SECONDS = 1.0 / 1000D;
    private transient final AtomicInteger physicsInteractions;
    private transient final RollingSequence avg = new RollingSequence(5);
    private transient long lastSample = 0L;

    public SamplerPhysicsUpdates() {
        super(ID, 1000);
        physicsInteractions = new AtomicInteger(0);
    }

    @Override
    public Material getIcon() {
        return Material.BOWL;
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
    public void on(BlockPistonExtendEvent event) {
        int b = event.getBlocks().size() + 2;
        physicsInteractions.addAndGet(b);
        getChunkCounter(event.getBlock()).addAndGet(b);
    }

    @EventHandler
    public void on(BlockPistonRetractEvent event) {
        int b = event.getBlocks().size() + 2;
        physicsInteractions.addAndGet(b);
        getChunkCounter(event.getBlock()).addAndGet(b);
    }

    @Override
    public double onSample() {
        if (lastSample == 0) {
            lastSample = M.ms();
        }

        int r = physicsInteractions.getAndSet(0);
        long dur = Math.max(M.ms() - lastSample, 1000);
        lastSample = M.ms();
        avg.put(r / (dur * D1_OVER_SECONDS));

        return avg.getAverage();
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.round(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return "PHY/s";
    }
}
