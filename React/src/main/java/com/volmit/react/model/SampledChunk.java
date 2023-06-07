package com.volmit.react.model;

import art.arcane.chrono.ChronoLatch;
import com.google.common.util.concurrent.AtomicDouble;
import lombok.Data;
import org.bukkit.Chunk;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

@Data
public class SampledChunk {
    private static final DoubleUnaryOperator HALF = (v) -> v * 0.5D;
    private final Chunk chunk;
    private final SampledWorld world;
    private final ChronoLatch cleaner;
    private Map<String, AtomicDouble> values;

    public SampledChunk(Chunk chunk, SampledWorld world) {
        this.chunk = chunk;
        this.world = world;
        this.values = new HashMap<>();
        this.cleaner = new ChronoLatch(1000);
    }

    public double highestSubScore() {
        return values.values().stream().mapToDouble(AtomicDouble::get).max().orElse(0);
    }

    public double totalScore() {
        return values.values().stream().mapToDouble(AtomicDouble::get).sum();
    }

    public Optional<AtomicDouble> optional(String key) {
        return values.containsKey(key) ? Optional.of(values.get(key)) : Optional.empty();
    }

    public AtomicDouble get(String key) {
        if (cleaner.flip()) {
            cleanup();
        }

        return values.computeIfAbsent(key, (k) -> new AtomicDouble(0D));
    }

    private void cleanup() {
        String remove = null;

        for (String i : values.keySet()) {
            double v = values.get(i).updateAndGet(HALF);

            if (remove == null && v <= 1) {
                remove = i;
            }
        }

        if (remove != null) {
            values.remove(remove);
        }
    }
}
