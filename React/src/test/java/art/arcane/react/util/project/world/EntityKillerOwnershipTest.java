package art.arcane.react.util.project.world;

import art.arcane.react.React;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.google.common.util.concurrent.AtomicDouble;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class EntityKillerOwnershipTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React.instance = null;
    EntityKiller.stopAll();
    EntityKiller.startAccepting();
  }

  @AfterEach
  void tearDown() {
    EntityKiller.stopAll();
    EntityKiller.startAccepting();
    React.instance = previous;
  }

  @Test
  void concurrentConstructionClaimsEachEntityUuidOnce() throws Exception {
    int workers = 16;
    Entity entity = entity(UUID.randomUUID());
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    List<Future<EntityKiller>> futures = new ArrayList<>(workers);

    try {
      for (int worker = 0; worker < workers; worker++) {
        futures.add(executor.submit(() -> {
          ready.countDown();
          await(start);
          return new EntityKiller(entity, 10);
        }));
      }

      Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      for (Future<EntityKiller> future : futures) {
        Assertions.assertNotNull(future.get(5, TimeUnit.SECONDS));
      }
    } finally {
      start.countDown();
      executor.shutdownNow();
      Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    Assertions.assertEquals(1, EntityKiller.activeCount());
  }

  @Test
  void concurrentStopsReleaseOwnershipExactlyOnce() throws Exception {
    int workers = 16;
    EntityKiller killer = new EntityKiller(entity(UUID.randomUUID()), 10);
    Assertions.assertEquals(1, EntityKiller.activeCount());
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    List<Future<?>> futures = new ArrayList<>(workers);

    try {
      for (int worker = 0; worker < workers; worker++) {
        futures.add(executor.submit(() -> {
          ready.countDown();
          await(start);
          killer.stop();
        }));
      }

      Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      for (Future<?> future : futures) {
        future.get(5, TimeUnit.SECONDS);
      }
    } finally {
      start.countDown();
      executor.shutdownNow();
      Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    Assertions.assertEquals(0, EntityKiller.activeCount());
    Assertions.assertEquals(0, EntityKiller.cleanupInFlightCount());
  }

  @Test
  void stopAllWaitsForAcceptedCleanupBeforeCancellingTheRecurringTask() throws Exception {
    Entity entity = entity(UUID.randomUUID());
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(container);
    AtomicReference<Runnable> acceptedCleanup = new AtomicReference<>();
    CountDownLatch stopAllStarted = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(20))).thenReturn(7);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        acceptedCleanup.set(invocation.getArgument(1));
        return true;
      });
      EntityKiller killer = new EntityKiller(entity, 10);
      killer.stop();
      Assertions.assertNotNull(acceptedCleanup.get());

      Future<Boolean> stopped = executor.submit(() -> {
        stopAllStarted.countDown();
        return EntityKiller.stopAll(5_000L);
      });
      Assertions.assertTrue(stopAllStarted.await(5, TimeUnit.SECONDS));
      Assertions.assertFalse(stopped.isDone());
      Assertions.assertEquals(1, EntityKiller.activeCount());
      Assertions.assertEquals(1, EntityKiller.cleanupInFlightCount());
      scheduling.verify(() -> J.csr(7), Mockito.never());

      acceptedCleanup.get().run();

      Assertions.assertTrue(stopped.get(5, TimeUnit.SECONDS));
      Assertions.assertEquals(0, EntityKiller.activeCount());
      Assertions.assertEquals(0, EntityKiller.cleanupInFlightCount());
      scheduling.verify(() -> J.csr(7), Mockito.times(1));
    } finally {
      executor.shutdownNow();
      Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void acceptedRetirementCompletesCleanupExactlyOnce() throws Exception {
    Entity entity = entity(UUID.randomUUID());
    AtomicReference<Runnable> retiredCleanup = new AtomicReference<>();
    CountDownLatch stopAllStarted = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(20))).thenReturn(9);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        retiredCleanup.set(invocation.getArgument(3));
        return true;
      });
      EntityKiller killer = new EntityKiller(entity, 10);
      killer.stop();
      Assertions.assertNotNull(retiredCleanup.get());

      Future<Boolean> stopped = executor.submit(() -> {
        stopAllStarted.countDown();
        return EntityKiller.stopAll(5_000L);
      });
      Assertions.assertTrue(stopAllStarted.await(5, TimeUnit.SECONDS));
      Assertions.assertFalse(stopped.isDone());

      retiredCleanup.get().run();
      retiredCleanup.get().run();

      Assertions.assertTrue(stopped.get(5, TimeUnit.SECONDS));
      Assertions.assertEquals(0, EntityKiller.activeCount());
      Assertions.assertEquals(0, EntityKiller.cleanupInFlightCount());
      scheduling.verify(() -> J.csr(9), Mockito.times(1));
    } finally {
      executor.shutdownNow();
      Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void rejectedCleanupRetiresWithoutTouchingForeignEntityState() {
    Entity entity = entity(UUID.randomUUID());

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(20))).thenReturn(11);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenReturn(false);
      new EntityKiller(entity, 10);

      Assertions.assertTrue(EntityKiller.stopAll(1_000L));

      Mockito.verify(entity, Mockito.never()).setCustomNameVisible(Mockito.anyBoolean());
      Mockito.verify(entity, Mockito.never()).setCustomName(Mockito.any());
      Mockito.verify(entity, Mockito.never()).getPersistentDataContainer();
      Assertions.assertEquals(0, EntityKiller.activeCount());
      Assertions.assertEquals(0, EntityKiller.cleanupInFlightCount());
      scheduling.verify(() -> J.csr(11), Mockito.times(1));
    }
  }

  @Test
  void foliaDeathSoundTouchesPlayersOnlyOnTheirEntityOwners() {
    Entity source = entity(UUID.randomUUID());
    Player player = Mockito.mock(Player.class);
    EntityController controller = Mockito.mock(EntityController.class);
    React.Audiences audiences = Mockito.mock(React.Audiences.class);
    Audience audience = Mockito.mock(Audience.class);
    World world = Mockito.mock(World.class);
    World playerWorld = Mockito.mock(World.class);
    Location location = Mockito.mock(Location.class);
    UUID worldId = UUID.randomUUID();
    AtomicReference<Runnable> playerTask = new AtomicReference<>();
    Mockito.when(location.getWorld()).thenReturn(world);
    Mockito.when(location.getX()).thenReturn(12.5D);
    Mockito.when(location.getY()).thenReturn(64D);
    Mockito.when(location.getZ()).thenReturn(-8.5D);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(playerWorld.getUID()).thenReturn(worldId);
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{player});
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getWorld()).thenReturn(playerWorld);
    Mockito.when(audiences.player(player)).thenReturn(audience);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<FoliaScheduler> folia = Mockito.mockStatic(FoliaScheduler.class)) {
      scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(20))).thenReturn(13);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      react.when(React::audiences).thenReturn(audiences);
      folia.when(() -> FoliaScheduler.runEntity(
          Mockito.isNull(),
          Mockito.eq(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0L),
          Mockito.isNull()
      )).thenAnswer(invocation -> {
        playerTask.set(invocation.getArgument(2));
        return true;
      });
      EntityKiller killer = new EntityKiller(source, 10);

      killer.playDeathSound(location);

      Mockito.verify(player, Mockito.never()).isOnline();
      Mockito.verify(player, Mockito.never()).getWorld();
      Assertions.assertNotNull(playerTask.get());
      playerTask.get().run();
      Mockito.verify(audience).playSound(Mockito.any(), Mockito.eq(12.5D), Mockito.eq(64D), Mockito.eq(-8.5D));
    }
  }

  @Test
  void stopAllBlocksNewClaimsUntilTheControllerRestarts() {
    Entity entity = entity(UUID.randomUUID());
    new EntityKiller(entity, 10);

    EntityKiller.stopAll();
    new EntityKiller(entity, 10);

    Assertions.assertEquals(0, EntityKiller.activeCount());

    EntityKiller.startAccepting();
    new EntityKiller(entity, 10);

    Assertions.assertEquals(1, EntityKiller.activeCount());
  }

  @Test
  void duplicateClaimCannotUseTheKillPath() {
    Entity entity = entity(UUID.randomUUID());

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(20))).thenReturn(7);
      new EntityKiller(entity, 10);
      EntityKiller duplicate = new EntityKiller(entity, 10);

      duplicate.kill();

      scheduling.verify(() -> J.runEntity(
          Mockito.eq(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      ), Mockito.never());
      Assertions.assertEquals(1, EntityKiller.activeCount());
    }
  }

  @Test
  void killerRemovalIsCountedOnlyByTheEntityRemoveEvent() {
    Entity entity = entity(UUID.randomUUID());
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    SamplerEntities sampler = Mockito.spy(new SamplerEntities());
    World world = Mockito.mock(World.class);
    Location location = Mockito.mock(Location.class);
    Chunk chunk = Mockito.mock(Chunk.class);
    AtomicDouble chunkCount = new AtomicDouble(0D);
    EntitySpawnEvent spawnEvent = Mockito.mock(EntitySpawnEvent.class);
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(container);
    Mockito.when(entity.getWorld()).thenReturn(world);
    Mockito.when(entity.getLocation()).thenReturn(location);
    Mockito.when(spawnEvent.getEntity()).thenReturn(entity);
    Mockito.when(spawnEvent.getLocation()).thenReturn(location);
    Mockito.when(location.getChunk()).thenReturn(chunk);
    Mockito.when(chunk.getWorld()).thenReturn(world);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(world.getPlayers()).thenReturn(List.of());
    Mockito.doReturn(chunkCount).when(sampler).getChunkCounter(chunk);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.sampler(SamplerEntities.ID)).thenReturn(sampler);
      scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(20))).thenReturn(7);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        Runnable task = invocation.getArgument(1);
        task.run();
        return true;
      });
      sampler.start();
      try {
        sampler.on(spawnEvent);
        EntityKiller killer = new EntityKiller(entity, 10);

        killer.kill();
        sampler.on(new EntityRemoveEvent(entity, EntityRemoveEvent.Cause.PLUGIN));
      } finally {
        sampler.stop();
      }
    }

    Mockito.verify(entity).remove();
    Assertions.assertEquals(0, sampler.getEntities().get());
    Assertions.assertEquals(0D, chunkCount.get());
  }

  @Test
  void stopReleasesOwnershipWhenEntityCleanupFails() {
    Entity entity = entity(UUID.randomUUID());
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    IllegalStateException failure = new IllegalStateException("entity cleanup failed");
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(container);
    Mockito.doThrow(failure).when(container).remove(Mockito.any(NamespacedKey.class));

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(20))).thenReturn(7);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        Runnable task = invocation.getArgument(1);
        task.run();
        return true;
      });
      EntityKiller killer = new EntityKiller(entity, 10);

      IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, killer::stop);

      Assertions.assertSame(failure, thrown);
      Assertions.assertEquals(0, EntityKiller.activeCount());
    }
  }

  @Test
  void delayedTickDoesNotRestoreCountdownAfterStop() {
    Entity entity = entity(UUID.randomUUID());
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(container);
    AtomicReference<Runnable> recurringTask = new AtomicReference<>();
    AtomicReference<Runnable> delayedOwnedTask = new AtomicReference<>();
    AtomicInteger entityRuns = new AtomicInteger();

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(20))).thenAnswer(invocation -> {
        recurringTask.set(invocation.getArgument(0));
        return 7;
      });
      scheduling.when(() -> J.runEntity(
          Mockito.eq(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        if (entityRuns.getAndIncrement() == 0) {
          delayedOwnedTask.set(invocation.getArgument(1));
          return true;
        }

        Runnable rejected = invocation.getArgument(3);
        rejected.run();
        return false;
      });
      EntityKiller killer = new EntityKiller(entity, 10);
      recurringTask.get().run();

      killer.stop();
      Mockito.clearInvocations(entity);
      delayedOwnedTask.get().run();

      Mockito.verifyNoInteractions(entity);
      Assertions.assertEquals(0, EntityKiller.activeCount());
    }
  }

  @Test
  void delayedKillDoesNotRemoveEntityAfterStop() {
    Entity entity = entity(UUID.randomUUID());
    AtomicReference<Runnable> delayedOwnedTask = new AtomicReference<>();
    AtomicInteger entityRuns = new AtomicInteger();

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(20))).thenReturn(7);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        if (entityRuns.getAndIncrement() == 0) {
          delayedOwnedTask.set(invocation.getArgument(1));
          return true;
        }

        Runnable rejected = invocation.getArgument(3);
        rejected.run();
        return false;
      });
      EntityKiller killer = new EntityKiller(entity, 10);
      killer.kill();

      killer.stop();
      Mockito.clearInvocations(entity);
      delayedOwnedTask.get().run();

      Mockito.verifyNoInteractions(entity);
      Assertions.assertEquals(0, EntityKiller.activeCount());
    }
  }

  private Entity entity(UUID id) {
    Entity entity = Mockito.mock(Entity.class);
    Mockito.when(entity.getUniqueId()).thenReturn(id);
    return entity;
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
