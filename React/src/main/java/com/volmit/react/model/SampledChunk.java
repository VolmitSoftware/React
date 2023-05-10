package com.volmit.react.model;

import com.google.common.util.concurrent.AtomicDouble;
import lombok.Data;
import org.bukkit.Chunk;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class SampledChunk {
    private final Chunk chunk;
    private final SampledWorld world;

    private Map<String, AtomicInteger> values;

    private AtomicInteger redstoneInteractions = new AtomicInteger(0);
    private AtomicInteger waterUpdates = new AtomicInteger(0);
    private AtomicInteger pistonInteractions = new AtomicInteger(0);

    public SampledChunk(Chunk chunk, SampledWorld world) {
        this.chunk = chunk;
        this.world = world;
        this.values = new HashMap<>();
    }

    public AtomicInteger get(String key) {
        return values.computeIfAbsent(key, (k) -> new AtomicInteger(0));
    }
}
