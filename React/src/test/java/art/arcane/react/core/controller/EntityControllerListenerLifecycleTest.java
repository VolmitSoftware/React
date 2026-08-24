package art.arcane.react.core.controller;

import art.arcane.chrono.ChronoLatch;
import art.arcane.react.React;
import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.EntityKiller;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

class EntityControllerListenerLifecycleTest {
  @Test
  void unregisterRemovesEveryRegistrationForListenerIdentity() {
    EntityController controller = new EntityController();
    controller.setEntityTickListeners(new ConcurrentHashMap<>());
    controller.setAllEntityTickListeners(new CopyOnWriteArrayList<>());
    Consumer<Entity> listener = entity -> {
    };

    controller.registerEntityTickListener(EntityType.ITEM, listener);
    controller.registerEntityTickListener(EntityType.ZOMBIE, listener);
    controller.registerEntityTickListener(listener);
    controller.unregisterEntityTickListener(listener);

    Assertions.assertTrue(controller.getAllEntityTickListeners().isEmpty());
    Assertions.assertFalse(controller.getEntityTickListeners().containsKey(EntityType.ITEM));
    Assertions.assertFalse(controller.getEntityTickListeners().containsKey(EntityType.ZOMBIE));
  }

  @Test
  void periodicEntityScanKeepsOnlyOnePendingJob() {
    EntityController controller = controllerWithListener();
    ReactConfiguration configuration = Mockito.mock(ReactConfiguration.class);
    EntityPriority priority = Mockito.mock(EntityPriority.class);
    List<Runnable> queued = new ArrayList<>();
    Mockito.when(configuration.getPriority()).thenReturn(priority);

    try (MockedStatic<ReactConfiguration> configurations = Mockito.mockStatic(ReactConfiguration.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      configurations.when(ReactConfiguration::get).thenReturn(configuration);
      captureQueuedJobs(scheduling, queued);
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of());

      controller.onTick();
      controller.onTick();

      Assertions.assertEquals(1, queued.size());
      queued.getFirst().run();
      controller.onTick();
      Assertions.assertEquals(2, queued.size());
    }
  }

  @Test
  void periodicEntityScanReleasesGuardAfterFailure() {
    EntityController controller = controllerWithListener();
    ReactConfiguration configuration = Mockito.mock(ReactConfiguration.class);
    EntityPriority priority = Mockito.mock(EntityPriority.class);
    List<Runnable> queued = new ArrayList<>();
    Mockito.when(configuration.getPriority()).thenReturn(priority);

    try (MockedStatic<ReactConfiguration> configurations = Mockito.mockStatic(ReactConfiguration.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      configurations.when(ReactConfiguration::get).thenReturn(configuration);
      captureQueuedJobs(scheduling, queued);
      bukkit.when(Bukkit::getWorlds).thenThrow(new IllegalStateException("scan failed"));

      controller.onTick();
      Assertions.assertThrows(IllegalStateException.class, queued.getFirst()::run);
      controller.onTick();

      Assertions.assertEquals(2, queued.size());
    }
  }

  @Test
  void delayedFoliaFlightIsSingleAndCannotReachRestartedListeners() {
    EntityController controller = controllerWithListener();
    AtomicInteger oldListenerCalls = new AtomicInteger(0);
    AtomicInteger newListenerCalls = new AtomicInteger(0);
    controller.getAllEntityTickListeners().clear();
    controller.getAllEntityTickListeners().add(entity -> oldListenerCalls.incrementAndGet());
    ReactConfiguration configuration = Mockito.mock(ReactConfiguration.class);
    EntityPriority priority = Mockito.mock(EntityPriority.class);
    Mockito.when(configuration.getPriority()).thenReturn(priority);
    Player first = scanPlayer();
    Player second = scanPlayer();
    Entity sampled = Mockito.mock(Entity.class);
    Mockito.when(sampled.getType()).thenReturn(EntityType.ZOMBIE);
    Mockito.when(first.getNearbyEntities(48, 32, 48)).thenReturn(List.of(sampled));
    Mockito.when(second.getNearbyEntities(48, 32, 48)).thenReturn(List.of(sampled));
    controller.setFoliaPlayers(new Player[]{first, second});
    controller.setFoliaPlayerSnapshotAtMS(System.currentTimeMillis());
    List<Runnable> operations = new ArrayList<>();
    List<Runnable> retired = new ArrayList<>();
    React previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    React.instance = plugin;

    try (MockedStatic<ReactConfiguration> configurations = Mockito.mockStatic(ReactConfiguration.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<EntityKiller> killer = Mockito.mockStatic(EntityKiller.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      configurations.when(ReactConfiguration::get).thenReturn(configuration);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
      scheduling.when(() -> J.runEntity(
          Mockito.any(Player.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        operations.add(invocation.getArgument(1));
        retired.add(invocation.getArgument(3));
        return true;
      });
      killer.when(() -> EntityKiller.stopAll(Mockito.anyLong())).thenReturn(true);
      managed.when(() -> ReactEntity.tick(Mockito.any(Entity.class), Mockito.same(priority))).thenReturn(true);
      managed.when(() -> ReactEntity.awaitManagedCleanup(Mockito.anyLong())).thenReturn(true);

      controller.onTick();
      controller.onTick();
      Assertions.assertEquals(2, operations.size());

      controller.stop();
      controller.start();
      controller.registerEntityTickListener(entity -> newListenerCalls.incrementAndGet());
      controller.setFoliaPlayers(new Player[]{first, second});
      controller.setFoliaPlayerSnapshotAtMS(System.currentTimeMillis());

      operations.subList(0, 2).forEach(Runnable::run);
      retired.getFirst().run();
      retired.getFirst().run();
      Assertions.assertEquals(0, oldListenerCalls.get());
      Assertions.assertEquals(0, newListenerCalls.get());

      controller.onTick();
      controller.onTick();
      Assertions.assertEquals(4, operations.size());
      operations.subList(2, 4).forEach(Runnable::run);
      Assertions.assertEquals(2, newListenerCalls.get());

      controller.onTick();
      Assertions.assertEquals(6, operations.size());
    } finally {
      React.instance = previous;
    }
  }

  private static EntityController controllerWithListener() {
    EntityController controller = new EntityController();
    controller.setEntityTickListeners(new ConcurrentHashMap<>());
    controller.setAllEntityTickListeners(new CopyOnWriteArrayList<>());
    controller.getAllEntityTickListeners().add(entity -> {
    });
    ChronoLatch valueSaver = Mockito.mock(ChronoLatch.class);
    ChronoLatch scratchSweeper = Mockito.mock(ChronoLatch.class);
    Mockito.when(valueSaver.flip()).thenReturn(false);
    Mockito.when(scratchSweeper.flip()).thenReturn(false);
    controller.setValueSaver(valueSaver);
    controller.setScratchSweeper(scratchSweeper);
    return controller;
  }

  private static Player scanPlayer() {
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.isOnline()).thenReturn(true);
    return player;
  }

  private static void captureQueuedJobs(MockedStatic<J> scheduling, List<Runnable> queued) {
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
      queued.add(invocation.getArgument(0));
      return null;
    });
  }
}
