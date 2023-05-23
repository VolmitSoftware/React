package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.content.sampler.SamplerRedstoneTickTime;
import com.volmit.react.model.Circuit;
import com.volmit.react.model.CircuitServer;
import com.volmit.react.util.scheduling.J;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.HopperInventorySearchEvent;

public class FeatureCircuitManager extends ReactFeature implements Listener {
    public static final String ID = "circuit-manager";
    /**
     * Stop the biggest circuit when the redstone milliseconds exceeds this value
     */
    private double maxCircuitMS = 15;
    private transient CircuitServer circuitServer;

    public FeatureCircuitManager() {
        super(ID);
    }

    @Override
    public void onActivate() {
        circuitServer = new CircuitServer();

    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public int getTickInterval() {
        return 1000;
    }

    @Override
    public void onTick() {
        J.s(() -> circuitServer.tick());

        if (React.sampler(SamplerRedstoneTickTime.ID).sample() > maxCircuitMS) {
            Circuit c = circuitServer.worst();

            if (c != null) {
                c.stop();
                React.warn("Stopping Circuit " + c.getId());
            }
        }
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        circuitServer.remove(e.getBlock());
    }

    @EventHandler
    public void on(BlockPistonExtendEvent e) {
        circuitServer.event(e.getBlock());

        for (Block i : e.getBlocks()) {
            circuitServer.event(i);
        }
    }

    @EventHandler
    public void on(BlockPistonRetractEvent e) {
        circuitServer.event(e.getBlock());

        for (Block i : e.getBlocks()) {
            circuitServer.event(i);
        }
    }

    @EventHandler
    public void on(BlockRedstoneEvent e) {
        Circuit c = circuitServer.event(e.getBlock());

        if (c != null && c.getStop().get()) {
            e.setNewCurrent(e.getOldCurrent());
        }
    }

    @EventHandler
    public void on(HopperInventorySearchEvent e) {
        Circuit r = circuitServer.event(e.getBlock());
        Circuit s = circuitServer.event(e.getBlock());

        if (r != null && r.getStop().get()) {
            e.setInventory(null);
        }

        if (s != null && s.getStop().get()) {
            e.setInventory(null);
        }
    }
}
