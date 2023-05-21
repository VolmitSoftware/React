package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedRateSampler;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RollingSequence;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class SamplerPhysicsUpdates extends ReactCachedRateSampler implements Listener {
    public static final String ID = "physics";

    public SamplerPhysicsUpdates() {
        super(ID, 1000);
    }

    @Override
    public Material getIcon() {
        return Material.BOWL;
    }

    @Override
    public void start() {
        super.start();
        React.instance.registerListener(this);
    }

    @Override
    public void stop() {
        super.stop();
        React.instance.unregisterListener(this);
    }

    @EventHandler
    public void on(BlockPistonExtendEvent event) {
        int b = event.getBlocks().size() + 2;
        increment(b);
        getChunkCounter(event.getBlock()).addAndGet(b);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void on(BlockPhysicsEvent event) {
        increment();
        getChunkCounter(event.getBlock()).addAndGet(1);
    }

    @EventHandler
    public void on(BlockPistonRetractEvent event) {
        int b = event.getBlocks().size() + 2;
        increment(b);
        getChunkCounter(event.getBlock()).addAndGet(b);
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
