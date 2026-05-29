package art.arcane.react.core.controller;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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

    @Before
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
        Assert.assertNotNull(items);
        Assert.assertTrue(items.contains(item));
    }

    @Test
    public void removeItemLeavesChunkBucket() {
        UUID item = UUID.randomUUID();
        index.addItem(worldA, 0, 0, item);
        index.removeItem(item);
        Set<UUID> items = index.itemsInChunk(worldA, 0, 0);
        Assert.assertFalse(items.contains(item));
    }

    @Test
    public void hasItemsAboveReturnsTrueWhenItemPresent() {
        index.addItem(worldA, 3, 5, UUID.randomUUID());
        Assert.assertTrue(index.hasItemsAbove(worldA, 3, 5));
    }

    @Test
    public void hasItemsAboveReturnsFalseWhenEmpty() {
        Assert.assertFalse(index.hasItemsAbove(worldA, 99, 99));
    }

    @Test
    public void queryNeverPresentReturnsEmpty() {
        Set<UUID> items = index.itemsInChunk(worldA, 42, 42);
        Assert.assertNotNull(items);
        Assert.assertTrue(items.isEmpty());
    }

    @Test
    public void removeWorldClearsAllItems() {
        index.addItem(worldA, 0, 0, UUID.randomUUID());
        index.addItem(worldA, 1, 1, UUID.randomUUID());
        index.addItem(worldB, 0, 0, UUID.randomUUID());
        index.removeWorld(worldA);
        Assert.assertFalse(index.hasItemsAbove(worldA, 0, 0));
        Assert.assertFalse(index.hasItemsAbove(worldA, 1, 1));
        Assert.assertEquals(1, index.size());
    }

    @Test
    public void clearRemovesAllItems() {
        index.addItem(worldA, 0, 0, UUID.randomUUID());
        index.addItem(worldB, 2, 2, UUID.randomUUID());
        index.clear();
        Assert.assertEquals(0, index.size());
        Assert.assertFalse(index.hasItemsAbove(worldA, 0, 0));
    }

    @Test
    public void chunkMigrationUpdatesBucket() {
        UUID item = UUID.randomUUID();
        index.addItem(worldA, 0, 0, item);
        index.removeItem(item);
        index.addItem(worldA, 5, 5, item);
        Assert.assertFalse(index.hasItemsAbove(worldA, 0, 0));
        Assert.assertTrue(index.hasItemsAbove(worldA, 5, 5));
        Assert.assertTrue(index.itemsInChunk(worldA, 5, 5).contains(item));
    }

    @Test
    public void duplicateAddIsIdempotent() {
        UUID item = UUID.randomUUID();
        index.addItem(worldA, 0, 0, item);
        index.addItem(worldA, 0, 0, item);
        Assert.assertEquals(1, index.itemsInChunk(worldA, 0, 0).size());
        Assert.assertEquals(1, index.size());
    }

    @Test
    public void itemsInChunkReturnsSnapshot() {
        UUID item = UUID.randomUUID();
        index.addItem(worldA, 0, 0, item);
        Set<UUID> snapshot = index.itemsInChunk(worldA, 0, 0);
        index.removeItem(item);
        Assert.assertTrue(snapshot.contains(item));
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
        Assert.assertFalse(index.hasItemsAbove(worldA, 0, 0));
        Assert.assertFalse(index.itemsInChunk(worldA, 0, 0).contains(item1));
        Assert.assertFalse(index.itemsInChunk(worldA, 0, 0).contains(item2));
        Assert.assertTrue(index.hasItemsAbove(worldA, 1, 0));
        Assert.assertEquals(1, index.size());
    }

    @Test
    public void removeChunkAlsoRemovesFromReverseMap() {
        UUID item = UUID.randomUUID();
        index.addItem(worldA, 5, 5, item);
        index.removeChunk(worldA, 5, 5);
        index.addItem(worldA, 10, 10, item);
        Assert.assertTrue(index.hasItemsAbove(worldA, 10, 10));
        Assert.assertEquals(1, index.size());
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

        Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
    }
}
