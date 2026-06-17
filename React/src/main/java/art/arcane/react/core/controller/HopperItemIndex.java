package art.arcane.react.core.controller;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HopperItemIndex {
    private Map<UUID, Long2ObjectOpenHashMap<Set<UUID>>> itemsByWorldChunk;
    private Map<UUID, ItemBucketRef> bucketByItem;

    public HopperItemIndex() {
        itemsByWorldChunk = new ConcurrentHashMap<>();
        bucketByItem = new ConcurrentHashMap<>();
    }

    public void addItem(UUID worldId, int chunkX, int chunkZ, UUID itemId) {
        long key = chunkKey(chunkX, chunkZ);
        ItemBucketRef existing = bucketByItem.get(itemId);
        if (existing != null && existing.worldId.equals(worldId) && existing.chunkKey == key) {
            return;
        }
        if (existing != null) {
            removeFromBucket(itemId, existing.worldId, existing.chunkKey);
        }
        addToBucket(itemId, worldId, key);
        bucketByItem.put(itemId, new ItemBucketRef(worldId, key));
    }

    public void removeItem(UUID itemId) {
        ItemBucketRef ref = bucketByItem.remove(itemId);
        if (ref != null) {
            removeFromBucket(itemId, ref.worldId, ref.chunkKey);
        }
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

    public Set<UUID> itemsInChunk(UUID worldId, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<Set<UUID>> worldBuckets = itemsByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return Collections.emptySet();
        }
        long key = chunkKey(chunkX, chunkZ);
        synchronized (worldBuckets) {
            Set<UUID> bucket = worldBuckets.get(key);
            if (bucket == null || bucket.isEmpty()) {
                return Collections.emptySet();
            }
            return new HashSet<>(bucket);
        }
    }

    public void removeChunk(UUID worldId, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<Set<UUID>> worldBuckets = itemsByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return;
        }
        long key = chunkKey(chunkX, chunkZ);
        Set<UUID> removed;
        boolean worldEmpty;
        synchronized (worldBuckets) {
            removed = worldBuckets.remove(key);
            worldEmpty = worldBuckets.isEmpty();
        }
        if (removed != null) {
            for (UUID itemId : removed) {
                ItemBucketRef ref = bucketByItem.get(itemId);
                if (ref != null && worldId.equals(ref.worldId) && ref.chunkKey == key) {
                    bucketByItem.remove(itemId);
                }
            }
        }
        if (worldEmpty) {
            itemsByWorldChunk.remove(worldId, worldBuckets);
        }
    }

    public void removeWorld(UUID worldId) {
        Long2ObjectOpenHashMap<Set<UUID>> worldBuckets = itemsByWorldChunk.remove(worldId);
        if (worldBuckets == null) {
            return;
        }
        synchronized (worldBuckets) {
            for (Set<UUID> bucket : worldBuckets.values()) {
                for (UUID itemId : bucket) {
                    ItemBucketRef ref = bucketByItem.get(itemId);
                    if (ref != null && worldId.equals(ref.worldId)) {
                        bucketByItem.remove(itemId);
                    }
                }
            }
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
        Long2ObjectOpenHashMap<Set<UUID>> worldBuckets = itemsByWorldChunk
            .computeIfAbsent(worldId, ignored -> new Long2ObjectOpenHashMap<>());
        synchronized (worldBuckets) {
            Set<UUID> bucket = worldBuckets.get(key);
            if (bucket == null) {
                bucket = new HashSet<>();
                worldBuckets.put(key, bucket);
            }
            bucket.add(itemId);
        }
    }

    private void removeFromBucket(UUID itemId, UUID worldId, long key) {
        Long2ObjectOpenHashMap<Set<UUID>> worldBuckets = itemsByWorldChunk.get(worldId);
        if (worldBuckets == null) {
            return;
        }
        boolean worldEmpty;
        synchronized (worldBuckets) {
            Set<UUID> bucket = worldBuckets.get(key);
            if (bucket == null) {
                return;
            }
            bucket.remove(itemId);
            if (bucket.isEmpty()) {
                worldBuckets.remove(key);
            }
            worldEmpty = worldBuckets.isEmpty();
        }
        if (worldEmpty) {
            itemsByWorldChunk.remove(worldId, worldBuckets);
        }
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
