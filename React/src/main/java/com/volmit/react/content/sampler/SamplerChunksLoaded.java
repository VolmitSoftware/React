package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RollingSequence;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class SamplerChunksLoaded extends ReactCachedSampler implements Listener {
    public static final String ID = "chunks-loaded";
    private static final double D1_OVER_SECONDS = 1.0 / 1000D;
    private transient final AtomicInteger generated;
    private transient final RollingSequence avg = new RollingSequence(5);
    private transient long lastSample = 0L;

    public SamplerChunksLoaded() {
        super(ID, 1000); // 1 tick interval for higher accuracy
        generated = new AtomicInteger(0);
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
    public void on(ChunkLoadEvent event) {
        generated.incrementAndGet();
    }

    @Override
    public double onSample() {
        if (lastSample == 0) {
            lastSample = M.ms();
        }

        int r = generated.getAndSet(0);
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
        return "LOADS/s";
    }
}
