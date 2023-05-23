package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedRateSampler;
import com.volmit.react.util.format.Form;
import org.bukkit.Material;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

public class SamplerHopperUpdates extends ReactCachedRateSampler implements Listener {
    public static final String ID = "hopper";

    public SamplerHopperUpdates() {
        super(ID, 1000);
    }

    @Override
    public Material getIcon() {
        return Material.HOPPER;
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
        if (e.getSource().getHolder() instanceof Hopper) {
            increment();
            getChunkCounter(e.getSource().getLocation().getBlock()).addAndGet(1D);
        } else if (e.getDestination().getHolder() instanceof Hopper) {
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
