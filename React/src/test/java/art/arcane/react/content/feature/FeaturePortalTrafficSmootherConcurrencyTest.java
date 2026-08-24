package art.arcane.react.content.feature;

import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class FeaturePortalTrafficSmootherConcurrencyTest {
  @Test
  void concurrentSameDestinationTrafficUsesOneAtomicWindow() throws InterruptedException {
    FeaturePortalTrafficSmoother.PortalWindow window = new FeaturePortalTrafficSmoother.PortalWindow(1_000L);
    int attempts = 64;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(attempts);
    AtomicInteger throttled = new AtomicInteger();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    ExecutorService executor = Executors.newFixedThreadPool(16);

    for (int i = 0; i < attempts; i++) {
      executor.execute(() -> {
        try {
          start.await();
          if (window.record(true, 31, 100, 5_000, 1_000, 1_000L)) {
            throttled.incrementAndGet();
          }
        } catch (Throwable throwable) {
          failure.compareAndSet(null, throwable);
        } finally {
          finished.countDown();
        }
      });
    }

    start.countDown();
    boolean completed = finished.await(5, TimeUnit.SECONDS);
    executor.shutdownNow();

    Assertions.assertTrue(completed);
    Assertions.assertNull(failure.get());
    Assertions.assertEquals(33, throttled.get());
  }

  @Test
  void concurrentDelayClaimsNeverExceedTheGlobalCapacity() throws InterruptedException {
    FeaturePortalTrafficSmoother.DelayedRegistry registry =
        new FeaturePortalTrafficSmoother.DelayedRegistry(true);
    int attempts = 64;
    int capacity = 8;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(attempts);
    Map<UUID, FeaturePortalTrafficSmoother.DelayedClaim> accepted = new ConcurrentHashMap<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    ExecutorService executor = Executors.newFixedThreadPool(16);

    for (int i = 0; i < attempts; i++) {
      executor.execute(() -> {
        try {
          UUID id = UUID.randomUUID();
          start.await();
          FeaturePortalTrafficSmoother.DelayedClaim claim =
              registry.claim(id, 1L, 1_000L, 11_000L, capacity);
          if (claim != null) {
            accepted.put(id, claim);
          }
        } catch (Throwable throwable) {
          failure.compareAndSet(null, throwable);
        } finally {
          finished.countDown();
        }
      });
    }

    start.countDown();
    boolean completed = finished.await(5, TimeUnit.SECONDS);
    executor.shutdownNow();

    Assertions.assertTrue(completed);
    Assertions.assertNull(failure.get());
    Assertions.assertEquals(capacity, accepted.size());
    Assertions.assertEquals(capacity, registry.size());

    AtomicInteger releases = new AtomicInteger();
    for (Map.Entry<UUID, FeaturePortalTrafficSmoother.DelayedClaim> entry : accepted.entrySet()) {
      if (registry.release(entry.getKey(), entry.getValue())) {
        releases.incrementAndGet();
      }
      if (registry.release(entry.getKey(), entry.getValue())) {
        releases.incrementAndGet();
      }
    }

    Assertions.assertEquals(capacity, releases.get());
    Assertions.assertEquals(0, registry.size());
  }

  @Test
  void expiredClaimCannotReleaseItsReplacement() {
    FeaturePortalTrafficSmoother.DelayedRegistry registry =
        new FeaturePortalTrafficSmoother.DelayedRegistry(true);
    UUID id = UUID.randomUUID();
    FeaturePortalTrafficSmoother.DelayedClaim expired = registry.claim(id, 1L, 1_000L, 2_000L, 1);
    FeaturePortalTrafficSmoother.DelayedClaim replacement = registry.claim(id, 1L, 2_000L, 12_000L, 1);

    Assertions.assertNotNull(expired);
    Assertions.assertNotNull(replacement);
    Assertions.assertFalse(registry.release(id, expired));
    Assertions.assertEquals(1, registry.size());
    Assertions.assertTrue(registry.release(id, replacement));
    Assertions.assertEquals(0, registry.size());
  }

  @Test
  void deactivationBeforeDelayedCallbackPreventsTeleport() throws ReflectiveOperationException {
    FeaturePortalTrafficSmoother feature = new FeaturePortalTrafficSmoother();
    setBoolean(feature, "onlyDuringPressure", false);
    setBoolean(feature, "bypassNearPlayers", false);
    setInt(feature, "maxPlayerPortalsPerChunkWindow", 0);
    feature.onActivate();

    World world = Mockito.mock(World.class);
    Player player = Mockito.mock(Player.class);
    PlayerPortalEvent event = Mockito.mock(PlayerPortalEvent.class);
    Location destination = new Location(world, 8, 64, 8);
    AtomicReference<Runnable> callback = new AtomicReference<>();
    AtomicReference<Runnable> retired = new AtomicReference<>();
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(event.getPlayer()).thenReturn(player);
    Mockito.when(event.getTo()).thenReturn(destination);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(
          Mockito.same(player),
          Mockito.any(Runnable.class),
          Mockito.eq(2),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        callback.set(invocation.getArgument(1));
        retired.set(invocation.getArgument(3));
        return true;
      });

      feature.on(event);
      Mockito.verify(event).setCancelled(true);
      Assertions.assertNotNull(callback.get());
      Assertions.assertNotNull(retired.get());

      feature.onDeactivate();
      callback.get().run();
      retired.get().run();

      Mockito.verify(player, Mockito.never()).isOnline();
      Mockito.verify(player, Mockito.never()).isDead();
      Mockito.verify(player, Mockito.never()).teleport(
          Mockito.any(Location.class),
          Mockito.any(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.class)
      );
      Mockito.verify(player, Mockito.never()).teleportAsync(
          Mockito.any(Location.class),
          Mockito.any(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.class)
      );
    }
  }

  @Test
  void foliaCrossWorldTraversalUsesTeleportAsyncAndHoldsCapacityUntilCompletion()
      throws ReflectiveOperationException {
    FeaturePortalTrafficSmoother feature = new FeaturePortalTrafficSmoother();
    setBoolean(feature, "onlyDuringPressure", false);
    setBoolean(feature, "bypassNearPlayers", false);
    setInt(feature, "maxPlayerPortalsPerChunkWindow", 0);
    setInt(feature, "maxQueuedDelays", 1);
    feature.onActivate();

    World destinationWorld = Mockito.mock(World.class);
    Mockito.when(destinationWorld.getUID()).thenReturn(UUID.randomUUID());
    Location destination = new Location(destinationWorld, 600, 72, -900);
    Player first = Mockito.mock(Player.class);
    Player second = Mockito.mock(Player.class);
    Mockito.when(first.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(second.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(first.isOnline()).thenReturn(true);
    Mockito.when(first.isDead()).thenReturn(false);
    CompletableFuture<Boolean> traversal = new CompletableFuture<>();
    Mockito.when(first.teleportAsync(
        Mockito.any(Location.class),
        Mockito.eq(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN)
    )).thenReturn(traversal);
    PlayerPortalEvent firstEvent = Mockito.mock(PlayerPortalEvent.class);
    PlayerPortalEvent secondEvent = Mockito.mock(PlayerPortalEvent.class);
    Mockito.when(firstEvent.getPlayer()).thenReturn(first);
    Mockito.when(firstEvent.getTo()).thenReturn(destination);
    Mockito.when(secondEvent.getPlayer()).thenReturn(second);
    Mockito.when(secondEvent.getTo()).thenReturn(destination);
    List<Runnable> callbacks = new ArrayList<>();
    List<Runnable> retired = new ArrayList<>();

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(
          Mockito.any(Player.class),
          Mockito.any(Runnable.class),
          Mockito.eq(2),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        callbacks.add(invocation.getArgument(1));
        retired.add(invocation.getArgument(3));
        return true;
      });

      feature.on(firstEvent);
      Assertions.assertEquals(1, callbacks.size());
      Mockito.verify(firstEvent).setCancelled(true);
      callbacks.getFirst().run();

      Mockito.verify(first).teleportAsync(
          Mockito.any(Location.class),
          Mockito.eq(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN)
      );
      Mockito.verify(first, Mockito.never()).teleport(
          Mockito.any(Location.class),
          Mockito.any(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.class)
      );

      feature.on(secondEvent);
      Assertions.assertEquals(1, callbacks.size());
      Mockito.verify(secondEvent, Mockito.never()).setCancelled(true);

      traversal.complete(true);
      feature.on(secondEvent);
      Assertions.assertEquals(2, callbacks.size());
      Mockito.verify(secondEvent).setCancelled(true);

      retired.get(1).run();
      feature.onDeactivate();
    }
  }

  @Test
  void duplicateEntityClaimLeavesRejectedPortalUncancelled() throws ReflectiveOperationException {
    FeaturePortalTrafficSmoother feature = new FeaturePortalTrafficSmoother();
    setBoolean(feature, "onlyDuringPressure", false);
    setBoolean(feature, "bypassNearPlayers", false);
    setInt(feature, "maxEntityPortalsPerChunkWindow", 0);
    setInt(feature, "maxQueuedDelays", 1);
    feature.onActivate();

    World world = Mockito.mock(World.class);
    Entity entity = Mockito.mock(Entity.class);
    EntityPortalEvent acceptedEvent = Mockito.mock(EntityPortalEvent.class);
    EntityPortalEvent duplicateEvent = Mockito.mock(EntityPortalEvent.class);
    Location destination = new Location(world, 8, 64, 8);
    AtomicReference<Runnable> retired = new AtomicReference<>();
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(acceptedEvent.getEntity()).thenReturn(entity);
    Mockito.when(acceptedEvent.getTo()).thenReturn(destination);
    Mockito.when(duplicateEvent.getEntity()).thenReturn(entity);
    Mockito.when(duplicateEvent.getTo()).thenReturn(destination);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(
          Mockito.same(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(4),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        retired.set(invocation.getArgument(3));
        return true;
      });

      feature.on(acceptedEvent);
      feature.on(duplicateEvent);

      Mockito.verify(acceptedEvent).setCancelled(true);
      Mockito.verify(duplicateEvent, Mockito.never()).setCancelled(true);
      scheduling.verify(() -> J.runEntity(
          Mockito.same(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(4),
          Mockito.any(Runnable.class)
      ), Mockito.times(1));
      Assertions.assertNotNull(retired.get());
      retired.get().run();
      feature.onDeactivate();
    }
  }

  @Test
  void zeroDelayCapacityLeavesThrottledPlayerPortalUncancelled()
      throws ReflectiveOperationException {
    FeaturePortalTrafficSmoother feature = new FeaturePortalTrafficSmoother();
    setBoolean(feature, "onlyDuringPressure", false);
    setBoolean(feature, "bypassNearPlayers", false);
    setInt(feature, "maxPlayerPortalsPerChunkWindow", 0);
    setInt(feature, "maxQueuedDelays", 0);
    feature.onActivate();

    World world = Mockito.mock(World.class);
    Player player = Mockito.mock(Player.class);
    PlayerPortalEvent event = Mockito.mock(PlayerPortalEvent.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(event.getPlayer()).thenReturn(player);
    Mockito.when(event.getTo()).thenReturn(new Location(world, 8, 64, 8));

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      feature.on(event);

      Mockito.verify(event, Mockito.never()).setCancelled(true);
      scheduling.verify(() -> J.runEntity(
          Mockito.any(Entity.class),
          Mockito.any(Runnable.class),
          Mockito.anyInt(),
          Mockito.any(Runnable.class)
      ), Mockito.never());
      feature.onDeactivate();
    }
  }

  @Test
  void retiredTaskReleasesCapacityAndRejectsItsStaleCallback() throws ReflectiveOperationException {
    FeaturePortalTrafficSmoother feature = new FeaturePortalTrafficSmoother();
    setBoolean(feature, "onlyDuringPressure", false);
    setBoolean(feature, "bypassNearPlayers", false);
    setInt(feature, "maxPlayerPortalsPerChunkWindow", 0);
    setInt(feature, "maxQueuedDelays", 1);
    feature.onActivate();

    World world = Mockito.mock(World.class);
    Player first = Mockito.mock(Player.class);
    Player second = Mockito.mock(Player.class);
    PlayerPortalEvent firstEvent = Mockito.mock(PlayerPortalEvent.class);
    PlayerPortalEvent secondEvent = Mockito.mock(PlayerPortalEvent.class);
    Location destination = new Location(world, 8, 64, 8);
    List<Runnable> callbacks = new ArrayList<>();
    List<Runnable> retired = new ArrayList<>();
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(first.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(second.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(firstEvent.getPlayer()).thenReturn(first);
    Mockito.when(firstEvent.getTo()).thenReturn(destination);
    Mockito.when(secondEvent.getPlayer()).thenReturn(second);
    Mockito.when(secondEvent.getTo()).thenReturn(destination);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(
          Mockito.any(Player.class),
          Mockito.any(Runnable.class),
          Mockito.eq(2),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        callbacks.add(invocation.getArgument(1));
        retired.add(invocation.getArgument(3));
        return true;
      });

      feature.on(firstEvent);
      Assertions.assertEquals(1, callbacks.size());
      retired.getFirst().run();

      feature.on(secondEvent);
      Assertions.assertEquals(2, callbacks.size());
      callbacks.getFirst().run();

      Mockito.verify(first, Mockito.never()).isOnline();
      Mockito.verify(first, Mockito.never()).teleport(
          Mockito.any(Location.class),
          Mockito.any(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.class)
      );
      retired.get(1).run();
      feature.onDeactivate();
    }
  }

  private static void setBoolean(Object target, String fieldName, boolean value)
      throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.setBoolean(target, value);
  }

  private static void setInt(Object target, String fieldName, int value)
      throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.setInt(target, value);
  }
}
