package com.volmit.react.content.sampler;

import art.arcane.chrono.PrecisionStopwatch;
import com.volmit.react.React;
import com.volmit.react.api.event.layer.ServerTickEvent;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.RollingSequence;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

public class SamplerPhysicsTickTime extends ReactCachedSampler implements Listener {
    public static final String ID = "physics-tick-time";
    private int tickAverage = 15;
    private transient double maxDuration;
    private transient PrecisionStopwatch stopwatch;
    private transient RollingSequence average;
    private transient boolean running;

    public SamplerPhysicsTickTime() {
        super(ID, 50);
    }

    @Override
    public void start() {
        super.start();
        average = new RollingSequence(tickAverage);
        stopwatch = new PrecisionStopwatch();
        running = false;
        maxDuration = 0;
    }

    @Override
    public Material getIcon() {
        return Material.MUSHROOM_STEW;
    }

    @EventHandler
    public void on(ServerTickEvent e) {
        average.put(maxDuration);
        running = false;
        maxDuration = 0;
    }

    @EventHandler
    public void on(BlockPhysicsEvent e) {
        if (!running) {
            stopwatch.resetAndBegin();
            running = true;
        } else {
            maxDuration = stopwatch.getMilliseconds();
        }
    }

    @Override
    public double onSample() {
        return average.getAverage();
    }

    @Override
    public String format(double t) {
        return formattedValue(t) + formattedSuffix(t);
    }

    @Override
    public Component format(Component value, Component suffix) {
        return Component.empty().append(value).append(suffix);
    }

    @Override
    public String formattedValue(double t) {
        return Form.durationSplit(t, 2)[0];
    }

    @Override
    public String formattedSuffix(double t) {
        return Form.durationSplit(t, 2)[1] + " PHY";
    }
}
