package art.arcane.react.content.feature;

import art.arcane.react.nms.NmsBridges;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HopperChainCoalescingRepairTest {

  @Test
  void hundredThousandDirtyCoordinatesKeepBoundedQueueAndSchedulerWave() throws Exception {
    FeatureHopperChainCoalescing feature = new FeatureHopperChainCoalescing();
    feature.onActivate();
    setInt(feature, "repairChunksPerTick", Integer.MAX_VALUE);
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    List<Runnable> scheduledTasks = new ArrayList<>();

    Method queueRepair = FeatureHopperChainCoalescing.class.getDeclaredMethod(
        "queueRepair",
        nestedClass("ChunkCoordinate"),
        boolean.class
    );
    queueRepair.setAccessible(true);
    for (int index = 0; index < 100_000; index++) {
      queueRepair.invoke(feature, coordinate(worldId, index, -index), true);
    }

    Queue<?> repairQueue = field(feature, "repairQueue", Queue.class);
    Set<?> queuedRepairs = field(feature, "queuedRepairs", Set.class);
    Map<?, ?> debounce = field(feature, "rebuildDebounce", Map.class);
    assertEquals(8192, repairQueue.size());
    assertEquals(8192, queuedRepairs.size());
    assertEquals(8192, debounce.size());

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

      Method process = FeatureHopperChainCoalescing.class.getDeclaredMethod(
          "processCoordinateRepairs",
          long.class
      );
      process.setAccessible(true);
      AtomicLong generation = field(feature, "lifecycleGeneration", AtomicLong.class);
      process.invoke(feature, generation.get());
      assertEquals(256, scheduledTasks.size());

      feature.onDeactivate();
      for (Runnable task : scheduledTasks) {
        task.run();
      }
    }

    assertTrue(repairQueue.isEmpty());
    assertTrue(queuedRepairs.isEmpty());
    Mockito.verify(world, Mockito.never()).isChunkLoaded(Mockito.anyInt(), Mockito.anyInt());
  }

  @Test
  void repairTaskReadsOnlyItsOwningChunk() throws Exception {
    FeatureHopperChainCoalescing feature = new FeatureHopperChainCoalescing();
    feature.onActivate();
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(world.isChunkLoaded(14, -9)).thenReturn(true);
    Mockito.when(world.getChunkAt(14, -9)).thenReturn(chunk);
    Mockito.when(chunk.getTileEntities()).thenReturn(new BlockState[0]);

    Method queueRepair = FeatureHopperChainCoalescing.class.getDeclaredMethod(
        "queueRepair",
        nestedClass("ChunkCoordinate"),
        boolean.class
    );
    queueRepair.setAccessible(true);
    queueRepair.invoke(feature, coordinate(worldId, 14, -9), true);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(() -> J.runChunk(
          Mockito.same(world),
          Mockito.eq(14),
          Mockito.eq(-9),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        Runnable task = invocation.getArgument(3);
        task.run();
        return true;
      });

      Method process = FeatureHopperChainCoalescing.class.getDeclaredMethod(
          "processCoordinateRepairs",
          long.class
      );
      process.setAccessible(true);
      AtomicLong generation = field(feature, "lifecycleGeneration", AtomicLong.class);
      process.invoke(feature, generation.get());
    }

    Mockito.verify(world).getChunkAt(14, -9);
    Mockito.verify(world, Mockito.times(1)).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
  }

  private static Object coordinate(UUID worldId, int chunkX, int chunkZ) throws Exception {
    Class<?> coordinateType = nestedClass("ChunkCoordinate");
    Constructor<?> constructor = coordinateType.getDeclaredConstructor(UUID.class, int.class, int.class);
    constructor.setAccessible(true);
    return constructor.newInstance(worldId, chunkX, chunkZ);
  }

  private static Class<?> nestedClass(String simpleName) {
    for (Class<?> type : FeatureHopperChainCoalescing.class.getDeclaredClasses()) {
      if (type.getSimpleName().equals(simpleName)) {
        return type;
      }
    }
    throw new IllegalStateException("Missing nested class " + simpleName);
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
