package com.volmit.react.core.controller;

import com.volmit.react.React;
import com.volmit.react.model.SampledServer;
import com.volmit.react.model.SampledWorld;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class ObserverController extends TickedObject implements IController {
    private final SampledServer sampled;

    public ObserverController() {
        super("react", "observer", 250);
        sampled = new SampledServer();
        start();
    }

    @Override
    public void onTick() {
        //Clear the contents of the world statistics

    }

    @Override
    public String getName() {
        return "Observer";
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
    public void on(BlockRedstoneEvent event) {
        sampled.getChunk(event.getBlock().getChunk()).getRedstoneInteractions().incrementAndGet();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPhysicsEvent event) {
        sampled.getChunk(event.getBlock().getChunk()).getWaterUpdates().incrementAndGet();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPistonExtendEvent event) {
        sampled.getChunk(event.getBlock().getChunk()).getPistonInteractions().incrementAndGet();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPistonRetractEvent event) {
        sampled.getChunk(event.getBlock().getChunk()).getPistonInteractions().incrementAndGet();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ChunkUnloadEvent event) {
        sampled.removeChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(WorldUnloadEvent event) {
        sampled.removeWorld(event.getWorld());
    }
}
