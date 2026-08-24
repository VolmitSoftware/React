package art.arcane.react.core.controller;

import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticked;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class NearbyPlayerIndexControllerTest {
  @Test
  void stationaryThousandPlayerPopulationRemainsIndexedWithoutTickerPolling() {
    World world = world();
    NearbyPlayerIndexController controller = new NearbyPlayerIndexController();
    Location target = null;

    try (MockedStatic<Bukkit> bukkit = emptyOnlinePlayers()) {
      controller.start();
      for (int index = 0; index < 1000; index++) {
        Location location = new Location(world, (index % 32) * 32D, 64D, (index / 32) * 32D);
        controller.injectSynthetic(new UUID(0L, index + 1L), location);
        if (index == 777) {
          target = location;
        }
      }

      Assertions.assertFalse(Ticked.class.isAssignableFrom(NearbyPlayerIndexController.class));
      Assertions.assertNotNull(target);
      Assertions.assertTrue(controller.hasNearbyPlayer(target, 1D));
      bukkit.verify(() -> Bukkit.getPlayer(Mockito.any(UUID.class)), Mockito.never());
    } finally {
      controller.stop();
    }
  }

  @Test
  void quitEventRemovesSnapshotWithoutPollingBukkit() {
    World world = world();
    NearbyPlayerIndexController controller = new NearbyPlayerIndexController();
    UUID playerId = UUID.randomUUID();
    Location location = new Location(world, 10D, 64D, 10D);
    Player player = Mockito.mock(Player.class);
    PlayerQuitEvent event = Mockito.mock(PlayerQuitEvent.class);
    Mockito.when(player.getUniqueId()).thenReturn(playerId);
    Mockito.when(event.getPlayer()).thenReturn(player);

    try (MockedStatic<Bukkit> bukkit = emptyOnlinePlayers()) {
      controller.start();
      controller.injectSynthetic(playerId, location);
      Assertions.assertTrue(controller.hasNearbyPlayer(location, 1D));

      controller.on(event);

      Assertions.assertFalse(controller.hasNearbyPlayer(location, 1D));
      bukkit.verify(() -> Bukkit.getPlayer(Mockito.any(UUID.class)), Mockito.never());
    } finally {
      controller.stop();
    }
  }

  @Test
  void nearestDistanceQueryUsesSnapshotCoordinatesAcrossAThousandPlayers() {
    World world = world();
    NearbyPlayerIndexController controller = new NearbyPlayerIndexController();
    Location query = new Location(world, 10D, 64D, -8D);

    try (MockedStatic<Bukkit> bukkit = emptyOnlinePlayers()) {
      controller.start();
      for (int index = 0; index < 999; index++) {
        controller.injectSynthetic(
            new UUID(0L, index + 1L),
            new Location(world, 1_000D + index, 64D, 1_000D)
        );
      }
      controller.injectSynthetic(new UUID(1L, 1L), new Location(world, 13D, 67D, -4D));

      Assertions.assertEquals(34D, controller.nearestDistanceSquared(query, 64D));
      Assertions.assertEquals(
          Double.POSITIVE_INFINITY,
          controller.nearestDistanceSquared(query, 5D)
      );
      Assertions.assertTrue(controller.hasNearbyPlayerInColumn(world, 10D, -8D, 5D));
      Assertions.assertFalse(controller.hasNearbyPlayerInColumn(world, 10D, -8D, 4D));
      bukkit.verify(() -> Bukkit.getPlayer(Mockito.any(UUID.class)), Mockito.never());
    } finally {
      controller.stop();
    }
  }

  @Test
  void concurrentSnapshotUpdatesNeverPublishATornNearbyPosition() throws Exception {
    World world = world();
    NearbyPlayerIndexController controller = new NearbyPlayerIndexController();

    try (MockedStatic<Bukkit> bukkit = emptyOnlinePlayers()) {
      controller.start();
      assertConcurrentSnapshotResult(
          controller,
          new Location(world, 0D, 15D, 0D),
          new Location(world, 15D, 0D, 0D),
          new Location(world, 0D, 0D, 0D),
          14D,
          false
      );
      bukkit.verify(() -> Bukkit.getPlayer(Mockito.any(UUID.class)), Mockito.never());
    } finally {
      controller.stop();
    }
  }

  @Test
  void concurrentSnapshotUpdatesNeverLoseANearbyPlayer() throws Exception {
    World world = world();
    NearbyPlayerIndexController controller = new NearbyPlayerIndexController();

    try (MockedStatic<Bukkit> bukkit = emptyOnlinePlayers()) {
      controller.start();
      assertConcurrentSnapshotResult(
          controller,
          new Location(world, 0D, 8D, 0D),
          new Location(world, 8D, 0D, 0D),
          new Location(world, 0D, 0D, 0D),
          9D,
          true
      );
      bukkit.verify(() -> Bukkit.getPlayer(Mockito.any(UUID.class)), Mockito.never());
    } finally {
      controller.stop();
    }
  }

  @Test
  void crossChunkMovementNeverDisappearsFromSmallCellQueriesAtThousandPlayerScale() throws Exception {
    World world = world();
    NearbyPlayerIndexController controller = new NearbyPlayerIndexController();

    try (MockedStatic<Bukkit> bukkit = emptyOnlinePlayers()) {
      controller.start();
      for (int index = 0; index < 1_000; index++) {
        controller.injectSynthetic(
            new UUID(4L, index + 1L),
            new Location(world, 10_000D + index, 64D, 10_000D)
        );
      }
      assertConcurrentSnapshotResult(
          controller,
          new Location(world, 15D, 64D, 0D),
          new Location(world, 17D, 64D, 0D),
          new Location(world, 16D, 64D, 0D),
          2D,
          true
      );
      bukkit.verify(() -> Bukkit.getPlayer(Mockito.any(UUID.class)), Mockito.never());
    } finally {
      controller.stop();
    }
  }

  @Test
  void movementSnapshotsObserveOnlyTheFinalUncancelledDestination() throws NoSuchMethodException {
    assertFinalMovementHandler(PlayerMoveEvent.class);
    assertFinalMovementHandler(PlayerTeleportEvent.class);
  }

  @Test
  void impactSnapshotQueryUsesWorldIdentityWithoutBukkitResolution() {
    World world = world();
    NearbyPlayerIndexController controller = new NearbyPlayerIndexController();
    UUID playerId = UUID.randomUUID();
    Location from = new Location(world, 0D, 64D, 0D);
    Location to = new Location(world, 8D, 65D, 9D);
    Player player = Mockito.mock(Player.class);
    PlayerMoveEvent event = Mockito.mock(PlayerMoveEvent.class);
    Mockito.when(player.getUniqueId()).thenReturn(playerId);
    Mockito.when(player.getName()).thenReturn("Alex");
    Mockito.when(player.getVelocity()).thenReturn(new Vector(1D, 2D, 2D));
    Mockito.when(player.isGliding()).thenReturn(true);
    Mockito.when(player.getVehicle()).thenReturn(Mockito.mock(Entity.class));
    Mockito.when(event.getPlayer()).thenReturn(player);
    Mockito.when(event.getFrom()).thenReturn(from);
    Mockito.when(event.getTo()).thenReturn(to);

    try (MockedStatic<Bukkit> bukkit = emptyOnlinePlayers()) {
      controller.start();
      controller.on(event);
      Mockito.reset(player);

      List<NearbyPlayerIndexController.PlayerViewSnapshot> snapshots =
          controller.playerSnapshotsInColumn(world.getUID(), 8D, 9D, 2D);

      Assertions.assertEquals(1, snapshots.size());
      NearbyPlayerIndexController.PlayerViewSnapshot snapshot = snapshots.getFirst();
      Assertions.assertEquals(playerId, snapshot.playerId());
      Assertions.assertEquals("Alex", snapshot.name());
      Assertions.assertEquals(8D, snapshot.x());
      Assertions.assertEquals(65D, snapshot.y());
      Assertions.assertEquals(9D, snapshot.z());
      Assertions.assertEquals(3D, snapshot.speed());
      Assertions.assertTrue(snapshot.gliding());
      Assertions.assertTrue(snapshot.mounted());
      Mockito.verifyNoInteractions(player);
      bukkit.verify(() -> Bukkit.getWorld(Mockito.any(UUID.class)), Mockito.never());
    } finally {
      controller.stop();
    }
  }

  @Test
  void wideImpactQueryScansSnapshotsInsteadOfEveryCoordinateCell() {
    World world = world();
    NearbyPlayerIndexController controller = new NearbyPlayerIndexController();
    UUID playerId = UUID.randomUUID();

    try (MockedStatic<Bukkit> bukkit = emptyOnlinePlayers()) {
      controller.start();
      controller.injectSynthetic(playerId, new Location(world, 8D, 64D, 9D));
      Map<Long, Set<UUID>> indexedBuckets = controller.getPlayersByWorldChunk().get(world.getUID());
      Map<Long, Set<UUID>> observedBuckets = Mockito.spy(new HashMap<>(indexedBuckets));
      controller.getPlayersByWorldChunk().put(world.getUID(), observedBuckets);

      List<NearbyPlayerIndexController.PlayerViewSnapshot> snapshots =
          controller.playerSnapshotsInColumn(world, 0D, 0D, 2_560D);

      Assertions.assertEquals(1, snapshots.size());
      Mockito.verify(observedBuckets, Mockito.never()).get(Mockito.any());
    } finally {
      controller.stop();
    }
  }

  @Test
  void cancelledWorldUnloadRetainsIndexedPlayers() throws NoSuchMethodException {
    World world = world();
    NearbyPlayerIndexController controller = new NearbyPlayerIndexController();
    UUID playerId = UUID.randomUUID();
    Location location = new Location(world, 8D, 64D, 9D);
    WorldUnloadEvent event = Mockito.mock(WorldUnloadEvent.class);
    Mockito.when(event.getWorld()).thenReturn(world);
    Mockito.when(event.isCancelled()).thenReturn(true);

    try (MockedStatic<Bukkit> bukkit = emptyOnlinePlayers()) {
      controller.start();
      controller.injectSynthetic(playerId, location);

      controller.on(event);

      Assertions.assertTrue(controller.hasNearbyPlayer(location, 1D));
      EventHandler handler = NearbyPlayerIndexController.class
          .getMethod("on", WorldUnloadEvent.class)
          .getAnnotation(EventHandler.class);
      Assertions.assertNotNull(handler);
      Assertions.assertEquals(EventPriority.MONITOR, handler.priority());
      Assertions.assertTrue(handler.ignoreCancelled());
    } finally {
      controller.stop();
    }
  }

  @Test
  void thousandPlayerFoliaReloadFailsClosedUntilEveryInitialOwnerTaskCompletes() {
    World world = world();
    List<Player> players = new ArrayList<>(1_000);
    List<Runnable> updates = new ArrayList<>(1_000);
    for (int index = 0; index < 1_000; index++) {
      Player player = Mockito.mock(Player.class);
      Mockito.when(player.getUniqueId()).thenReturn(new UUID(0L, index + 1L));
      Mockito.when(player.getName()).thenReturn("P" + index);
      Mockito.when(player.getLocation()).thenReturn(new Location(world, index, 64D, index));
      Mockito.when(player.getVelocity()).thenReturn(new Vector());
      players.add(player);
    }

    NearbyPlayerIndexController controller = new NearbyPlayerIndexController();
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Player.class))).thenReturn(false);
      scheduling.when(() -> J.runEntity(
          Mockito.any(Player.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        updates.add(invocation.getArgument(1));
        return true;
      });

      controller.start();

      Assertions.assertFalse(controller.isInitialSeedReady());
      Assertions.assertTrue(controller.hasNearbyPlayerInColumn(world, 0D, 0D, 32D));
      Assertions.assertEquals(1_000, updates.size());
      for (Runnable update : updates) {
        update.run();
      }
      Assertions.assertTrue(controller.isInitialSeedReady());
      Assertions.assertTrue(controller.hasNearbyPlayerInColumn(world, 0D, 0D, 32D));
    } finally {
      controller.stop();
    }
  }

  @Test
  void rejectedOrRetiredInitialOwnerTaskRetriesWithoutPlayerMovement() {
    assertFailedInitialSeed(false);
    assertFailedInitialSeed(true);
  }

  private void assertFinalMovementHandler(Class<?> eventType) throws NoSuchMethodException {
    EventHandler handler = NearbyPlayerIndexController.class.getMethod("on", eventType).getAnnotation(EventHandler.class);
    Assertions.assertNotNull(handler);
    Assertions.assertEquals(EventPriority.MONITOR, handler.priority());
    Assertions.assertTrue(handler.ignoreCancelled());
  }

  private void assertFailedInitialSeed(boolean acceptedThenRetired) {
    World world = world();
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(player.getName()).thenReturn("Stationary");
    Mockito.when(player.getLocation()).thenReturn(new Location(world, 8D, 64D, 8D));
    Mockito.when(player.getVelocity()).thenReturn(new Vector());
    NearbyPlayerIndexController controller = new NearbyPlayerIndexController();
    List<Runnable> retries = new ArrayList<>();
    List<Runnable> updates = new ArrayList<>();
    AtomicInteger ownerAttempts = new AtomicInteger();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(false);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        if (ownerAttempts.getAndIncrement() == 0) {
          if (!acceptedThenRetired) {
            return false;
          }
          Runnable retired = invocation.getArgument(3);
          retired.run();
          return true;
        }
        updates.add(invocation.getArgument(1));
        return true;
      });
      scheduling.when(() -> J.a(Mockito.any(Runnable.class), Mockito.eq(1)))
          .thenAnswer(invocation -> {
            retries.add(invocation.getArgument(0));
            return null;
          });

      controller.start();

      Assertions.assertFalse(controller.isInitialSeedReady());
      Assertions.assertTrue(controller.hasNearbyPlayerInColumn(world, 0D, 0D, 32D));
      Assertions.assertEquals(1, retries.size());

      retries.remove(0).run();
      Assertions.assertEquals(1, updates.size());
      updates.remove(0).run();

      Assertions.assertTrue(controller.isInitialSeedReady());
      Assertions.assertTrue(controller.hasNearbyPlayerInColumn(world, 8D, 8D, 1D));
    } finally {
      controller.stop();
    }
  }

  private void assertConcurrentSnapshotResult(
      NearbyPlayerIndexController controller,
      Location first,
      Location second,
      Location query,
      double blocks,
      boolean expected
  ) throws Exception {
    UUID playerId = UUID.randomUUID();
    int readers = 3;
    int iterations = 100_000;
    CountDownLatch ready = new CountDownLatch(readers + 1);
    CountDownLatch start = new CountDownLatch(1);
    AtomicBoolean mismatch = new AtomicBoolean(false);
    ExecutorService executor = Executors.newFixedThreadPool(readers + 1);
    List<Future<?>> futures = new ArrayList<>(readers + 1);
    controller.injectSynthetic(playerId, first);

    try {
      futures.add(executor.submit(() -> {
        awaitStart(ready, start);
        for (int iteration = 0; iteration < iterations; iteration++) {
          controller.injectSynthetic(playerId, (iteration & 1) == 0 ? first : second);
        }
      }));
      for (int reader = 0; reader < readers; reader++) {
        futures.add(executor.submit(() -> {
          awaitStart(ready, start);
          for (int iteration = 0; iteration < iterations; iteration++) {
            if (controller.hasNearbyPlayer(query, blocks) != expected) {
              mismatch.set(true);
              return;
            }
          }
        }));
      }

      Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    Assertions.assertFalse(mismatch.get());
  }

  private void awaitStart(CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      if (!start.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for concurrent snapshot test start");
      }
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for concurrent snapshot test start", failure);
    }
  }

  private MockedStatic<Bukkit> emptyOnlinePlayers() {
    MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
    return bukkit;
  }

  private World world() {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    return world;
  }
}
