package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTargetEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

class FeatureAdaptiveEntitySleepLifecycleTest {
  static {
    if (React.instance == null) {
      React react = Mockito.mock(React.class);
      Mockito.when(react.getName()).thenReturn("react");
      Mockito.when(react.namespace()).thenReturn("react");
      React.instance = react;
    }
  }

  @Test
  void staleMainScanCannotRunOrClearTheReloadedGenerationQueue() {
    FeatureAdaptiveEntitySleep feature = new FeatureAdaptiveEntitySleep();
    Sampler sampler = Mockito.mock(Sampler.class);
    List<Runnable> queued = new ArrayList<>();
    Mockito.when(sampler.sample()).thenReturn(50D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of());

      feature.onActivate();
      feature.onTick();
      feature.onDeactivate();
      feature.onActivate();
      feature.onTick();

      Assertions.assertEquals(2, queued.size());
      queued.getFirst().run();
      feature.onTick();

      Assertions.assertEquals(2, queued.size());
      managed.verify(() -> ReactEntity.requestPause(
          Mockito.any(Entity.class),
          Mockito.eq(ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP)
      ), Mockito.never());
      managed.verify(() -> ReactEntity.requestDoze(Mockito.any(Mob.class)), Mockito.never());
      feature.onDeactivate();
    }
  }

  @Test
  void staleFoliaAnchorCannotInspectPlayersAfterDeactivation() {
    FeatureAdaptiveEntitySleep feature = new FeatureAdaptiveEntitySleep();
    EntityController controller = Mockito.mock(EntityController.class);
    Sampler sampler = Mockito.mock(Sampler.class);
    Player player = Mockito.mock(Player.class);
    List<Runnable> regionTasks = new ArrayList<>();
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{player});
    Mockito.when(sampler.sample()).thenReturn(50D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      ))
          .thenAnswer(invocation -> {
            regionTasks.add(invocation.getArgument(1));
            return true;
          });

      feature.onActivate();
      feature.onTick();
      Assertions.assertEquals(1, regionTasks.size());
      feature.onDeactivate();
      Mockito.clearInvocations(player);

      regionTasks.getFirst().run();

      Mockito.verifyNoInteractions(player);
      managed.verify(() -> ReactEntity.requestPause(
          Mockito.any(Entity.class),
          Mockito.eq(ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP)
      ), Mockito.never());
      managed.verify(() -> ReactEntity.requestDoze(Mockito.any(Mob.class)), Mockito.never());
    }
  }

  @Test
  void delayedFoliaOwnersSuppressCyclesUntilEveryAnchorRetires() {
    FeatureAdaptiveEntitySleep feature = new FeatureAdaptiveEntitySleep();
    EntityController controller = Mockito.mock(EntityController.class);
    Sampler sampler = Mockito.mock(Sampler.class);
    Player first = Mockito.mock(Player.class);
    Player second = Mockito.mock(Player.class);
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{first, second});
    Mockito.when(sampler.sample()).thenReturn(50D);
    List<Runnable> retired = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(
          Mockito.any(Player.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        retired.add(invocation.getArgument(3));
        return true;
      });

      feature.onActivate();
      feature.onTick();
      feature.onTick();
      Assertions.assertEquals(2, retired.size());

      retired.getFirst().run();
      retired.getFirst().run();
      feature.onTick();
      Assertions.assertEquals(2, retired.size());

      retired.get(1).run();
      feature.onTick();
      Assertions.assertEquals(4, retired.size());
      feature.onDeactivate();
    }
  }

  @Test
  void rejectedAndRetiredAnchorReleasesFlightExactlyOnce() {
    FeatureAdaptiveEntitySleep feature = new FeatureAdaptiveEntitySleep();
    EntityController controller = Mockito.mock(EntityController.class);
    Sampler sampler = Mockito.mock(Sampler.class);
    Player player = Mockito.mock(Player.class);
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{player});
    Mockito.when(sampler.sample()).thenReturn(50D);
    List<Runnable> retired = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        Runnable retirement = invocation.getArgument(3);
        retired.add(retirement);
        retirement.run();
        retirement.run();
        return false;
      });

      feature.onActivate();
      feature.onTick();
      feature.onTick();

      Assertions.assertEquals(2, retired.size());
      feature.onDeactivate();
    }
  }

  @Test
  void staleFoliaEntityHandoffCannotMutateAfterDeactivation() {
    FeatureAdaptiveEntitySleep feature = new FeatureAdaptiveEntitySleep();
    EntityController controller = Mockito.mock(EntityController.class);
    Sampler sampler = Mockito.mock(Sampler.class);
    Player player = Mockito.mock(Player.class);
    Entity entity = Mockito.mock(Entity.class);
    List<Entity> taskEntities = new ArrayList<>();
    List<Runnable> regionTasks = new ArrayList<>();
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{player});
    Mockito.when(sampler.sample()).thenReturn(50D);
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getNearbyEntities(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
        .thenReturn(List.of(entity));

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class)))
          .thenAnswer(invocation -> invocation.getArgument(0) == player);
      scheduling.when(() -> J.runEntity(
          Mockito.any(Entity.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      ))
          .thenAnswer(invocation -> {
            taskEntities.add(invocation.getArgument(0));
            regionTasks.add(invocation.getArgument(1));
            return true;
          });

      feature.onActivate();
      feature.onTick();
      Assertions.assertEquals(1, regionTasks.size());
      Assertions.assertSame(player, taskEntities.getFirst());
      regionTasks.getFirst().run();
      Assertions.assertEquals(2, regionTasks.size());
      Assertions.assertSame(entity, taskEntities.get(1));
      feature.onDeactivate();
      Mockito.clearInvocations(entity);

      regionTasks.get(1).run();

      Mockito.verifyNoInteractions(entity);
      managed.verify(() -> ReactEntity.requestPause(
          Mockito.any(Entity.class),
          Mockito.eq(ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP)
      ), Mockito.never());
      managed.verify(() -> ReactEntity.requestDoze(Mockito.any(Mob.class)), Mockito.never());
    }
  }

  @Test
  void crossRegionTargetWakeRunsOnlyOnTheTargetOwner() {
    FeatureAdaptiveEntitySleep feature = new FeatureAdaptiveEntitySleep();
    Entity source = Mockito.mock(Entity.class);
    Entity target = Mockito.mock(Entity.class);
    EntityTargetEvent event = Mockito.mock(EntityTargetEvent.class);
    List<Runnable> targetTasks = new ArrayList<>();
    Mockito.when(event.getEntity()).thenReturn(source);
    Mockito.when(event.getTarget()).thenReturn(target);
    Mockito.when(source.isDead()).thenReturn(true);
    Mockito.when(target.isDead()).thenReturn(false);
    Mockito.clearInvocations(target);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(source)).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(target)).thenReturn(false);
      scheduling.when(() -> J.runEntity(Mockito.eq(target), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            targetTasks.add(invocation.getArgument(1));
            return true;
          });

      feature.onActivate();
      feature.on(event);

      Assertions.assertEquals(1, targetTasks.size());
      Mockito.verifyNoInteractions(target);
      targetTasks.getFirst().run();

      Mockito.verify(target).isDead();
      managed.verify(() -> ReactEntity.releasePause(
          target,
          ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP
      ));
      feature.onDeactivate();
    }
  }
}
