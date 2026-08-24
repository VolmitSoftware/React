package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.protect.internal.ProtectionGuards;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.EntityKiller;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import art.arcane.volmlib.util.scheduling.Looper;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

class EntityControllerManagedStateLifecycleTest {
  private static React previous;

  @BeforeAll
  static void setUpPlugin() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    React.instance = plugin;
  }

  @AfterAll
  static void restorePlugin() {
    React.instance = previous;
  }

  @Test
  void loadedEntitiesReconcileProtectionCountdownAndManagedPauseState() {
    Entity entity = Mockito.mock(Entity.class);
    EntitiesLoadEvent event = Mockito.mock(EntitiesLoadEvent.class);
    Mockito.when(event.getEntities()).thenReturn(List.of(entity));

    try (MockedStatic<ProtectionGuards> protection = Mockito.mockStatic(ProtectionGuards.class);
         MockedStatic<EntityKiller> killers = Mockito.mockStatic(EntityKiller.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      new EntityController().on(event);

      protection.verify(() -> ProtectionGuards.hydrate(entity));
      killers.verify(() -> EntityKiller.reconcile(entity));
      managed.verify(() -> ReactEntity.reconcileManagedState(entity));
    }
  }

  @Test
  void stopReleasesKillerAndManagedEntityOwnership() {
    EntityController controller = new EntityController();

    try (MockedStatic<EntityKiller> killers = Mockito.mockStatic(EntityKiller.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      killers.when(() -> EntityKiller.stopAll(Mockito.anyLong())).thenReturn(true);
      managed.when(() -> ReactEntity.awaitManagedCleanup(Mockito.anyLong())).thenReturn(true);

      controller.stop();

      killers.verify(() -> EntityKiller.stopAll(30_000L));
      managed.verify(ReactEntity::releaseAllManagedState);
      managed.verify(() -> ReactEntity.awaitManagedCleanup(30_000L));
      managed.verify(ReactEntity::clearScratch);
    }
  }

  @Test
  void stopPropagatesManagedCleanupFailureAfterClearingScratch() {
    EntityController controller = new EntityController();

    try (MockedStatic<EntityKiller> killers = Mockito.mockStatic(EntityKiller.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      killers.when(() -> EntityKiller.stopAll(Mockito.anyLong())).thenReturn(true);
      managed.when(() -> ReactEntity.awaitManagedCleanup(Mockito.anyLong())).thenReturn(false);

      IllegalStateException failure = Assertions.assertThrows(
          IllegalStateException.class,
          controller::stop
      );

      Assertions.assertTrue(failure.getMessage().contains("did not drain"));
      killers.verify(() -> EntityKiller.stopAll(30_000L));
      managed.verify(ReactEntity::releaseAllManagedState);
      managed.verify(ReactEntity::clearScratch);
    }
  }

  @Test
  void stopPropagatesKillerCleanupFailureAfterManagedCleanup() {
    EntityController controller = new EntityController();

    try (MockedStatic<EntityKiller> killers = Mockito.mockStatic(EntityKiller.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      killers.when(() -> EntityKiller.stopAll(Mockito.anyLong())).thenReturn(false);
      managed.when(() -> ReactEntity.awaitManagedCleanup(Mockito.anyLong())).thenReturn(true);

      IllegalStateException failure = Assertions.assertThrows(
          IllegalStateException.class,
          controller::stop
      );

      Assertions.assertTrue(failure.getMessage().contains("did not drain"));
      killers.verify(() -> EntityKiller.stopAll(30_000L));
      managed.verify(ReactEntity::releaseAllManagedState);
      managed.verify(() -> ReactEntity.awaitManagedCleanup(30_000L));
      managed.verify(ReactEntity::clearScratch);
    }
  }

  @Test
  void nonFoliaPostStartDoesNotScheduleAFullLoadedEntityReconciliation() {
    EntityController controller = new EntityController();
    Looper looper = Mockito.mock(Looper.class);
    controller.setLooper(looper);
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });

      controller.postStart();

      Mockito.verify(looper).start();
      Assertions.assertTrue(queued.isEmpty());
    }
  }

  @Test
  void foliaJoinReconcilesThePlayerAndNearbyEntitiesOnOwnedSchedulers() {
    EntityController controller = new EntityController();
    Player player = Mockito.mock(Player.class);
    Entity nearby = Mockito.mock(Entity.class);
    PlayerJoinEvent event = Mockito.mock(PlayerJoinEvent.class);
    Mockito.when(event.getPlayer()).thenReturn(player);
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getNearbyEntities(48, 32, 48)).thenReturn(List.of(nearby));

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<EntityKiller> killers = Mockito.mockStatic(EntityKiller.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
      scheduling.when(() -> J.runEntity(Mockito.eq(player), Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        Runnable task = invocation.getArgument(1);
        task.run();
        return true;
      });

      controller.on(event);

      killers.verify(() -> EntityKiller.reconcile(player));
      killers.verify(() -> EntityKiller.reconcile(nearby));
      managed.verify(() -> ReactEntity.reconcileManagedState(player));
      managed.verify(() -> ReactEntity.reconcileManagedState(nearby));
    }
  }
}
