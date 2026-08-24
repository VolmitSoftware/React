package art.arcane.react.content.tweak;

import art.arcane.react.React;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

class TweakVehicleIdleBrakeLifecycleTest {
  @Test
  void foliaAnchorCountIsBoundedByPlayersAndBudget() {
    Assertions.assertEquals(0, TweakVehicleIdleBrake.foliaAnchorCount(0, 180));
    Assertions.assertEquals(0, TweakVehicleIdleBrake.foliaAnchorCount(1000, 0));
    Assertions.assertEquals(1, TweakVehicleIdleBrake.foliaAnchorCount(1000, 12));
    Assertions.assertEquals(15, TweakVehicleIdleBrake.foliaAnchorCount(1000, 180));
    Assertions.assertEquals(3, TweakVehicleIdleBrake.foliaAnchorCount(3, 180));
    Assertions.assertEquals(1, TweakVehicleIdleBrake.vehicleBudget(Integer.MIN_VALUE));
    Assertions.assertEquals(4096, TweakVehicleIdleBrake.vehicleBudget(Integer.MAX_VALUE));
  }

  @Test
  void foliaCycleUsesAFixedTotalVehicleBudget() throws ReflectiveOperationException {
    TweakVehicleIdleBrake tweak = new TweakVehicleIdleBrake();
    setInt(tweak, "maxVehiclesSampledPerWorld", 24);
    EntityController controller = Mockito.mock(EntityController.class);
    Player firstPlayer = playerWithNearbyVehicles(30);
    Player secondPlayer = playerWithNearbyVehicles(30);
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{firstPlayer, secondPlayer});
    List<ScheduledTask> scheduled = new ArrayList<>();
    AtomicInteger brakes = new AtomicInteger(0);
    countBrakes(firstPlayer, brakes);
    countBrakes(secondPlayer, brakes);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
      captureEntityTasks(scheduling, scheduled);

      tweak.onActivate();
      tweak.onTick();

      Assertions.assertEquals(2, scheduled.size());
      for (ScheduledTask task : scheduled) {
        task.runnable().run();
      }
      Assertions.assertEquals(24, brakes.get());
    }
  }

  @Test
  void foliaAnchorsRotateWithoutDuplicatesInsideACycle() throws ReflectiveOperationException {
    TweakVehicleIdleBrake tweak = new TweakVehicleIdleBrake();
    setInt(tweak, "maxVehiclesSampledPerWorld", 24);
    EntityController controller = Mockito.mock(EntityController.class);
    Player first = emptyPlayer();
    Player second = emptyPlayer();
    Player third = emptyPlayer();
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{first, second, third});
    List<ScheduledTask> scheduled = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
      captureEntityTasks(scheduling, scheduled);

      tweak.onActivate();
      tweak.onTick();
      Assertions.assertEquals(List.of(first, second), scheduled.stream().map(ScheduledTask::player).toList());
      List<ScheduledTask> firstCycle = List.copyOf(scheduled);
      scheduled.clear();
      firstCycle.forEach(task -> task.runnable().run());

      tweak.onTick();
      Assertions.assertEquals(List.of(third, first), scheduled.stream().map(ScheduledTask::player).toList());
    }
  }

  @Test
  void delayedVehicleCallbackStopsAfterRestart() {
    TweakVehicleIdleBrake tweak = new TweakVehicleIdleBrake();
    EntityController controller = Mockito.mock(EntityController.class);
    Player player = emptyPlayer();
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{player});
    List<ScheduledTask> scheduled = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
      captureEntityTasks(scheduling, scheduled);

      tweak.onActivate();
      tweak.onTick();
      Assertions.assertEquals(1, scheduled.size());

      tweak.onDeactivate();
      tweak.onActivate();
      scheduled.getFirst().runnable().run();

      Mockito.verify(player, Mockito.never()).getNearbyEntities(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble());
    }
  }

  @Test
  void nonFoliaIndexScanHasAHardEnumerationBound() throws ReflectiveOperationException {
    TweakVehicleIdleBrake tweak = new TweakVehicleIdleBrake();
    setInt(tweak, "maxVehiclesSampledPerWorld", 5);
    EntityController controller = Mockito.mock(EntityController.class);
    AtomicInteger brakes = new AtomicInteger(0);
    AtomicInteger inspections = new AtomicInteger(0);
    List<Minecart> vehicles = vehicles(1000, brakes, inspections);
    AtomicReference<Consumer<Entity>> indexer = new AtomicReference<>();
    List<Runnable> queued = new ArrayList<>();
    Mockito.doAnswer(invocation -> {
      indexer.set(invocation.getArgument(0));
      return null;
    }).when(controller).registerEntityTickListener(Mockito.<Consumer<Entity>>any());

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });

      tweak.onActivate();
      Assertions.assertNotNull(indexer.get());
      for (Minecart vehicle : vehicles) {
        indexer.get().accept(vehicle);
      }
      tweak.onTick();
      Assertions.assertEquals(1, queued.size());
      queued.getFirst().run();

      Assertions.assertEquals(5, inspections.get());
      Assertions.assertEquals(5, brakes.get());
    }
  }

  private static Player emptyPlayer() {
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getNearbyEntities(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
        .thenReturn(List.of());
    return player;
  }

  private static Player playerWithNearbyVehicles(int count) {
    Player player = Mockito.mock(Player.class);
    List<Entity> nearby = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      nearby.add(vehicle(null));
    }
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getNearbyEntities(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
        .thenReturn(nearby);
    return player;
  }

  private static void countBrakes(Player player, AtomicInteger brakes) {
    for (Entity entity : player.getNearbyEntities(0D, 0D, 0D)) {
      Mockito.doAnswer(invocation -> {
        brakes.incrementAndGet();
        return null;
      }).when(entity).setVelocity(Mockito.any(Vector.class));
    }
  }

  private static List<Minecart> vehicles(int count, AtomicInteger brakes, AtomicInteger inspections) {
    List<Minecart> vehicles = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Minecart vehicle = vehicle(brakes);
      Mockito.when(vehicle.getPassengers()).thenAnswer(invocation -> {
        inspections.incrementAndGet();
        return List.of();
      });
      vehicles.add(vehicle);
    }
    return vehicles;
  }

  private static Minecart vehicle(AtomicInteger brakes) {
    Minecart vehicle = Mockito.mock(Minecart.class);
    Mockito.when(vehicle.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(vehicle.getPassengers()).thenReturn(List.of());
    Mockito.when(vehicle.getVelocity()).thenReturn(new Vector(1D, 0D, 0D));
    if (brakes != null) {
      Mockito.doAnswer(invocation -> {
        brakes.incrementAndGet();
        return null;
      }).when(vehicle).setVelocity(Mockito.any(Vector.class));
    }
    return vehicle;
  }

  private static void captureEntityTasks(MockedStatic<J> scheduling, List<ScheduledTask> scheduled) {
    scheduling.when(() -> J.runEntity(
            Mockito.any(Entity.class),
            Mockito.any(Runnable.class),
            Mockito.eq(0),
            Mockito.any(Runnable.class)))
        .thenAnswer(invocation -> {
          scheduled.add(new ScheduledTask(invocation.getArgument(0), invocation.getArgument(1)));
          return true;
        });
  }

  private static void setInt(TweakVehicleIdleBrake tweak, String fieldName, int value) throws ReflectiveOperationException {
    Field field = TweakVehicleIdleBrake.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.setInt(tweak, value);
  }

  private record ScheduledTask(Player player, Runnable runnable) {
  }
}
