package art.arcane.react.core.controller;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HopperItemIndex {
    private static final long[] EMPTY_CHUNKS = new long[0];
    private static final UUID[] EMPTY_ITEMS = new UUID[0];

    private Map<UUID, Long2ObjectOpenHashMap<Set<UUID>>> itemsByWorldChunk;
    private Map<UUID, ItemBucketRef> bucketByItem;

    public HopperItemIndex() {
        itemsByWorldChunk = new ConcurrentHashMap<>();
        bucketByItem = new ConcurrentHashMap<>();
    }

    public void addItem(UUID worldId, int chunkX, int chunkZ, UUID itemId) {
        long key = chunkKey(chunkX, chunkZ);
        bucketByItem.compute(itemId, (ignored, existing) -> {
            if (existing != null && existing.worldId.equals(worldId) && existing.chunkKey == key) {
                return existing;
            }
            if (existing != null) {
                removeFromBucket(itemId, existing.worldId, existing.chunkKey);
            }
            addToBucket(itemId, worldId, key);
            return new ItemBucketRef(worldId, key);
        });
    }

    public void removeItem(UUID itemId) {
        bucketByItem.computeIfPresent(itemId, (ignored, ref) -> {
            removeFromBucket(itemId, ref.worldId, ref.chunkKey);
            return null;
        });
    }

    public boolean hasItemsAbove(UUID worldId, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<Set<UUID>> worldBuckets = itemsByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return false;
        }
        long key = chunkKey(chunkX, chunkZ);
        synchronized (worldBuckets) {
            Set<UUID> bucket = worldBuckets.get(key);
            return bucket != null && !bucket.isEmpty();
        }
    }

    public long[] itemChunkKeys(UUID worldId) {
        Long2ObjectOpenHashMap<Set<UUID>> worldBuckets = itemsByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return EMPTY_CHUNKS;
        }
        synchronized (worldBuckets) {
            return worldBuckets.isEmpty() ? EMPTY_CHUNKS : worldBuckets.keySet().toLongArray();
        }
    }

    public UUID[] itemIdsInChunk(UUID worldId, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<Set<UUID>> worldBuckets = itemsByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return EMPTY_ITEMS;
        }
        long key = chunkKey(chunkX, chunkZ);
        synchronized (worldBuckets) {
            Set<UUID> bucket = worldBuckets.get(key);
            return bucket == null || bucket.isEmpty() ? EMPTY_ITEMS : bucket.toArray(new UUID[bucket.size()]);
        }
    }

    public void removeChunk(UUID worldId, int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        Set<UUID> removedItems = new HashSet<>();
        itemsByWorldChunk.computeIfPresent(worldId, (ignored, worldBuckets) -> {
            synchronized (worldBuckets) {
                Set<UUID> removed = worldBuckets.remove(key);
                if (removed != null) {
                    removedItems.addAll(removed);
                }
                return worldBuckets.isEmpty() ? null : worldBuckets;
            }
        });
        for (UUID itemId : removedItems) {
            bucketByItem.computeIfPresent(itemId, (ignored, ref) -> {
                if (!worldId.equals(ref.worldId) || ref.chunkKey != key) {
                    return ref;
                }
                removeFromBucket(itemId, ref.worldId, ref.chunkKey);
                return null;
            });
        }
    }

    public void removeWorld(UUID worldId) {
        Long2ObjectOpenHashMap<Set<UUID>> worldBuckets = itemsByWorldChunk.remove(worldId);
        if (worldBuckets == null) {
            return;
        }
        Set<UUID> removedItems = new HashSet<>();
        synchronized (worldBuckets) {
            for (Set<UUID> bucket : worldBuckets.values()) {
                removedItems.addAll(bucket);
            }
        }
        for (UUID itemId : removedItems) {
            bucketByItem.computeIfPresent(itemId, (ignored, ref) -> {
                if (!worldId.equals(ref.worldId)) {
                    return ref;
                }
                removeFromBucket(itemId, ref.worldId, ref.chunkKey);
                return null;
            });
        }
    }

    public void clear() {
        itemsByWorldChunk.clear();
        bucketByItem.clear();
    }

    public int size() {
        return bucketByItem.size();
    }

    public Set<UUID> allItemIds() {
        return new HashSet<>(bucketByItem.keySet());
    }

    private void addToBucket(UUID itemId, UUID worldId, long key) {
        itemsByWorldChunk.compute(worldId, (ignored, current) -> {
            Long2ObjectOpenHashMap<Set<UUID>> worldBuckets = current == null
                ? new Long2ObjectOpenHashMap<>()
                : current;
            synchronized (worldBuckets) {
                Set<UUID> bucket = worldBuckets.get(key);
                if (bucket == null) {
                    bucket = new HashSet<>();
                    worldBuckets.put(key, bucket);
                }
                bucket.add(itemId);
            }
            return worldBuckets;
        });
    }

    private void removeFromBucket(UUID itemId, UUID worldId, long key) {
        itemsByWorldChunk.computeIfPresent(worldId, (ignored, worldBuckets) -> {
            synchronized (worldBuckets) {
                Set<UUID> bucket = worldBuckets.get(key);
                if (bucket == null) {
                    return worldBuckets;
                }
                bucket.remove(itemId);
                if (bucket.isEmpty()) {
                    worldBuckets.remove(key);
                }
                return worldBuckets.isEmpty() ? null : worldBuckets;
            }
        });
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static final class ItemBucketRef {
        private final UUID worldId;
        private final long chunkKey;

        private ItemBucketRef(UUID worldId, long chunkKey) {
            this.worldId = worldId;
            this.chunkKey = chunkKey;
        }
    }
}
