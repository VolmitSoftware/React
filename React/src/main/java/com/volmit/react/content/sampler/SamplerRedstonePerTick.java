package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RollingSequence;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class SamplerRedstonePerTick extends ReactCachedSampler implements Listener {
    public static final String ID = "redstone";
    private final AtomicInteger redstoneInteractions;
    private final Set<Material> redstoneComponents;
    private final RollingSequence avg = new RollingSequence(20);
    private long lastSample = 0L;
    private static final double D1_OVER_TICKS = 1.0/50D;

    public SamplerRedstonePerTick() {
        super(ID, 50); // 1 tick interval for higher accuracy
        redstoneInteractions = new AtomicInteger(0);
        redstoneComponents = new HashSet<>();
        redstoneComponents.add(Material.REDSTONE_WIRE);
        redstoneComponents.add(Material.REDSTONE_TORCH);
        redstoneComponents.add(Material.REDSTONE_BLOCK);
        redstoneComponents.add(Material.REPEATER);
        redstoneComponents.add(Material.COMPARATOR);
        redstoneComponents.add(Material.PISTON);
        redstoneComponents.add(Material.STICKY_PISTON);
        redstoneComponents.add(Material.REDSTONE_LAMP);
        redstoneComponents.add(Material.OBSERVER);
        // Add more redstone components here if needed
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
    public void on(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        BlockData blockData = block.getBlockData();

        if (blockData instanceof RedstoneWire) {
            redstoneInteractions.incrementAndGet();
        }
    }

    @Override
    public double onSample() {
        if(lastSample == 0) {
            lastSample = M.ms();
        }

        int r = redstoneInteractions.getAndSet(0);
        long dur = Math.max(M.ms() - lastSample, 50);
        lastSample = M.ms();
        avg.put( r / (dur*D1_OVER_TICKS));

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
