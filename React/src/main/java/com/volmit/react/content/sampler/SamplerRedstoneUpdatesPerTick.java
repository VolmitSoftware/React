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
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class SamplerRedstoneUpdatesPerTick extends ReactCachedSampler implements Listener {
    public static final String ID = "redstone";
    private static final double D1_OVER_TICKS = 1.0 / 50D;
    private transient final AtomicInteger redstoneInteractions;
    private transient final RollingSequence avg = new RollingSequence(20);
    private transient long lastSample = 0L;

    public SamplerRedstoneUpdatesPerTick() {
        super(ID, 50); // 1 tick interval for higher accuracy
        redstoneInteractions = new AtomicInteger(0);
    }

    @Override
    public Material getIcon() {
        return Material.REDSTONE;
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
    public void on(BlockRedstoneEvent event) { // Get any block that is powered by redstone, and update the counter
        redstoneInteractions.incrementAndGet();
    }

    @EventHandler
    public void on(BlockPistonExtendEvent event) { // Get the Piston block, and update the counter for each block it pushes, and the piston itself
        redstoneInteractions.addAndGet(event.getBlocks().size() + 2); // (the 2 is for the piston itself and the block it pushes into)
    }

    @EventHandler
    public void on(BlockPistonRetractEvent event) { // Get the Piston block, and update the counter for each block it pulls, and the piston itself
        redstoneInteractions.addAndGet(event.getBlocks().size() + 2); // (the 2 is for the piston itself and the block it pulls from)
    }

    @Override
    public double onSample() {
        if (lastSample == 0) {
            lastSample = M.ms();
        }

        int r = redstoneInteractions.getAndSet(0);
        long dur = Math.max(M.ms() - lastSample, 50);
        lastSample = M.ms();
        avg.put(r / (dur * D1_OVER_TICKS));

        return avg.getAverage();
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.round(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return "RI/t";
    }
}
