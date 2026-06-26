package art.arcane.react.core.controller;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HopperItemIndexTest {

    private HopperItemIndex index;
    private UUID worldA;
    private UUID worldB;

    @BeforeEach
    public void setup() {
        index = new HopperItemIndex();
        worldA = UUID.randomUUID();
        worldB = UUID.randomUUID();
    }

    @Test
    public void addItemAppearsInChunkBucket() {
        UUID item = UUID.randomUUID();
        index.addItem(worldA, 0, 0, item);
        Set<UUID> items = index.itemsInChunk(worldA, 0, 0);
        Assertions.assertNotNull(items);
        Assertions.assertTrue(items.contains(item));
    }

    @Test
    public void removeItemLeavesChunkBucket() {
        UUID item = UUID.randomUUID();
        index.addItem(worldA, 0, 0, item);
        index.removeItem(item);
        Set<UUID> items = index.itemsInChunk(worldA, 0, 0);
        Assertions.assertFalse(items.contains(item));
    }

    @Test
    public void hasItemsAboveReturnsTrueWhenItemPresent() {
        index.addItem(worldA, 3, 5, UUID.randomUUID());
        Assertions.assertTrue(index.hasItemsAbove(worldA, 3, 5));
    }

    @Test
    public void hasItemsAboveReturnsFalseWhenEmpty() {
        Assertions.assertFalse(index.hasItemsAbove(worldA, 99, 99));
    }

    @Test
    public void queryNeverPresentReturnsEmpty() {
        Set<UUID> items = index.itemsInChunk(worldA, 42, 42);
        Assertions.assertNotNull(items);
        Assertions.assertTrue(items.isEmpty());
    }

    @Test
    public void removeWorldClearsAllItems() {
        index.addItem(worldA, 0, 0, UUID.randomUUID());
        index.addItem(worldA, 1, 1, UUID.randomUUID());
        index.addItem(worldB, 0, 0, UUID.randomUUID());
        index.removeWorld(worldA);
        Assertions.assertFalse(index.hasItemsAbove(worldA, 0, 0));
        Assertions.assertFalse(index.hasItemsAbove(worldA, 1, 1));
        Assertions.assertEquals(1, index.size());
    }

    @Test
    public void clearRemovesAllItems() {
        index.addItem(worldA, 0, 0, UUID.randomUUID());
        index.addItem(worldB, 2, 2, UUID.randomUUID());
        index.clear();
        Assertions.assertEquals(0, index.size());
        Assertions.assertFalse(index.hasItemsAbove(worldA, 0, 0));
    }

    @Test
    public void chunkMigrationUpdatesBucket() {
        UUID item = UUID.randomUUID();
        index.addItem(worldA, 0, 0, item);
        index.removeItem(item);
        index.addItem(worldA, 5, 5, item);
        Assertions.assertFalse(index.hasItemsAbove(worldA, 0, 0));
        Assertions.assertTrue(index.hasItemsAbove(worldA, 5, 5));
        Assertions.assertTrue(index.itemsInChunk(worldA, 5, 5).contains(item));
    }

    @Test
    public void duplicateAddIsIdempotent() {
        UUID item = UUID.randomUUID();
        index.addItem(worldA, 0, 0, item);
        index.addItem(worldA, 0, 0, item);
        Assertions.assertEquals(1, index.itemsInChunk(worldA, 0, 0).size());
        Assertions.assertEquals(1, index.size());
    }

    @Test
    public void itemsInChunkReturnsSnapshot() {
        UUID item = UUID.randomUUID();
        index.addItem(worldA, 0, 0, item);
        Set<UUID> snapshot = index.itemsInChunk(worldA, 0, 0);
        index.removeItem(item);
        Assertions.assertTrue(snapshot.contains(item));
    }

    @Test
    public void removeChunkClearsItems() {
        UUID item1 = UUID.randomUUID();
        UUID item2 = UUID.randomUUID();
        UUID item3 = UUID.randomUUID();
        index.addItem(worldA, 0, 0, item1);
        index.addItem(worldA, 0, 0, item2);
        index.addItem(worldA, 1, 0, item3);
        index.removeChunk(worldA, 0, 0);
        Assertions.assertFalse(index.hasItemsAbove(worldA, 0, 0));
        Assertions.assertFalse(index.itemsInChunk(worldA, 0, 0).contains(item1));
        Assertions.assertFalse(index.itemsInChunk(worldA, 0, 0).contains(item2));
        Assertions.assertTrue(index.hasItemsAbove(worldA, 1, 0));
        Assertions.assertEquals(1, index.size());
    }

    @Test
    public void removeChunkAlsoRemovesFromReverseMap() {
        UUID item = UUID.randomUUID();
        index.addItem(worldA, 5, 5, item);
        index.removeChunk(worldA, 5, 5);
        index.addItem(worldA, 10, 10, item);
        Assertions.assertTrue(index.hasItemsAbove(worldA, 10, 10));
        Assertions.assertEquals(1, index.size());
    }

    @Test
    public void concurrentAddRemoveDoesNotThrow() throws InterruptedException {
        int threads = 4;
        int opsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<UUID> items = new ArrayList<>();
        for (int i = 0; i < opsPerThread; i++) {
            items.add(UUID.randomUUID());
        }

        for (int t = 0; t < threads; t++) {
            int tid = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        UUID item = items.get(i);
                        index.addItem(worldA, tid, tid, item);
                        if (i % 2 == 0) {
                            index.removeItem(item);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
    }
}
