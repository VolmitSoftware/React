package com.volmit.react.model;

import art.arcane.chrono.ChronoLatch;
import com.volmit.react.util.math.BlockPosition;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RNG;
import lombok.Data;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Data
public class Circuit {
    private final long id;
    private final Set<BlockPosition> positions;
    private final AtomicInteger events;
    private final AtomicInteger eventBuffer;
    private final AtomicLong lastEvent;
    private final AtomicBoolean stop;

    public Circuit(long id) {
        this.id = id;
        this.positions = new HashSet<>();
        this.events = new AtomicInteger(0);
        this.eventBuffer = new AtomicInteger(0);
        this.lastEvent = new AtomicLong(M.ms());
        this.stop = new AtomicBoolean(false);
    }

    public int countBlocks() {
        return positions.size();
    }

    public void write(Block block) {
        positions.add(new BlockPosition(block));
        eventBuffer.incrementAndGet();
        lastEvent.set(M.ms());
    }

    public void remove(BlockPosition block) {
        positions.remove(block);
    }

    public void stop() {
        stop.set(true);
    }

    public void tick() {
        events.set(eventBuffer.getAndSet(0));
        if(M.ms() - lastEvent.get() > 10000) {
            positions.clear();
        }
    }
}
