package com.volmit.react.model;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SampledServer {
    private final Map<String, SampledWorld> worlds;

    public SampledServer() {
        worlds = new HashMap<>();
    }

    public SampledChunk getChunk(Chunk chunk) {
        return getWorld(chunk.getWorld()).getChunk(chunk);
    }

    public Optional<SampledChunk> optionalChunk(Chunk c) {
        return optionalWorld(c.getWorld()).flatMap((w) -> w.optionalChunk(c));
    }

    public boolean hasWorld(String world) {
        return worlds.containsKey(world);
    }

    public boolean hasWorld(World world) {
        return hasWorld(world.getName());
    }

    public void removeChunk(Chunk chunk) {
        if(hasWorld(chunk.getWorld())) {
            getWorld(chunk.getWorld()).remove(chunk);
        }
    }

    public void removeWorld(World world) {
        removeWorld(world.getName());
    }

    public void removeWorld(String name) {
        worlds.remove(name);
    }

    public SampledWorld getWorld(World world) {
        return getWorld(world.getName());
    }

    public Optional<SampledWorld> optionalWorld(World world) {
        return hasWorld(world) ? Optional.of(getWorld(world)) : Optional.empty();
    }

    public SampledWorld getWorld(String name) {
        return worlds.computeIfAbsent(name, (k) -> new SampledWorld(Bukkit.getWorld(name)));
    }
}
