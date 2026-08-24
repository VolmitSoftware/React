package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactProtection;
import art.arcane.react.api.protect.internal.ProtectionGuards;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTargetEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class FoliaFeatureScanLifecycleTest {
  static {
    if (React.instance == null) {
      React react = Mockito.mock(React.class);
      Mockito.when(react.getName()).thenReturn("react");
      Mockito.when(react.namespace()).thenReturn("react");
      React.instance = react;
    }
  }

  @Test
  void dynamicRangeUsesCapturedRotatingAnchorsAndWaitsForEveryRegionTask() throws Exception {
    FeatureDynamicActivationRange feature = new FeatureDynamicActivationRange();
    setInt(feature, "maxEntitiesSampledPerCycle", 16);
    feature.onActivate();

    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.sample()).thenReturn(40D);
    EntityController controller = Mockito.mock(EntityController.class);
    Player[] players = players(5);
    Mockito.when(controller.getFoliaPlayers()).thenReturn(players);
    List<ScheduledTask> tasks = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(
              Mockito.any(Entity.class),
              Mockito.any(Runnable.class),
              Mockito.eq(0),
              Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            tasks.add(new ScheduledTask(invocation.getArgument(0), invocation.getArgument(1)));
            return true;
          });

      feature.onTick();
      feature.onTick();

      Assertions.assertEquals(2, tasks.size());
      Assertions.assertSame(players[0], tasks.get(0).entity);
      Assertions.assertSame(players[1], tasks.get(1).entity);
      bukkit.verify(Bukkit::getOnlinePlayers, Mockito.never());

      tasks.get(0).work.run();
      feature.onTick();
      Assertions.assertEquals(2, tasks.size());

      tasks.get(1).work.run();
      feature.onTick();
      Assertions.assertEquals(4, tasks.size());
      Assertions.assertSame(players[2], tasks.get(2).entity);
      Assertions.assertSame(players[3], tasks.get(3).entity);
    }
  }

  @Test
  void dynamicRangeCompletesEveryDispatchOutcomeExactlyOnce() throws Exception {
    FeatureDynamicActivationRange feature = new FeatureDynamicActivationRange();
    setInt(feature, "maxEntitiesSampledPerCycle", 32);
    feature.onActivate();

    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.sample()).thenReturn(40D);
    EntityController controller = Mockito.mock(EntityController.class);
    Player[] players = players(4);
    Mockito.when(controller.getFoliaPlayers()).thenReturn(players);
    AtomicInteger attempts = new AtomicInteger();
    AtomicReference<Runnable> acceptedRetired = new AtomicReference<>();
    AtomicReference<Runnable> acceptedRetiredWork = new AtomicReference<>();
    AtomicReference<Runnable> acceptedWork = new AtomicReference<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(
              Mockito.any(Entity.class),
              Mockito.any(Runnable.class),
              Mockito.eq(0),
              Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            int attempt = attempts.getAndIncrement();
            Runnable work = invocation.getArgument(1);
            Runnable retired = invocation.getArgument(3);
            if (attempt == 0) {
              acceptedRetiredWork.set(work);
              acceptedRetired.set(retired);
              return true;
            }
            if (attempt == 1) {
              retired.run();
              return false;
            }
            if (attempt == 2) {
              throw new IllegalStateException("scheduler failed");
            }
            if (attempt == 3) {
              acceptedWork.set(work);
              return true;
            }
            return false;
          });

      feature.onTick();
      feature.onTick();
      Assertions.assertEquals(4, attempts.get());

      acceptedRetired.get().run();
      acceptedRetired.get().run();
      acceptedRetiredWork.get().run();
      feature.onTick();
      Assertions.assertEquals(4, attempts.get());

      acceptedWork.get().run();
      acceptedWork.get().run();
      feature.onTick();
      Assertions.assertEquals(8, attempts.get());
      feature.onDeactivate();
    }
  }

  @Test
  void dynamicRangeRejectsCallbacksFromAnOlderActivation() throws Exception {
    FeatureDynamicActivationRange feature = new FeatureDynamicActivationRange();
    setInt(feature, "maxEntitiesSampledPerCycle", 1);
    feature.onActivate();

    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.sample()).thenReturn(40D);
    EntityController controller = Mockito.mock(EntityController.class);
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{player});
    List<Runnable> tasks = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(
              Mockito.any(Entity.class),
              Mockito.any(Runnable.class),
              Mockito.eq(0),
              Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(1));
            return true;
          });

      feature.onTick();
      feature.onActivate();
      feature.onTick();
      Assertions.assertEquals(2, tasks.size());

      tasks.get(0).run();
      Mockito.verify(player, Mockito.never()).isOnline();
      feature.onTick();
      Assertions.assertEquals(2, tasks.size());

      Mockito.when(player.isOnline()).thenReturn(false);
      tasks.get(1).run();
      feature.onTick();
      Assertions.assertEquals(3, tasks.size());
    }
  }

  @Test
  void dynamicRangeCrossRegionTargetWakeRunsOnlyOnTheTargetOwner() {
    FeatureDynamicActivationRange feature = new FeatureDynamicActivationRange();
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
          ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE
      ));
      feature.onDeactivate();
    }
  }

  @Test
  void dynamicRangeReadsCandidateIdentityOnlyAfterEntityOwnerHandoff() throws Exception {
    FeatureDynamicActivationRange feature = new FeatureDynamicActivationRange();
    setInt(feature, "maxEntitiesSampledPerCycle", 1);
    Sampler sampler = Mockito.mock(Sampler.class);
    EntityController controller = Mockito.mock(EntityController.class);
    Player player = Mockito.mock(Player.class);
    Entity candidate = Mockito.mock(Entity.class);
    UUID candidateId = UUID.randomUUID();
    AtomicBoolean candidateOwned = new AtomicBoolean(false);
    List<ScheduledTask> tasks = new ArrayList<>();
    Mockito.when(sampler.sample()).thenReturn(40D);
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{player});
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getNearbyEntities(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
        .thenReturn(List.of(candidate));
    Mockito.when(candidate.getUniqueId()).thenReturn(candidateId);
    Mockito.when(candidate.isDead()).thenReturn(false);
    Mockito.clearInvocations(candidate);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class)))
          .thenAnswer(invocation -> invocation.getArgument(0) == player || candidateOwned.get());
      scheduling.when(() -> J.runEntity(
              Mockito.any(Entity.class),
              Mockito.any(Runnable.class),
              Mockito.eq(0),
              Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            tasks.add(new ScheduledTask(invocation.getArgument(0), invocation.getArgument(1)));
            return true;
          });

      feature.onActivate();
      feature.onTick();
      Assertions.assertEquals(1, tasks.size());
      tasks.getFirst().work.run();

      Assertions.assertEquals(2, tasks.size());
      Assertions.assertSame(candidate, tasks.get(1).entity);
      Mockito.verify(candidate, Mockito.never()).getUniqueId();
      Mockito.verify(candidate, Mockito.never()).isDead();

      candidateOwned.set(true);
      tasks.get(1).work.run();

      Mockito.verify(candidate).getUniqueId();
      Mockito.verify(candidate).isDead();
      managed.verify(() -> ReactEntity.releasePause(
          candidate,
          ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE
      ));
      feature.onDeactivate();
    }
  }

  @Test
  void entityTrimmerUsesCapturedRotatingAnchorsAndWaitsForEveryRegionTask() throws Exception {
    FeatureEntityTrimmer feature = new FeatureEntityTrimmer();
    setInt(feature, "softMaxEntitiesPerPlayer", 1);
    setInt(feature, "softMaxEntitiesPerWorld", 4);

    EntityController controller = Mockito.mock(EntityController.class);
    Player[] players = players(30);
    Mockito.when(controller.getFoliaPlayers()).thenReturn(players);
    List<ScheduledTask> tasks = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(
              Mockito.any(Entity.class),
              Mockito.any(Runnable.class),
              Mockito.anyInt(),
              Mockito.nullable(Runnable.class)))
          .thenAnswer(invocation -> {
            tasks.add(new ScheduledTask(invocation.getArgument(0), invocation.getArgument(1)));
            return true;
          });

      feature.onTick();
      feature.onTick();

      Assertions.assertEquals(24, tasks.size());
      Assertions.assertSame(players[0], tasks.get(0).entity);
      Assertions.assertSame(players[1], tasks.get(1).entity);
      bukkit.verify(Bukkit::getOnlinePlayers, Mockito.never());

      for (int i = 0; i < 23; i++) {
        tasks.get(i).work.run();
      }
      feature.onTick();
      Assertions.assertEquals(24, tasks.size());

      tasks.get(23).work.run();
      feature.onTick();
      Assertions.assertEquals(48, tasks.size());
      Assertions.assertSame(players[24], tasks.get(24).entity);
      Assertions.assertSame(players[25], tasks.get(25).entity);
    }
  }

  @Test
  void entityTrimmerPaperQueuesOneBoundedMainThreadScanWithoutEnumeratingWorlds() {
    FeatureEntityTrimmer feature = new FeatureEntityTrimmer();
    Player[] players = players(30);
    List<Runnable> mainTasks = new ArrayList<>();

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        mainTasks.add(invocation.getArgument(0));
        return null;
      });
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Arrays.asList(players));

      feature.onTick();
      Assertions.assertEquals(1, mainTasks.size());
      mainTasks.getFirst().run();
      feature.onTick();

      Assertions.assertEquals(2, mainTasks.size());
      bukkit.verify(Bukkit::getWorlds, Mockito.never());
    }
  }

  @Test
  void entityTrimmerBoundsUniqueCandidatesAndRejectsDelayedKillsAfterDeactivation() throws Exception {
    FeatureEntityTrimmer feature = new FeatureEntityTrimmer();
    setInt(feature, "softMaxEntitiesPerPlayer", 1);
    setInt(feature, "softMaxEntitiesPerWorld", 4);
    setInt(feature, "minKillBatchSize", 1);
    setDouble(feature, "opporunityThreshold", 0.25D);
    setDouble(feature, "maxPriority", 100D);

    EntityController controller = Mockito.mock(EntityController.class);
    Player player = Mockito.mock(Player.class);
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Location center = new Location(world, 0D, 64D, 0D);
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getLocation()).thenReturn(center);
    Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getEntityCount()).thenReturn(8);
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{player});
    List<Entity> entities = entities(8);
    for (int i = 0; i < entities.size(); i++) {
      Mockito.when(entities.get(i).getLocation()).thenReturn(new Location(world, i, 64D, 0D));
    }
    Mockito.when(player.getNearbyEntities(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
        .thenReturn(entities);
    List<Runnable> playerTasks = new ArrayList<>();
    List<Runnable> delayedKills = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<ReactEntity> entityState = Mockito.mockStatic(ReactEntity.class);
         MockedStatic<ReactProtection> protection = Mockito.mockStatic(ReactProtection.class);
         MockedStatic<ProtectionGuards> guards = Mockito.mockStatic(ProtectionGuards.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
      scheduling.when(() -> J.runEntity(
              Mockito.any(Entity.class),
              Mockito.any(Runnable.class),
              Mockito.anyInt(),
              Mockito.nullable(Runnable.class)))
          .thenAnswer(invocation -> {
            Entity entity = invocation.getArgument(0);
            Runnable work = invocation.getArgument(1);
            if (entity == player) {
              playerTasks.add(work);
            } else {
              delayedKills.add(work);
            }
            return true;
          });
      entityState.when(() -> ReactEntity.getPriority(Mockito.any(Entity.class))).thenReturn(0D);
      protection.when(() -> ReactProtection.isProtected(Mockito.any(Entity.class), Mockito.eq(ReactOperation.TRIM)))
          .thenReturn(false);
      guards.when(() -> ProtectionGuards.allows(Mockito.any(Entity.class), Mockito.eq(ReactOperation.TRIM)))
          .thenReturn(true);

      feature.onTick();
      Assertions.assertEquals(1, playerTasks.size());
      playerTasks.getFirst().run();

      for (int i = 0; i < entities.size(); i++) {
        Entity entity = entities.get(i);
        entityState.verify(() -> ReactEntity.getPriority(entity), Mockito.times(1));
      }
      Assertions.assertEquals(1, delayedKills.size());

      feature.onTick();
      Assertions.assertEquals(1, playerTasks.size());
      feature.onDeactivate();
      delayedKills.getFirst().run();
      react.verify(() -> React.kill(Mockito.any(Entity.class)), Mockito.never());
    }
  }

  @Test
  void entityTrimmerAppliesWorldChunkPlayerAndMinimumBatchCaps() throws Exception {
    Assertions.assertEquals(4, runEntityTrimmerCycle(-1, -1, 4, 1, 1D));
    Assertions.assertEquals(5, runEntityTrimmerCycle(3, -1, -1, 1, 1D));
    Assertions.assertEquals(3, runEntityTrimmerCycle(-1, 5, -1, 1, 1D));
    Assertions.assertEquals(0, runEntityTrimmerCycle(-1, 1, -1, 2, 0.25D));
  }

  private static int runEntityTrimmerCycle(
      int chunkCap,
      int playerCap,
      int worldCap,
      int minimumBatch,
      double opportunity
  ) throws Exception {
    FeatureEntityTrimmer feature = new FeatureEntityTrimmer();
    setInt(feature, "softMaxEntitiesPerChunk", chunkCap);
    setInt(feature, "softMaxEntitiesPerPlayer", playerCap);
    setInt(feature, "softMaxEntitiesPerWorld", worldCap);
    setInt(feature, "minKillBatchSize", minimumBatch);
    setDouble(feature, "opporunityThreshold", opportunity);
    setDouble(feature, "maxPriority", 100D);

    EntityController controller = Mockito.mock(EntityController.class);
    Player player = Mockito.mock(Player.class);
    World world = Mockito.mock(World.class);
    Chunk chunk = Mockito.mock(Chunk.class);
    Location center = new Location(world, 0D, 64D, 0D);
    List<Entity> entities = entities(8);
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getLocation()).thenReturn(center);
    Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(world.getEntityCount()).thenReturn(entities.size());
    Mockito.when(chunk.getEntities()).thenReturn(entities.toArray(Entity[]::new));
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{player});
    for (int i = 0; i < entities.size(); i++) {
      Entity entity = entities.get(i);
      Mockito.when(entity.getLocation()).thenReturn(new Location(world, i, 64D, 0D));
      Mockito.when(entity.getChunk()).thenReturn(chunk);
    }
    Mockito.when(player.getNearbyEntities(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
        .thenReturn(entities);

    List<Runnable> playerTasks = new ArrayList<>();
    List<Runnable> delayedKills = new ArrayList<>();
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<ReactEntity> entityState = Mockito.mockStatic(ReactEntity.class);
         MockedStatic<ReactProtection> protection = Mockito.mockStatic(ReactProtection.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
      scheduling.when(() -> J.runEntity(
              Mockito.any(Entity.class),
              Mockito.any(Runnable.class),
              Mockito.anyInt(),
              Mockito.nullable(Runnable.class)))
          .thenAnswer(invocation -> {
            Entity entity = invocation.getArgument(0);
            Runnable work = invocation.getArgument(1);
            if (entity == player) {
              playerTasks.add(work);
            } else {
              delayedKills.add(work);
            }
            return true;
          });
      entityState.when(() -> ReactEntity.getPriority(Mockito.any(Entity.class))).thenReturn(0D);
      protection.when(() -> ReactProtection.isProtected(Mockito.any(Entity.class), Mockito.eq(ReactOperation.TRIM)))
          .thenReturn(false);

      feature.onTick();
      Assertions.assertEquals(1, playerTasks.size());
      playerTasks.getFirst().run();
      return delayedKills.size();
    }
  }

  private static Player[] players(int count) {
    Player[] players = new Player[count];
    for (int i = 0; i < count; i++) {
      Player player = Mockito.mock(Player.class);
      Mockito.when(player.isOnline()).thenReturn(false);
      players[i] = player;
    }
    return players;
  }

  private static List<Entity> entities(int count) {
    List<Entity> entities = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      LivingEntity entity = Mockito.mock(LivingEntity.class);
      Mockito.when(entity.isDead()).thenReturn(false);
      Mockito.when(entity.getTicksLived()).thenReturn(500);
      Mockito.when(entity.getType()).thenReturn(EntityType.ZOMBIE);
      Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
      entities.add(entity);
    }
    return entities;
  }

  private static void setInt(Object target, String name, int value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.setInt(target, value);
  }

  private static void setDouble(Object target, String name, double value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.setDouble(target, value);
  }

  private record ScheduledTask(Entity entity, Runnable work) {
  }
}
