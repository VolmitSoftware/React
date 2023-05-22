package com.volmit.react.core.controller;

import art.arcane.spatial.mantle.Mantle;
import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.model.SampledChunk;
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
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Comparator;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
public class ObserverController extends TickedObject implements IController {
    private final SampledServer sampled;

    public ObserverController() {
        super("react", "observer", 1000);
        sampled = new SampledServer();
        start();
    }

    @Override
    public void onTick() {

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

    public SampledChunk absoluteWorst() {
        return sampled.getWorlds().values().stream()
            .flatMap(i -> i.getChunks().values().stream())
            .max(Comparator.comparingDouble(SampledChunk::totalScore)
                .thenComparingDouble(SampledChunk::highestSubScore))
            .orElse(null);
    }

    public AtomicDouble get(Block b, Sampler sampler) {
        return get(b.getChunk(), sampler);
    }

    public AtomicDouble get(Chunk c, Sampler sampler) {
        return sampled.getChunk(c).get(sampler.getId());
    }

    public Optional<Double> sample(Chunk c, Sampler s) {
        return sampled.optionalChunk(c).flatMap(i -> i.optional(s.getId())).map(AtomicDouble::get);
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
