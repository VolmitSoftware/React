package art.arcane.react.core.controller;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class HopperPositionIndex {
    private Map<UUID, Map<Long, Set<Long>>> hoppersByWorldChunk;

    public HopperPositionIndex() {
        hoppersByWorldChunk = new ConcurrentHashMap<>();
    }

    public void addHopper(UUID worldId, int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        long key = chunkKey(cx, cz);
        long packed = packPos(x, y, z);
        Map<Long, Set<Long>> worldBuckets = hoppersByWorldChunk.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>());
        Set<Long> bucket = worldBuckets.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
        bucket.add(packed);
    }

    public void removeHopper(UUID worldId, int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        long key = chunkKey(cx, cz);
        long packed = packPos(x, y, z);
        Map<Long, Set<Long>> worldBuckets = hoppersByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return;
        }
        Set<Long> bucket = worldBuckets.get(key);
        if (bucket == null) {
            return;
        }
        bucket.remove(packed);
        if (bucket.isEmpty()) {
            worldBuckets.remove(key, bucket);
        }
        if (worldBuckets.isEmpty()) {
            hoppersByWorldChunk.remove(worldId, worldBuckets);
        }
    }

    public void forEachHopperInChunk(UUID worldId, int cx, int cz, Consumer<Long> consumer) {
        Map<Long, Set<Long>> worldBuckets = hoppersByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return;
        }
        Set<Long> bucket = worldBuckets.get(chunkKey(cx, cz));
        if (bucket == null || bucket.isEmpty()) {
            return;
        }
        for (Long pos : bucket) {
            consumer.accept(pos);
        }
    }

    public void removeChunk(UUID worldId, int cx, int cz) {
        Map<Long, Set<Long>> worldBuckets = hoppersByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return;
        }
        worldBuckets.remove(chunkKey(cx, cz));
        if (worldBuckets.isEmpty()) {
            hoppersByWorldChunk.remove(worldId, worldBuckets);
        }
    }

    public void removeWorld(UUID worldId) {
        hoppersByWorldChunk.remove(worldId);
    }

    public void forEachHopperInWorld(UUID worldId, Consumer<Long> consumer) {
        Map<Long, Set<Long>> worldBuckets = hoppersByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return;
        }
        for (Set<Long> bucket : worldBuckets.values()) {
            for (Long pos : bucket) {
                consumer.accept(pos);
            }
        }
    }

    public void clear() {
        hoppersByWorldChunk.clear();
    }

    public static int blockToChunk(int coord) {
        return coord >> 4;
    }

    public static long packPos(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    public static int unpackX(long packed) {
        return (int)(packed >> 38);
    }

    public static int unpackY(long packed) {
        int raw = (int)((packed >> 26) & 0xFFF);
        return (raw << 20) >> 20;
    }

    public static int unpackZ(long packed) {
        int raw = (int)(packed & 0x3FFFFFF);
        return (raw << 6) >> 6;
    }

    public static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
    }
}
