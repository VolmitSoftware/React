package com.volmit.react.core.controller;

import com.volmit.react.React;
import com.volmit.react.core.controller.data.ChunkStatistic;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
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
    private Map<String, Map<Chunk, ChunkStatistic>> worldStatistics = new HashMap<>();

    public ObserverController() {
        super("react", "observer", 250);
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

    public ChunkStatistic getChunkStatistic(Chunk chunk) {
        return worldStatistics.get(chunk.getWorld().getName()).get(chunk);
    }

    @Override
    public void start() {
        Bukkit.getWorlds().forEach(world -> {
            Map<Chunk, ChunkStatistic> chunkStatistics = new HashMap<>();
            Arrays.stream(world.getLoadedChunks()).forEach(chunk -> chunkStatistics.put(chunk, new ChunkStatistic()));
            worldStatistics.put(world.getName(), chunkStatistics);
        });
        React.instance.registerListener(this);
    }

    @Override
    public void stop() {
        React.instance.unregisterListener(this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockRedstoneEvent event) {
        ChunkStatistic chunkStatistic = getChunkStatistic(event.getBlock().getChunk());
        chunkStatistic.setRedstoneInteractions(chunkStatistic.getRedstoneInteractions() + 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPhysicsEvent event) {
        if (event.getChangedType() == Material.WATER) {
            ChunkStatistic chunkStatistic = getChunkStatistic(event.getBlock().getChunk());
            chunkStatistic.setWaterUpdates(chunkStatistic.getWaterUpdates() + 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPistonExtendEvent event) {
        ChunkStatistic chunkStatistic = getChunkStatistic(event.getBlock().getChunk());
        chunkStatistic.setPistonInteractions(chunkStatistic.getPistonInteractions() + event.getBlocks().size());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPistonRetractEvent event) {
        ChunkStatistic chunkStatistic = getChunkStatistic(event.getBlock().getChunk());
        chunkStatistic.setPistonInteractions(chunkStatistic.getPistonInteractions() + event.getBlocks().size());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ChunkUnloadEvent event) {
        worldStatistics.get(event.getWorld().getName()).remove(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(WorldUnloadEvent event) {
        worldStatistics.remove(event.getWorld().getName());
    }

}
