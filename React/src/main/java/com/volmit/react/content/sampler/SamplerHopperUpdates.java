package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedRateSampler;
import com.volmit.react.api.sampler.ReactCachedSampler;
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

public class SamplerHopperUpdates extends ReactCachedRateSampler implements Listener {
    public static final String ID = "hopper";

    public SamplerHopperUpdates() {
        super(ID, 1000);
    }

    @Override
    public Material getIcon() {
        return Material.HOPPER;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.HOPPER) {
            increment();
            getChunkCounter(e.getBlock()).addAndGet(1D);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(InventoryMoveItemEvent e) {
        if(e.getSource().getHolder() instanceof Hopper) {
            increment();
            getChunkCounter(e.getSource().getLocation().getBlock()).addAndGet(1D);
        }

        else if(e.getDestination().getHolder() instanceof Hopper){
            increment();
            getChunkCounter(e.getDestination().getLocation().getBlock()).addAndGet(1D);
        }
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
