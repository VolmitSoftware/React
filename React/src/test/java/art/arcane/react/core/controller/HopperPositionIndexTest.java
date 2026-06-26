package art.arcane.react.core.controller;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @BeforeEach
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
        Assertions.assertEquals(1, found.size());
    }

    @Test
    public void removeHopperLeavesChunk() {
        index.addHopper(worldA, 16, 64, 16);
        index.removeHopper(worldA, 16, 64, 16);
        List<Long> found = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 1, 1, found::add);
        Assertions.assertTrue(found.isEmpty());
    }

    @Test
    public void forEachHopperInChunkIteratesCorrectly() {
        index.addHopper(worldA, 0, 64, 0);
        index.addHopper(worldA, 15, 64, 15);
        index.addHopper(worldA, 48, 64, 48);
        List<Long> chunkZeroZero = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 0, 0, chunkZeroZero::add);
        Assertions.assertEquals(2, chunkZeroZero.size());
        List<Long> chunkThreeThree = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 3, 3, chunkThreeThree::add);
        Assertions.assertEquals(1, chunkThreeThree.size());
    }

    @Test
    public void removeChunkClearsHoppers() {
        index.addHopper(worldA, 0, 64, 0);
        index.addHopper(worldA, 1, 64, 0);
        index.removeChunk(worldA, 0, 0);
        List<Long> found = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 0, 0, found::add);
        Assertions.assertTrue(found.isEmpty());
    }

    @Test
    public void removeWorldClearsAllHoppers() {
        index.addHopper(worldA, 0, 64, 0);
        index.addHopper(worldA, 32, 64, 32);
        index.addHopper(worldB, 0, 64, 0);
        index.removeWorld(worldA);
        List<Long> foundA = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 0, 0, foundA::add);
        Assertions.assertTrue(foundA.isEmpty());
        List<Long> foundB = new ArrayList<>();
        index.forEachHopperInChunk(worldB, 0, 0, foundB::add);
        Assertions.assertEquals(1, foundB.size());
    }

    @Test
    public void clearRemovesAllHoppers() {
        index.addHopper(worldA, 0, 64, 0);
        index.addHopper(worldB, 16, 64, 16);
        index.clear();
        List<Long> foundA = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 0, 0, foundA::add);
        Assertions.assertTrue(foundA.isEmpty());
    }

    @Test
    public void queryNeverPresentReturnsEmpty() {
        List<Long> found = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 100, 100, found::add);
        Assertions.assertTrue(found.isEmpty());
    }

    @Test
    public void duplicateAddIsIdempotent() {
        index.addHopper(worldA, 0, 64, 0);
        index.addHopper(worldA, 0, 64, 0);
        List<Long> found = new ArrayList<>();
        index.forEachHopperInChunk(worldA, 0, 0, found::add);
        Assertions.assertEquals(1, found.size());
    }

    @Test
    public void negativeCoordinatesRoundTripCorrectly() {
        index.addHopper(worldA, -16, 64, -16);
        List<Long> found = new ArrayList<>();
        index.forEachHopperInChunk(worldA, -1, -1, found::add);
        Assertions.assertEquals(1, found.size());
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

        Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        CopyOnWriteArrayList<Long> collected = new CopyOnWriteArrayList<>();
        for (int cx = 0; cx < count; cx++) {
            index.forEachHopperInChunk(worldA, cx, 0, collected::add);
        }
        Assertions.assertEquals(count, collected.size());
    }

    @Property
    public void packPosRoundTripsCoordinates(
            @ForAll @IntRange(min = -33554432, max = 33554431) int x,
            @ForAll @IntRange(min = -2048, max = 2047) int y,
            @ForAll @IntRange(min = -33554432, max = 33554431) int z) {
        long packed = HopperPositionIndex.packPos(x, y, z);
        Assertions.assertEquals(x, HopperPositionIndex.unpackX(packed));
        Assertions.assertEquals(y, HopperPositionIndex.unpackY(packed));
        Assertions.assertEquals(z, HopperPositionIndex.unpackZ(packed));
    }
}
