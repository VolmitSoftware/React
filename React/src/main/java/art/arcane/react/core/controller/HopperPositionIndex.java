package art.arcane.react.core.controller;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class HopperPositionIndex {
    private Map<UUID, Long2ObjectOpenHashMap<LongOpenHashSet>> hoppersByWorldChunk;

    public HopperPositionIndex() {
        hoppersByWorldChunk = new ConcurrentHashMap<>();
    }

    public void addHopper(UUID worldId, int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        long key = chunkKey(cx, cz);
        long packed = packPos(x, y, z);
        Long2ObjectOpenHashMap<LongOpenHashSet> worldBuckets = hoppersByWorldChunk
            .computeIfAbsent(worldId, ignored -> new Long2ObjectOpenHashMap<>());
        synchronized (worldBuckets) {
            LongOpenHashSet bucket = worldBuckets.get(key);
            if (bucket == null) {
                bucket = new LongOpenHashSet();
                worldBuckets.put(key, bucket);
            }
            bucket.add(packed);
        }
    }

    public void removeHopper(UUID worldId, int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        long key = chunkKey(cx, cz);
        long packed = packPos(x, y, z);
        Long2ObjectOpenHashMap<LongOpenHashSet> worldBuckets = hoppersByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return;
        }
        boolean worldEmpty;
        synchronized (worldBuckets) {
            LongOpenHashSet bucket = worldBuckets.get(key);
            if (bucket == null) {
                return;
            }
            bucket.remove(packed);
            if (bucket.isEmpty()) {
                worldBuckets.remove(key);
            }
            worldEmpty = worldBuckets.isEmpty();
        }
        if (worldEmpty) {
            hoppersByWorldChunk.remove(worldId, worldBuckets);
        }
    }

    public void forEachHopperInChunk(UUID worldId, int cx, int cz, Consumer<Long> consumer) {
        Long2ObjectOpenHashMap<LongOpenHashSet> worldBuckets = hoppersByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return;
        }
        long key = chunkKey(cx, cz);
        long[] snapshot;
        synchronized (worldBuckets) {
            LongOpenHashSet bucket = worldBuckets.get(key);
            if (bucket == null || bucket.isEmpty()) {
                return;
            }
            snapshot = bucket.toLongArray();
        }
        for (int i = 0; i < snapshot.length; i++) {
            consumer.accept(snapshot[i]);
        }
    }

    public void removeChunk(UUID worldId, int cx, int cz) {
        Long2ObjectOpenHashMap<LongOpenHashSet> worldBuckets = hoppersByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return;
        }
        boolean worldEmpty;
        synchronized (worldBuckets) {
            worldBuckets.remove(chunkKey(cx, cz));
            worldEmpty = worldBuckets.isEmpty();
        }
        if (worldEmpty) {
            hoppersByWorldChunk.remove(worldId, worldBuckets);
        }
    }

    public void removeWorld(UUID worldId) {
        hoppersByWorldChunk.remove(worldId);
    }

    public void forEachHopperInWorld(UUID worldId, Consumer<Long> consumer) {
        Long2ObjectOpenHashMap<LongOpenHashSet> worldBuckets = hoppersByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return;
        }
        long[] snapshot;
        int total;
        synchronized (worldBuckets) {
            total = 0;
            for (LongOpenHashSet bucket : worldBuckets.values()) {
                total += bucket.size();
            }
            snapshot = new long[total];
            int index = 0;
            for (LongOpenHashSet bucket : worldBuckets.values()) {
                LongIterator iterator = bucket.iterator();
                while (iterator.hasNext()) {
                    snapshot[index++] = iterator.nextLong();
                }
            }
        }
        for (int i = 0; i < snapshot.length; i++) {
            consumer.accept(snapshot[i]);
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
