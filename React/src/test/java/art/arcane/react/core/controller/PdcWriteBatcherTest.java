package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class PdcWriteBatcherTest {
  private static final int MAX_PENDING_PER_CHUNK = 256;

  @Test
  void nearbyWritesKeepImmediateBypassSemantics() {
    PdcWriteBatcher batcher = new PdcWriteBatcher();
    World world = mockWorld(UUID.randomUUID());
    BlockState state = mockState(world, 1, 64, 2);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.hasNearbyPlayer(Mockito.any(Location.class), Mockito.eq(16D))).thenReturn(true);

      batcher.enqueue(state, true, false);

      Mockito.verify(state).update(true, false);
      Assertions.assertEquals(1L, batcher.getImmediateCount());
      Assertions.assertEquals(0L, batcher.getDeferredCount());
      Assertions.assertEquals(0L, batcher.getFlushedCount());
    }
  }

  @Test
  void paperFlushAppliesQueuedWritesAndPreservesMetrics() {
    UUID worldId = UUID.randomUUID();
    World world = mockWorld(worldId);
    BlockState state = mockState(world, 17, 70, -1);
    PdcWriteBatcher batcher = deferredBatcher();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(J::isFoliaThreading).thenReturn(false);

      batcher.enqueue(state, false, true);
      batcher.flushAll();

      Mockito.verify(state).update(false, true);
      Assertions.assertEquals(1L, batcher.getDeferredCount());
      Assertions.assertEquals(0L, batcher.getImmediateCount());
      Assertions.assertEquals(1L, batcher.getFlushedCount());
    }
  }

  @Test
  void chunkUnloadDrainsAcceptedFlightOnItsOwnerBeforeTheChunkBecomesUnavailable() {
    UUID worldId = UUID.randomUUID();
    World world = mockWorld(worldId);
    BlockState state = mockState(world, 33, 70, -17);
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(chunk.getWorld()).thenReturn(world);
    Mockito.when(chunk.getX()).thenReturn(2);
    Mockito.when(chunk.getZ()).thenReturn(-2);
    ChunkUnloadEvent event = Mockito.mock(ChunkUnloadEvent.class);
    Mockito.when(event.getChunk()).thenReturn(chunk);
    PdcWriteBatcher batcher = deferredBatcher();
    AtomicReference<Runnable> scheduled = new AtomicReference<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
        MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.eq(2), Mockito.eq(-2), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            scheduled.set(invocation.getArgument(3));
            return true;
          });

      batcher.enqueue(state, false, true);
      batcher.flushAll();
      Mockito.verify(state, Mockito.never()).update(Mockito.anyBoolean(), Mockito.anyBoolean());

      batcher.onChunkUnload(event);

      Mockito.verify(state).update(false, true);
      Assertions.assertEquals(1L, batcher.getFlushedCount());
      Assertions.assertEquals(0L, batcher.getRetiredUnavailableWriteCount());
      scheduled.get().run();
      Mockito.verify(state).update(false, true);
      Assertions.assertDoesNotThrow(batcher::stop);
      react.verify(() -> React.reportError(Mockito.any(Throwable.class)), Mockito.never());
    }
  }

  @Test
  void worldUnloadRetiresAcceptedFlightAndStopReportsItsExplicitDisposition() {
    UUID worldId = UUID.randomUUID();
    World world = mockWorld(worldId);
    BlockState state = mockState(world, -17, 64, 49);
    WorldUnloadEvent event = Mockito.mock(WorldUnloadEvent.class);
    Mockito.when(event.getWorld()).thenReturn(world);
    PdcWriteBatcher batcher = deferredBatcher();
    AtomicReference<Runnable> scheduled = new AtomicReference<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
        MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.eq(-2), Mockito.eq(3), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            scheduled.set(invocation.getArgument(3));
            return true;
          });

      batcher.enqueue(state, true, false);
      batcher.flushAll();
      batcher.onWorldUnload(event);
      scheduled.get().run();

      Mockito.verify(state, Mockito.never()).update(Mockito.anyBoolean(), Mockito.anyBoolean());
      Assertions.assertEquals(1L, batcher.getRetiredUnavailableWriteCount());
      IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, batcher::stop);
      Assertions.assertTrue(failure.getMessage().contains("disposition=FAILED_WORLD_UNAVAILABLE"));
      Assertions.assertTrue(failure.getMessage().contains("world=" + worldId));
      Assertions.assertTrue(failure.getMessage().contains("chunkX=-2"));
      Assertions.assertTrue(failure.getMessage().contains("chunkZ=3"));
      react.verify(() -> React.reportError(Mockito.argThat(throwable ->
          throwable.getMessage().contains("disposition=FAILED_WORLD_UNAVAILABLE")
              && throwable.getMessage().contains("world=" + worldId)
              && throwable.getMessage().contains("inFlight=1"))));
    }
  }

  @Test
  void missingWorldRetiresPendingQueueAndStopFailsWithoutWaitingForTimeout() {
    UUID worldId = UUID.randomUUID();
    World world = mockWorld(worldId);
    BlockState state = mockState(world, 17, 64, 17);
    PdcWriteBatcher batcher = deferredBatcher();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(null);

      batcher.enqueue(state, false, false);
      batcher.flushAll();
      long startedNanos = System.nanoTime();
      IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, batcher::stop);
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);

      Assertions.assertTrue(elapsedMillis < 1_000L, "missing-world stop retried instead of retiring the queue");
      Assertions.assertEquals(1L, batcher.getRetiredUnavailableWriteCount());
      Assertions.assertTrue(failure.getMessage().contains("disposition=FAILED_WORLD_UNAVAILABLE"));
      Assertions.assertTrue(failure.getMessage().contains("reason=Bukkit no longer exposes the owning world"));
      Assertions.assertTrue(failure.getMessage().contains("pending=1"));
      Mockito.verify(state, Mockito.never()).update(Mockito.anyBoolean(), Mockito.anyBoolean());
      react.verify(() -> React.reportError(Mockito.argThat(throwable ->
          throwable.getMessage().contains("disposition=FAILED_WORLD_UNAVAILABLE")
              && throwable.getMessage().contains("world=" + worldId)
              && throwable.getMessage().contains("pending=1"))));
    }
  }

  @Test
  void acceptedFoliaFlightCountsTowardCapUntilAcknowledged() {
    UUID worldId = UUID.randomUUID();
    World world = mockWorld(worldId);
    BlockState state = mockState(world, 1, 64, 1);
    PdcWriteBatcher batcher = deferredBatcher();
    AtomicReference<Runnable> scheduled = new AtomicReference<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.eq(0), Mockito.eq(0), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            scheduled.set(invocation.getArgument(3));
            return true;
          });

      for (int index = 0; index < MAX_PENDING_PER_CHUNK; index++) {
        batcher.enqueue(state, false, false);
      }
      batcher.flushAll();
      Assertions.assertNotNull(scheduled.get());

      batcher.enqueue(state, true, true);
      Mockito.verify(state).update(true, true);
      Assertions.assertEquals(MAX_PENDING_PER_CHUNK, batcher.getDeferredCount());
      Assertions.assertEquals(1L, batcher.getImmediateCount());

      scheduled.get().run();
      Mockito.verify(state, Mockito.times(MAX_PENDING_PER_CHUNK + 1))
          .update(Mockito.anyBoolean(), Mockito.anyBoolean());
      Assertions.assertEquals(MAX_PENDING_PER_CHUNK, batcher.getFlushedCount());

      batcher.enqueue(state, false, true);
      Assertions.assertEquals(MAX_PENDING_PER_CHUNK + 1L, batcher.getDeferredCount());
      Assertions.assertEquals(1L, batcher.getImmediateCount());
      Mockito.verify(state, Mockito.times(MAX_PENDING_PER_CHUNK + 1))
          .update(Mockito.anyBoolean(), Mockito.anyBoolean());
    }
  }

  @Test
  void rejectedFlightReinsertsWithoutReorderingOrDroppingWrites() {
    UUID worldId = UUID.randomUUID();
    World world = mockWorld(worldId);
    BlockState first = mockState(world, 1, 64, 1);
    BlockState second = mockState(world, 2, 64, 1);
    PdcWriteBatcher batcher = deferredBatcher();
    AtomicInteger attempts = new AtomicInteger();
    AtomicReference<Runnable> scheduled = new AtomicReference<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.eq(0), Mockito.eq(0), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
              return false;
            }
            scheduled.set(invocation.getArgument(3));
            return true;
          });

      batcher.enqueue(first, false, false);
      batcher.flushAll();
      batcher.enqueue(second, true, true);
      batcher.flushAll();
      scheduled.get().run();

      InOrder order = Mockito.inOrder(first, second);
      order.verify(first).update(false, false);
      order.verify(second).update(true, true);
      Assertions.assertEquals(2L, batcher.getDeferredCount());
      Assertions.assertEquals(0L, batcher.getImmediateCount());
      Assertions.assertEquals(2L, batcher.getFlushedCount());
    }
  }

  @Test
  void rejectedFlightRetainsThePerChunkCapWithoutDroppingStates() {
    UUID worldId = UUID.randomUUID();
    World world = mockWorld(worldId);
    BlockState state = mockState(world, 1, 64, 1);
    PdcWriteBatcher batcher = deferredBatcher();
    AtomicInteger attempts = new AtomicInteger();
    AtomicReference<Runnable> scheduled = new AtomicReference<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.eq(0), Mockito.eq(0), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
              return false;
            }
            scheduled.set(invocation.getArgument(3));
            return true;
          });

      for (int index = 0; index < MAX_PENDING_PER_CHUNK; index++) {
        batcher.enqueue(state, false, false);
      }
      batcher.flushAll();
      batcher.enqueue(state, true, true);

      Mockito.verify(state).update(true, true);
      Assertions.assertEquals(MAX_PENDING_PER_CHUNK, batcher.getDeferredCount());
      Assertions.assertEquals(1L, batcher.getImmediateCount());

      batcher.flushAll();
      scheduled.get().run();

      Mockito.verify(state, Mockito.times(MAX_PENDING_PER_CHUNK + 1))
          .update(Mockito.anyBoolean(), Mockito.anyBoolean());
      Assertions.assertEquals(MAX_PENDING_PER_CHUNK, batcher.getFlushedCount());
    }
  }

  @Test
  void stopDrainsPendingFoliaWritesBeforeReturning() {
    UUID worldId = UUID.randomUUID();
    World world = mockWorld(worldId);
    BlockState state = mockState(world, 1, 64, 1);
    PdcWriteBatcher batcher = deferredBatcher();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.eq(0), Mockito.eq(0), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            Runnable callback = invocation.getArgument(3);
            callback.run();
            return true;
          });

      batcher.enqueue(state, false, false);
      batcher.stop();

      Mockito.verify(state).update(false, false);
      Assertions.assertEquals(1L, batcher.getFlushedCount());
    }
  }

  @Test
  void stopWaitsForAcceptedFlightAcknowledgement() throws Exception {
    UUID worldId = UUID.randomUUID();
    World world = mockWorld(worldId);
    BlockState state = mockState(world, 1, 64, 1);
    PdcWriteBatcher batcher = deferredBatcher();
    AtomicReference<Runnable> scheduled = new AtomicReference<>();
    CountDownLatch stopStarted = new CountDownLatch(1);
    ExecutorService callbackExecutor = Executors.newSingleThreadExecutor();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      scheduler.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(1))).thenReturn(91);
      scheduler.when(() -> J.csr(91)).thenAnswer(invocation -> {
        stopStarted.countDown();
        return null;
      });
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.eq(0), Mockito.eq(0), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            scheduled.set(invocation.getArgument(3));
            return true;
          });

      batcher.start();
      batcher.setBypassRadius(0);
      batcher.enqueue(state, false, false);
      batcher.flushAll();
      Future<?> callback = callbackExecutor.submit(() -> {
        try {
          Assertions.assertTrue(stopStarted.await(2, TimeUnit.SECONDS));
          scheduled.get().run();
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(exception);
        }
      });

      batcher.stop();
      callback.get(2, TimeUnit.SECONDS);

      Mockito.verify(state).update(false, false);
      Assertions.assertEquals(1L, batcher.getFlushedCount());
    } finally {
      callbackExecutor.shutdownNow();
    }
  }

  @Test
  void timedOutStopReportsContextAndRetainsFlightForLateAcknowledgement() {
    UUID worldId = UUID.randomUUID();
    World world = mockWorld(worldId);
    BlockState state = mockState(world, -17, 64, 49);
    PdcWriteBatcher batcher = deferredBatcher();
    AtomicReference<Runnable> scheduled = new AtomicReference<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
        MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      scheduler.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(1))).thenReturn(92);
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.eq(-2), Mockito.eq(3), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            scheduled.set(invocation.getArgument(3));
            return true;
          });

      batcher.start();
      batcher.setBypassRadius(0);
      batcher.enqueue(state, false, false);
      batcher.flushAll();
      batcher.setStopDrainTimeoutMillis(50L);
      long startedNanos = System.nanoTime();

      IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, batcher::stop);

      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
      Assertions.assertTrue(elapsedMillis < 1_000L, "stop exceeded its bounded drain window");
      Assertions.assertTrue(failure.getMessage().contains("timed out after 50ms"));
      Assertions.assertTrue(failure.getMessage().contains("world=" + worldId));
      Assertions.assertTrue(failure.getMessage().contains("chunkX=-2"));
      Assertions.assertTrue(failure.getMessage().contains("chunkZ=3"));
      Assertions.assertTrue(failure.getMessage().contains("pending=0"));
      Assertions.assertTrue(failure.getMessage().contains("inFlight=1"));
      Mockito.verify(state, Mockito.never()).update(Mockito.anyBoolean(), Mockito.anyBoolean());

      scheduled.get().run();

      Mockito.verify(state).update(false, false);
      Assertions.assertEquals(1L, batcher.getFlushedCount());
    }
  }

  @Test
  void lateAcknowledgementAfterRestartCannotRemoveNewSameChunkWrites() {
    UUID worldId = UUID.randomUUID();
    World world = mockWorld(worldId);
    BlockState oldState = mockState(world, 1, 64, 1);
    BlockState newState = mockState(world, 2, 64, 1);
    PdcWriteBatcher batcher = deferredBatcher();
    AtomicInteger scheduleCount = new AtomicInteger();
    AtomicReference<Runnable> oldFlight = new AtomicReference<>();
    AtomicReference<Runnable> newFlight = new AtomicReference<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
        MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      scheduler.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(1))).thenReturn(93, 94);
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.eq(0), Mockito.eq(0), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            Runnable callback = invocation.getArgument(3);
            if (scheduleCount.getAndIncrement() == 0) {
              oldFlight.set(callback);
            } else {
              newFlight.set(callback);
            }
            return true;
          });

      batcher.start();
      batcher.setBypassRadius(0);
      batcher.enqueue(oldState, false, false);
      batcher.flushAll();
      batcher.setStopDrainTimeoutMillis(50L);
      Assertions.assertThrows(IllegalStateException.class, batcher::stop);

      batcher.start();
      batcher.setBypassRadius(0);
      batcher.enqueue(newState, true, true);
      oldFlight.get().run();
      batcher.flushAll();
      Assertions.assertNotNull(newFlight.get());
      newFlight.get().run();

      Mockito.verify(oldState).update(false, false);
      Mockito.verify(newState).update(true, true);
      Assertions.assertEquals(1L, batcher.getDeferredCount());
      Assertions.assertEquals(0L, batcher.getImmediateCount());
      Assertions.assertEquals(2L, batcher.getFlushedCount());
      react.verify(() -> React.reportError(Mockito.any(Throwable.class)), Mockito.never());
    }
  }

  @Test
  void failedUpdatesRetryAndRemainUndrainedUntilDurable() {
    UUID worldId = UUID.randomUUID();
    World world = mockWorld(worldId);
    BlockState state = mockState(world, 1, 64, 1);
    AtomicBoolean succeeds = new AtomicBoolean(false);
    Mockito.when(state.update(false, false)).thenAnswer(ignored -> succeeds.get());
    PdcWriteBatcher batcher = deferredBatcher();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(J::isFoliaThreading).thenReturn(false);
      scheduler.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(1))).thenReturn(95, 96);

      batcher.start();
      batcher.enqueue(state, false, false);
      batcher.flushAll();
      batcher.flushAll();
      batcher.flushAll();

      IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, batcher::stop);
      Assertions.assertTrue(failure.getMessage().contains("failed after 3 attempts"));
      Assertions.assertEquals(0L, batcher.getFlushedCount());

      succeeds.set(true);
      batcher.start();
      batcher.flushAll();
      Assertions.assertDoesNotThrow(batcher::stop);
      Assertions.assertEquals(1L, batcher.getFlushedCount());
      Mockito.verify(state, Mockito.times(4)).update(false, false);
    }
  }

  private static PdcWriteBatcher deferredBatcher() {
    PdcWriteBatcher batcher = new PdcWriteBatcher();
    batcher.setBypassRadius(0);
    return batcher;
  }

  private static World mockWorld(UUID worldId) {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    return world;
  }

  private static BlockState mockState(World world, int x, int y, int z) {
    BlockState state = Mockito.mock(BlockState.class);
    Mockito.when(state.getWorld()).thenReturn(world);
    Mockito.when(state.getX()).thenReturn(x);
    Mockito.when(state.getY()).thenReturn(y);
    Mockito.when(state.getZ()).thenReturn(z);
    Mockito.when(state.update(Mockito.anyBoolean(), Mockito.anyBoolean())).thenReturn(true);
    return state;
  }
}
