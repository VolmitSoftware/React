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
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class SamplerFluidUpdates extends ReactCachedSampler implements Listener {
    public static final String ID = "fluid";
    private static final double D1_OVER_SECONDS = 1.0 / 1000D;
    private transient final AtomicInteger fluidInteractions;
    private transient final RollingSequence avg = new RollingSequence(5);
    private transient long lastSample = 0L;

    public SamplerFluidUpdates() {
        super(ID, 1000);
        fluidInteractions = new AtomicInteger(0);
    }

    @Override
    public Material getIcon() {
        return Material.MILK_BUCKET;
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
    public void onBlockFromTo(BlockFromToEvent event) {
        BlockData data = event.getBlock().getBlockData();

        if (data instanceof Levelled l) {
            fluidInteractions.addAndGet(l.getLevel());
            getChunkCounter(event.getBlock()).addAndGet(1D);
            visualize(event.getBlock(), VisualizerType.FLUID);
        }
    }

    @Override
    public double onSample() {
        if (lastSample == 0) {
            lastSample = M.ms();
        }

        int r = fluidInteractions.getAndSet(0);
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
        return "FLOW/s";
    }
}
