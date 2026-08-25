package art.arcane.react.model;

import art.arcane.volmlib.util.math.BlockPosition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CircuitWorldTest {
  @Test
  void oneCallbackCountsAsOneEvent() {
    CircuitWorld world = new CircuitWorld("world-id", "world");
    world.event(new BlockPosition(0, 64, 0), 1000L);
    world.rollWindow(1500L, 15000L);

    CircuitSnapshot worst = world.worst(1500L);

    assertNotNull(worst);
    assertEquals(1, worst.events());
    assertEquals(1, worst.nodes());
    assertTrue(world.isConsistent());
  }

  @Test
  void bridgeEventFullyMergesAdjacentComponents() {
    CircuitWorld world = new CircuitWorld("world-id", "world");
    world.event(new BlockPosition(0, 64, 0), 1000L);
    world.event(new BlockPosition(2, 64, 0), 1001L);
    assertEquals(2, world.countCircuits());

    world.event(new BlockPosition(1, 64, 0), 1002L);
    world.rollWindow(1500L, 15000L);

    assertEquals(1, world.countCircuits());
    assertEquals(3, world.countBlocks());
    assertEquals(3, world.worst(1500L).events());
    assertTrue(world.isConsistent());
  }

  @Test
  void removingBridgeSplitsDisconnectedComponents() {
    CircuitWorld world = new CircuitWorld("world-id", "world");
    world.event(new BlockPosition(0, 64, 0), 1000L);
    world.event(new BlockPosition(1, 64, 0), 1001L);
    world.event(new BlockPosition(2, 64, 0), 1002L);

    world.remove(new BlockPosition(1, 64, 0), 1100L);

    assertEquals(2, world.countCircuits());
    assertEquals(2, world.countBlocks());
    assertTrue(world.isConsistent());
  }

  @Test
  void expirationRemovesTopologyDeterministically() {
    CircuitWorld world = new CircuitWorld("world-id", "world");
    world.event(new BlockPosition(0, 64, 0), 1000L);

    world.rollWindow(17001L, 15000L);

    assertEquals(0, world.countCircuits());
    assertEquals(0, world.countBlocks());
    assertNull(world.worst(17001L));
    assertTrue(world.isConsistent());
  }

  @Test
  void throttleHasBoundedRecovery() {
    CircuitWorld world = new CircuitWorld("world-id", "world");
    world.event(new BlockPosition(0, 64, 0), 1000L);
    world.rollWindow(1500L, 15000L);
    CircuitSnapshot candidate = world.worst(1500L);

    CircuitSnapshot throttled = world.throttle(candidate.circuitId(), 1500L, 10000L);

    assertNotNull(throttled);
    assertNull(world.worst(2000L));
    world.event(new BlockPosition(0, 64, 0), 2000L);
    world.rollWindow(12000L, 15000L);
    assertFalse(world.worst(12000L) == null);
  }

  @Test
  void concurrentRegionActivityPreservesIndexes() throws Exception {
    CircuitWorld world = new CircuitWorld("world-id", "world");
    int workers = 4;
    int eventsPerWorker = 250;
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    List<Runnable> tasks = new ArrayList<>(workers);
    for (int worker = 0; worker < workers; worker++) {
      int offset = worker * 1000;
      tasks.add(() -> {
        ready.countDown();
        try {
          start.await();
          for (int index = 0; index < eventsPerWorker; index++) {
            world.event(new BlockPosition(offset + index, 64, 0), 1000L + index);
          }
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
        }
      });
    }
    for (Runnable task : tasks) {
      executor.execute(task);
    }
    assertTrue(ready.await(5L, TimeUnit.SECONDS));
    start.countDown();
    executor.shutdown();
    assertTrue(executor.awaitTermination(10L, TimeUnit.SECONDS));

    assertEquals(workers, world.countCircuits());
    assertEquals(workers * eventsPerWorker, world.countBlocks());
    assertTrue(world.isConsistent());
  }
}
