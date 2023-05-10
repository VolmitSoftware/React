package com.volmit.react.model;

import com.volmit.react.util.cache.Cache;
import lombok.Data;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

@Data
public class SampledWorld {
    private final World world;
    private final Map<Long, SampledChunk> chunks;

    public SampledWorld(World world) {
        this.world = world;
        chunks = new HashMap<>();
    }

    public void remove(Chunk c) {
        remove(c.getX(), c.getZ());
    }

    public void remove(int x, int z) {
        chunks.remove(Cache.key(x, z));
    }

    public SampledChunk getChunk(Chunk c) {
        return getChunk(c.getX(), c.getZ());
    }

    public SampledChunk getChunk(int x, int z) {
       return chunks.computeIfAbsent(Cache.key(x, z), (k) -> new SampledChunk(world.getChunkAt(x, z), this));
    }
}
