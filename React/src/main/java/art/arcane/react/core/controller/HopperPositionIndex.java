package art.arcane.react.core.controller;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

public class HopperPositionIndex {
    private static final long[] EMPTY_POSITIONS = new long[0];

    private final Map<UUID, WorldBuckets> hoppersByWorldChunk;

    public HopperPositionIndex() {
        hoppersByWorldChunk = new ConcurrentHashMap<>();
    }

    public void addHopper(UUID worldId, int x, int y, int z) {
        long key = chunkKey(blockToChunk(x), blockToChunk(z));
        long packed = packPos(x, y, z);
        hoppersByWorldChunk.compute(worldId, (ignored, current) -> {
            WorldBuckets worldBuckets = current == null ? new WorldBuckets() : current;
            synchronized (worldBuckets) {
                LongOpenHashSet bucket = worldBuckets.hoppersByChunk.get(key);
                if (bucket == null) {
                    bucket = new LongOpenHashSet();
                    worldBuckets.hoppersByChunk.put(key, bucket);
                }
                if (bucket.add(packed)) {
                    worldBuckets.scanIndex.put(packed, worldBuckets.scanOrder.size());
                    worldBuckets.scanOrder.add(packed);
                }
            }
            return worldBuckets;
        });
    }

    public void removeHopper(UUID worldId, int x, int y, int z) {
        long key = chunkKey(blockToChunk(x), blockToChunk(z));
        long packed = packPos(x, y, z);
        hoppersByWorldChunk.computeIfPresent(worldId, (ignored, worldBuckets) -> {
            synchronized (worldBuckets) {
                LongOpenHashSet bucket = worldBuckets.hoppersByChunk.get(key);
                if (bucket == null || !bucket.remove(packed)) {
                    return worldBuckets;
                }
                removeFromScanOrder(worldBuckets, packed);
                if (bucket.isEmpty()) {
                    worldBuckets.hoppersByChunk.remove(key);
                }
                return worldBuckets.hoppersByChunk.isEmpty() ? null : worldBuckets;
            }
        });
    }

    public void forEachHopperInChunk(UUID worldId, int chunkX, int chunkZ, LongConsumer consumer) {
        long[] positions = hoppersInChunk(worldId, chunkX, chunkZ);
        for (int i = 0; i < positions.length; i++) {
            consumer.accept(positions[i]);
        }
    }

    public long[] hoppersInChunk(UUID worldId, int chunkX, int chunkZ) {
        WorldBuckets worldBuckets = hoppersByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return EMPTY_POSITIONS;
        }
        synchronized (worldBuckets) {
            LongOpenHashSet bucket = worldBuckets.hoppersByChunk.get(chunkKey(chunkX, chunkZ));
            return bucket == null || bucket.isEmpty() ? EMPTY_POSITIONS : bucket.toLongArray();
        }
    }

    public int hopperCount(UUID worldId) {
        WorldBuckets worldBuckets = hoppersByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return 0;
        }
        synchronized (worldBuckets) {
            return worldBuckets.scanOrder.size();
        }
    }

    public long[] nextHopperBatch(UUID worldId, int spreadPasses, int maximumBatchSize) {
        if (maximumBatchSize <= 0) {
            return EMPTY_POSITIONS;
        }
        WorldBuckets worldBuckets = hoppersByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return EMPTY_POSITIONS;
        }
        synchronized (worldBuckets) {
            int size = worldBuckets.scanOrder.size();
            if (size == 0) {
                return EMPTY_POSITIONS;
            }
            int spread = Math.max(1, spreadPasses);
            long available = worldBuckets.scanRemainder + size;
            int requested = (int) Math.min(size, available / spread);
            worldBuckets.scanRemainder = available % spread;
            int count = Math.min(requested, maximumBatchSize);
            if (count == 0) {
                return EMPTY_POSITIONS;
            }
            long[] positions = new long[count];
            for (int i = 0; i < count; i++) {
                if (worldBuckets.scanCursor >= size) {
                    worldBuckets.scanCursor = 0;
                }
                positions[i] = worldBuckets.scanOrder.getLong(worldBuckets.scanCursor++);
            }
            return positions;
        }
    }

    public void removeChunk(UUID worldId, int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        hoppersByWorldChunk.computeIfPresent(worldId, (ignored, worldBuckets) -> {
            synchronized (worldBuckets) {
                LongOpenHashSet removed = worldBuckets.hoppersByChunk.remove(key);
                if (removed == null) {
                    return worldBuckets;
                }
                LongIterator iterator = removed.iterator();
                while (iterator.hasNext()) {
                    removeFromScanOrder(worldBuckets, iterator.nextLong());
                }
                return worldBuckets.hoppersByChunk.isEmpty() ? null : worldBuckets;
            }
        });
    }

    public void removeWorld(UUID worldId) {
        hoppersByWorldChunk.remove(worldId);
    }

    public void forEachHopperInWorld(UUID worldId, LongConsumer consumer) {
        WorldBuckets worldBuckets = hoppersByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return;
        }
        long[] chunkKeys;
        synchronized (worldBuckets) {
            chunkKeys = worldBuckets.hoppersByChunk.keySet().toLongArray();
        }
        for (int i = 0; i < chunkKeys.length; i++) {
            long key = chunkKeys[i];
            long[] positions = hoppersInChunk(worldId, unpackChunkX(key), unpackChunkZ(key));
            for (int j = 0; j < positions.length; j++) {
                consumer.accept(positions[j]);
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

    public static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    public static int unpackChunkX(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    public static int unpackChunkZ(long chunkKey) {
        return (int) chunkKey;
    }

    private static void removeFromScanOrder(WorldBuckets worldBuckets, long packed) {
        int removedIndex = worldBuckets.scanIndex.remove(packed);
        if (removedIndex < 0) {
            return;
        }
        int lastIndex = worldBuckets.scanOrder.size() - 1;
        long moved = worldBuckets.scanOrder.removeLong(lastIndex);
        if (removedIndex < lastIndex) {
            worldBuckets.scanOrder.set(removedIndex, moved);
            worldBuckets.scanIndex.put(moved, removedIndex);
        }
        int remaining = worldBuckets.scanOrder.size();
        if (remaining == 0) {
            worldBuckets.scanCursor = 0;
            worldBuckets.scanRemainder = 0L;
        } else if (worldBuckets.scanCursor >= remaining) {
            worldBuckets.scanCursor = 0;
        }
    }

    private static final class WorldBuckets {
        private final Long2ObjectOpenHashMap<LongOpenHashSet> hoppersByChunk;
        private final LongArrayList scanOrder;
        private final Long2IntOpenHashMap scanIndex;
        private int scanCursor;
        private long scanRemainder;

        private WorldBuckets() {
            hoppersByChunk = new Long2ObjectOpenHashMap<>();
            scanOrder = new LongArrayList();
            scanIndex = new Long2IntOpenHashMap();
            scanIndex.defaultReturnValue(-1);
        }
    }
}
