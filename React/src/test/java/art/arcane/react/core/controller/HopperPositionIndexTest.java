package art.arcane.react.core.controller;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HopperPositionIndexTest {

    private HopperPositionIndex index;
    private UUID worldA;
    private UUID worldB;

    @Before
    public void setup() {
        index = new HopperPositionIndex();
        worldA = UUID.randomUUID();
        worldB = UUID.randomUUID();
    }

    @Test
    public void addHopperAppearsInChunk() {
        index.addHopper(worldA, 16, 64, 16);
        List<long[]> found = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 1, 1, pos -> found.add(new long[]{pos}));
        Assert.assertEquals(1, found.size());
    }

    @Test
    public void removeHopperLeavesChunk() {
        index.addHopper(worldA, 16, 64, 16);
        index.removeHopper(worldA, 16, 64, 16);
        List<Long> found = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 1, 1, found::add);
        Assert.assertTrue(found.isEmpty());
    }

    @Test
    public void forEachHopperInChunkIteratesCorrectly() {
        index.addHopper(worldA, 0, 64, 0);
        index.addHopper(worldA, 15, 64, 15);
        index.addHopper(worldA, 48, 64, 48);
        List<Long> chunkZeroZero = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 0, 0, chunkZeroZero::add);
        Assert.assertEquals(2, chunkZeroZero.size());
        List<Long> chunkThreeThree = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 3, 3, chunkThreeThree::add);
        Assert.assertEquals(1, chunkThreeThree.size());
    }

    @Test
    public void removeChunkClearsHoppers() {
        index.addHopper(worldA, 0, 64, 0);
        index.addHopper(worldA, 1, 64, 0);
        index.removeChunk(worldA, 0, 0);
        List<Long> found = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 0, 0, found::add);
        Assert.assertTrue(found.isEmpty());
    }

    @Test
    public void removeWorldClearsAllHoppers() {
        index.addHopper(worldA, 0, 64, 0);
        index.addHopper(worldA, 32, 64, 32);
        index.addHopper(worldB, 0, 64, 0);
        index.removeWorld(worldA);
        List<Long> foundA = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 0, 0, foundA::add);
        Assert.assertTrue(foundA.isEmpty());
        List<Long> foundB = new ArrayList<>();
        index.forEachHopperInChunk(worldB, 0, 0, foundB::add);
        Assert.assertEquals(1, foundB.size());
    }

    @Test
    public void clearRemovesAllHoppers() {
        index.addHopper(worldA, 0, 64, 0);
        index.addHopper(worldB, 16, 64, 16);
        index.clear();
        List<Long> foundA = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 0, 0, foundA::add);
        Assert.assertTrue(foundA.isEmpty());
    }

    @Test
    public void queryNeverPresentReturnsEmpty() {
        List<Long> found = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 100, 100, found::add);
        Assert.assertTrue(found.isEmpty());
    }

    @Test
    public void duplicateAddIsIdempotent() {
        index.addHopper(worldA, 0, 64, 0);
        index.addHopper(worldA, 0, 64, 0);
        List<Long> found = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 0, 0, found::add);
        Assert.assertEquals(1, found.size());
    }

    @Test
    public void negativeCoordinatesRoundTripCorrectly() {
        index.addHopper(worldA, -16, 64, -16);
        List<Long> found = new ArrayList<>();
        index.forEachHopperInChunk(worldA, -1, -1, found::add);
        Assert.assertEquals(1, found.size());
    }

    @Test
    public void concurrentAddAndIterate() throws InterruptedException {
        int count = 200;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            int x = i * 16;
            executor.submit(() -> {
                try {
                    index.addHopper(worldA, x, 64, 0);
                } finally {
                    latch.countDown();
                }
            });
        }

        Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        CopyOnWriteArrayList<Long> collected = new CopyOnWriteArrayList<>();
        for (int cx = 0; cx < count; cx++) {
            index.forEachHopperInChunk(worldA, cx, 0, collected::add);
        }
        Assert.assertEquals(count, collected.size());
    }
}
