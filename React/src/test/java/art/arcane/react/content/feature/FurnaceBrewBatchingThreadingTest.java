package art.arcane.react.content.feature;

import art.arcane.react.core.controller.ObserverController.LoadedChunkTarget;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnaceBrewBatchingThreadingTest {

  @Test
  void measurementWaveCapsOwnerSchedulerFanoutAcrossEightThousandEntries() throws Exception {
    FeatureFurnaceBrewBatching feature = activeFeature();
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(world.getUID()).thenReturn(worldId);

    Method add = FeatureFurnaceBrewBatching.class.getDeclaredMethod(
        "add",
        World.class,
        int.class,
        int.class,
        int.class,
        nestedClass("TrackedKind")
    );
    add.setAccessible(true);
    Object furnaceKind = enumConstant(nestedClass("TrackedKind"), "FURNACE");
    for (int index = 0; index < 8192; index++) {
      add.invoke(feature, world, index << 4, 64, 0, furnaceKind);
    }

    AtomicInteger scheduledTasks = new AtomicInteger();
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(() -> J.runChunk(
          Mockito.same(world),
          Mockito.anyInt(),
          Mockito.anyInt(),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        scheduledTasks.incrementAndGet();
        return true;
      });

      Method measure = FeatureFurnaceBrewBatching.class.getDeclaredMethod("measure", long.class);
      measure.setAccessible(true);
      measure.invoke(feature, 1L);
    }

    assertEquals(32, scheduledTasks.get());
  }

  @Test
  void hundredThousandStartupCoordinatesStayBoundedAndRetireOnDeactivation() throws Exception {
    FeatureFurnaceBrewBatching feature = activeFeature();
    setInt(feature, "reseedChunksPerTick", Integer.MAX_VALUE);
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    List<LoadedChunkTarget> targets = new ArrayList<>(100_000);
    for (int index = 0; index < 100_000; index++) {
      targets.add(new LoadedChunkTarget(worldId, index, -index));
    }
    List<Runnable> scheduledTasks = new ArrayList<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
         MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(() -> J.runChunk(
          Mockito.same(world),
          Mockito.anyInt(),
          Mockito.anyInt(),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        scheduledTasks.add(invocation.getArgument(3));
        return true;
      });
      bridges.when(NmsBridges::get).thenReturn(null);

      Method schedule = FeatureFurnaceBrewBatching.class.getDeclaredMethod(
          "scheduleReseedTargets",
          List.class,
          long.class
      );
      schedule.setAccessible(true);
      schedule.invoke(feature, targets, 1L);
      assertEquals(256, scheduledTasks.size());

      feature.onDeactivate();
      for (Runnable task : scheduledTasks) {
        task.run();
      }
    }

    Set<?> inFlight = field(feature, "seedTasksInFlight", Set.class);
    Queue<?> rotation = field(feature, "measurementChunkRotation", Queue.class);
    assertTrue(inFlight.isEmpty());
    assertTrue(rotation.isEmpty());
    Mockito.verify(world, Mockito.never()).isChunkLoaded(Mockito.anyInt(), Mockito.anyInt());
  }

  private static FeatureFurnaceBrewBatching activeFeature() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    Field active = FeatureFurnaceBrewBatching.class.getDeclaredField("active");
    active.setAccessible(true);
    active.setBoolean(feature, true);
    AtomicLong generation = field(feature, "lifecycleGeneration", AtomicLong.class);
    generation.set(1L);
    return feature;
  }

  private static Class<?> nestedClass(String simpleName) {
    for (Class<?> type : FeatureFurnaceBrewBatching.class.getDeclaredClasses()) {
      if (type.getSimpleName().equals(simpleName)) {
        return type;
      }
    }
    throw new IllegalStateException("Missing nested class " + simpleName);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Object enumConstant(Class<?> type, String name) {
    return Enum.valueOf((Class<? extends Enum>) type, name);
  }

  private static <T> T field(Object target, String name, Class<T> type) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return type.cast(field.get(target));
  }

  private static void setInt(Object target, String name, int value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.setInt(target, value);
  }
}
