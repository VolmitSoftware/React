package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.protect.ReactProtection;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class MonolithicJobFanoutTest {
  static {
    if (React.instance == null) {
      React react = Mockito.mock(React.class);
      Mockito.when(react.getName()).thenReturn("react");
      Mockito.when(react.namespace()).thenReturn("react");
      React.instance = react;
    }
  }

  @Test
  void adaptiveSleepQueuesOneJobPerSampledEntityAndHoldsTheScanUntilTheyFinish() {
    World world = Mockito.mock(World.class);
    List<Entity> entities = List.of(Mockito.mock(Entity.class), Mockito.mock(Entity.class), Mockito.mock(Entity.class));
    List<Runnable> jobs = new ArrayList<>();
    FeatureAdaptiveEntitySleep feature = new FeatureAdaptiveEntitySleep();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<WorldEntitySnapshots> snapshots = Mockito.mockStatic(WorldEntitySnapshots.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        jobs.add(invocation.getArgument(0));
        return null;
      });
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      snapshots.when(() -> WorldEntitySnapshots.next(Mockito.eq(world), Mockito.anyInt())).thenReturn(entities);
      feature.onActivate();

      feature.onTick();
      Assertions.assertEquals(1, jobs.size());
      jobs.removeFirst().run();

      Assertions.assertEquals(3, jobs.size());
      for (Entity entity : entities) {
        Mockito.verifyNoInteractions(entity);
      }
      feature.onTick();
      Assertions.assertEquals(3, jobs.size());

      for (Runnable job : new ArrayList<>(jobs)) {
        job.run();
      }
      jobs.clear();
      for (Entity entity : entities) {
        Mockito.verify(entity).isDead();
      }
      feature.onTick();
      Assertions.assertEquals(1, jobs.size());
      feature.onDeactivate();
    }
  }

  @Test
  void pathfinderBudgetQueuesOneJobPerSampledEntityAndHoldsTheScanUntilTheyFinish() throws ReflectiveOperationException {
    World world = Mockito.mock(World.class);
    List<Entity> entities = List.of(Mockito.mock(Entity.class), Mockito.mock(Entity.class));
    List<Runnable> jobs = new ArrayList<>();
    FeaturePathfinderBudget feature = new FeaturePathfinderBudget();
    set(feature, "nativeAccess", Mockito.mock(NativeWorldAccess.class));
    set(feature, "bridgesAvailable", true);
    set(feature, "active", true);
    ((AtomicLong) get(feature, "lifecycleGeneration")).set(1L);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<WorldEntitySnapshots> snapshots = Mockito.mockStatic(WorldEntitySnapshots.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        jobs.add(invocation.getArgument(0));
        return null;
      });
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      snapshots.when(() -> WorldEntitySnapshots.next(Mockito.eq(world), Mockito.anyInt())).thenReturn(entities);

      feature.onTick();
      Assertions.assertEquals(1, jobs.size());
      jobs.removeFirst().run();

      Assertions.assertEquals(2, jobs.size());
      for (Entity entity : entities) {
        Mockito.verifyNoInteractions(entity);
      }
      feature.onTick();
      Assertions.assertEquals(2, jobs.size());

      for (Runnable job : new ArrayList<>(jobs)) {
        job.run();
      }
      jobs.clear();
      feature.onTick();
      Assertions.assertEquals(1, jobs.size());
    }
  }

  @Test
  void entityTrimmerQueuesOneScanJobPerDistinctAnchorRegion() throws ReflectiveOperationException {
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(world.getUID()).thenReturn(worldId);
    Player first = player(world, 0D, 0D);
    Player sameRegion = player(world, 6D, 5D);
    Player farAway = player(world, 400D, -300D);
    NearbyPlayerIndexController index = snapshotIndex(worldId, first, sameRegion, farAway);
    List<Runnable> jobs = new ArrayList<>();
    FeatureEntityTrimmer feature = new FeatureEntityTrimmer();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(index);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        jobs.add(invocation.getArgument(0));
        return null;
      });
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(first, sameRegion, farAway));

      feature.onTick();
      Assertions.assertEquals(1, jobs.size());
      jobs.removeFirst().run();

      Assertions.assertEquals(2, jobs.size());
      Mockito.verify(first, Mockito.never()).getNearbyEntities(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble());
      for (Runnable job : new ArrayList<>(jobs)) {
        job.run();
      }
    }

    Mockito.verify(first).getNearbyEntities(32D, 32D, 32D);
    Mockito.verify(sameRegion, Mockito.never()).getNearbyEntities(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble());
    Mockito.verify(farAway).getNearbyEntities(32D, 32D, 32D);
  }

  @Test
  void entityTrimmerReadsEachNearbyEntityLocationOnce() throws ReflectiveOperationException {
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(world.getUID()).thenReturn(worldId);
    Player anchor = player(world, 0D, 0D);
    AtomicInteger locationReads = new AtomicInteger();
    List<Entity> nearby = new ArrayList<>();
    for (int index = 0; index < 20; index++) {
      Entity entity = Mockito.mock(Entity.class);
      Location location = new Location(world, index, 64D, 0D);
      Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
      Mockito.when(entity.getType()).thenReturn(EntityType.ZOMBIE);
      Mockito.when(entity.getTicksLived()).thenReturn(1_000);
      Mockito.when(entity.getLocation()).thenAnswer(invocation -> {
        locationReads.incrementAndGet();
        return location;
      });
      nearby.add(entity);
    }
    Mockito.when(anchor.getNearbyEntities(32D, 32D, 32D)).thenReturn(nearby);
    NearbyPlayerIndexController index = snapshotIndex(worldId, anchor);
    List<Runnable> jobs = new ArrayList<>();
    FeatureEntityTrimmer feature = new FeatureEntityTrimmer();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<ReactProtection> protection = Mockito.mockStatic(ReactProtection.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(index);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        jobs.add(invocation.getArgument(0));
        return null;
      });
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(anchor));

      feature.onTick();
      while (!jobs.isEmpty()) {
        jobs.removeFirst().run();
      }
    }

    Assertions.assertEquals(nearby.size(), locationReads.get());
  }

  private static Player player(World world, double x, double z) {
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getLocation()).thenReturn(new Location(world, x, 64D, z));
    Mockito.when(player.getNearbyEntities(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
        .thenReturn(List.of());
    return player;
  }

  private static NearbyPlayerIndexController snapshotIndex(UUID worldId, Player... players) {
    NearbyPlayerIndexController index = Mockito.mock(NearbyPlayerIndexController.class);
    for (Player player : players) {
      Location location = player.getLocation();
      UUID playerId = player.getUniqueId();
      Optional<NearbyPlayerIndexController.PlayerViewSnapshot> snapshot = Optional.of(
          new NearbyPlayerIndexController.PlayerViewSnapshot(
              playerId,
              "player",
              worldId,
              location.getX(),
              location.getY(),
              location.getZ(),
              0D,
              false,
              false
          )
      );
      Mockito.when(index.playerSnapshot(playerId)).thenReturn(snapshot);
    }
    Mockito.clearInvocations((Object[]) players);
    return index;
  }

  private static void set(Object owner, String name, Object value) throws ReflectiveOperationException {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(owner, value);
  }

  private static Object get(Object owner, String name) throws ReflectiveOperationException {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }
}
