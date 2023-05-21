package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.model.VisualizerType;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RollingSequence;
import org.bukkit.Material;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class SamplerHopperUpdates extends ReactCachedSampler implements Listener {
    public static final String ID = "hopper";
    private static final double D1_OVER_SECONDS = 1.0 / 1000D;
    private transient final AtomicInteger hopperInteractions;
    private transient final RollingSequence avg = new RollingSequence(5);
    private transient long lastSample = 0L;

    public SamplerHopperUpdates() {
        super(ID, 1000);
        hopperInteractions = new AtomicInteger(0);
    }

    @Override
    public Material getIcon() {
        return Material.HOPPER;
    }

    @Override
    public void start() {
        React.instance.registerListener(this);
    }

    @Override
    public void stop() {
        React.instance.unregisterListener(this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.HOPPER) {
            hopperInteractions.incrementAndGet();
            getChunkCounter(e.getBlock()).addAndGet(1D);
            visualize(e.getBlock(), VisualizerType.HOPPER);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(InventoryMoveItemEvent e) {
        if(e.getSource().getHolder() instanceof Hopper) {
            hopperInteractions.incrementAndGet();
            getChunkCounter(e.getSource().getLocation().getBlock()).addAndGet(1D);
            visualize(e.getSource().getLocation().getBlock(), VisualizerType.HOPPER);
        }

        else if(e.getDestination().getHolder() instanceof Hopper){
            hopperInteractions.incrementAndGet();
            getChunkCounter(e.getDestination().getLocation().getBlock()).addAndGet(1D);
            visualize(e.getDestination().getLocation().getBlock(), VisualizerType.HOPPER);
        }
    }

    @Override
    public double onSample() {
        if (lastSample == 0) {
            lastSample = M.ms();
        }

        int r = hopperInteractions.getAndSet(0);
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
        return "HOP/s";
    }
}
