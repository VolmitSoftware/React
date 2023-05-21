package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.core.nms.R194;
import com.volmit.react.model.VisualizerType;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RollingSequence;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.HopperInventorySearchEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class SamplerRedstoneUpdates extends ReactCachedSampler implements Listener {
    public static final String ID = "redstone";
    private static final double D1_OVER_SECONDS = 1.0 / 1000D;
    private transient final AtomicInteger redstoneInteractions;
    private transient final RollingSequence avg = new RollingSequence(5);
    private transient long lastSample = 0L;

    public SamplerRedstoneUpdates() {
        super(ID, 1000); // 1 tick interval for higher accuracy
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
    public void on(BlockRedstoneEvent event) {
        redstoneInteractions.incrementAndGet();
        getChunkCounter(event.getBlock()).addAndGet(1D);
        visualize(event.getBlock(), VisualizerType.REDSTONE);
    }

    @Override
    public double onSample() {
        if (lastSample == 0) {
            lastSample = M.ms();
        }

        int r = redstoneInteractions.getAndSet(0);
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
        return "RED/s";
    }
}
