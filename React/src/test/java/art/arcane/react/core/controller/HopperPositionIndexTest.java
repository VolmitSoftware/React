package art.arcane.react.core.controller;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        Assertions.assertEquals(1, index.hopperCount(worldA));
    }

    @Test
    public void hopperCountTracksIndividualChunkAndWorldRemoval() {
        index.addHopper(worldA, 0, 64, 0);
        index.addHopper(worldA, 16, 64, 0);
        index.addHopper(worldA, 32, 64, 0);
        Assertions.assertEquals(3, index.hopperCount(worldA));

        index.removeHopper(worldA, 16, 64, 0);
        Assertions.assertEquals(2, index.hopperCount(worldA));

        index.removeChunk(worldA, 0, 0);
        Assertions.assertEquals(1, index.hopperCount(worldA));

        index.removeWorld(worldA);
        Assertions.assertEquals(0, index.hopperCount(worldA));
    }

    @Test
    public void boundedBatchEventuallyVisitsEveryHopper() {
        int hopperCount = 100;
        int maximumBatchSize = 17;
        Set<Long> expected = new HashSet<>();
        for (int i = 0; i < hopperCount; i++) {
            int x = i * 2;
            index.addHopper(worldA, x, 64, 0);
            expected.add(HopperPositionIndex.packPos(x, 64, 0));
        }

        Set<Long> visited = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            long[] batch = index.nextHopperBatch(worldA, 1, maximumBatchSize);
            Assertions.assertTrue(batch.length <= maximumBatchSize);
            for (int j = 0; j < batch.length; j++) {
                visited.add(batch[j]);
            }
        }

        Assertions.assertEquals(expected, visited);
    }

    @Test
    public void sparseBatchHonorsConfiguredSpread() {
        long packed = HopperPositionIndex.packPos(0, 64, 0);
        index.addHopper(worldA, 0, 64, 0);

        Assertions.assertEquals(0, index.nextHopperBatch(worldA, 3, 10).length);
        Assertions.assertEquals(0, index.nextHopperBatch(worldA, 3, 10).length);
        Assertions.assertArrayEquals(new long[]{packed}, index.nextHopperBatch(worldA, 3, 10));
    }

    @Test
    public void removedHopperNeverAppearsInSubsequentBatch() {
        long removed = HopperPositionIndex.packPos(16, 64, 0);
        index.addHopper(worldA, 0, 64, 0);
        index.addHopper(worldA, 16, 64, 0);
        index.addHopper(worldA, 32, 64, 0);
        index.removeHopper(worldA, 16, 64, 0);

        long[] batch = index.nextHopperBatch(worldA, 1, 10);
        Assertions.assertEquals(2, batch.length);
        for (int i = 0; i < batch.length; i++) {
            Assertions.assertNotEquals(removed, batch[i]);
        }
    }

    @Test
    public void chunkKeyRoundTripsSignedCoordinates() {
        long key = HopperPositionIndex.chunkKey(-12345, 67890);
        Assertions.assertEquals(-12345, HopperPositionIndex.unpackChunkX(key));
        Assertions.assertEquals(67890, HopperPositionIndex.unpackChunkZ(key));
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
