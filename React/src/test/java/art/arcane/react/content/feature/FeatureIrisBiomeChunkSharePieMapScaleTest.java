package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class FeatureIrisBiomeChunkSharePieMapScaleTest {

  @Test
  void samplingRequiresTheIrisCapability() {
    FeatureIrisBiomeChunkSharePieMap feature = new FeatureIrisBiomeChunkSharePieMap();

    CapabilityGatedFeature gated = Assertions.assertInstanceOf(CapabilityGatedFeature.class, feature);
    Assertions.assertEquals(Set.of("iris"), gated.requiredCapabilities());
  }

  @Test
  void hundredThousandCoordinatesDispatchOnlyOneBoundedOwnerWindow() {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    ObserverController observer = Mockito.mock(ObserverController.class);
    List<ObserverController.LoadedChunkTarget> coordinates = coordinates(worldId, 100_000);
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(32)).thenReturn(coordinates);

    FeatureIrisBiomeChunkSharePieMap feature = new FeatureIrisBiomeChunkSharePieMap();
    feature.onActivate();
    AtomicInteger scheduled = new AtomicInteger();
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(() -> J.runChunk(
          Mockito.same(world),
          Mockito.anyInt(),
          Mockito.anyInt(),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        scheduled.incrementAndGet();
        return true;
      });

      feature.sampleNextBatch();

      Assertions.assertEquals(32, scheduled.get());
      Mockito.verify(observer).nextLoadedChunkCoordinateBatch(32);
      Mockito.verify(world, Mockito.never()).getLoadedChunks();
      Mockito.verify(world, Mockito.never()).isChunkLoaded(Mockito.anyInt(), Mockito.anyInt());
      Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
    } finally {
      feature.onDeactivate();
    }
  }

  @Test
  void outstandingOwnerTasksRemainCappedWhenTheServerCannotDrainThem() {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    ObserverController observer = Mockito.mock(ObserverController.class);
    List<ObserverController.LoadedChunkTarget> coordinates = coordinates(worldId, 100_000);
    AtomicInteger nextOffset = new AtomicInteger();
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(32)).thenAnswer(invocation -> {
      int start = nextOffset.getAndAdd(32);
      return coordinates.subList(start, start + 32);
    });

    FeatureIrisBiomeChunkSharePieMap feature = new FeatureIrisBiomeChunkSharePieMap();
    feature.onActivate();
    AtomicInteger scheduled = new AtomicInteger();
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(() -> J.runChunk(
          Mockito.same(world),
          Mockito.anyInt(),
          Mockito.anyInt(),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        scheduled.incrementAndGet();
        return true;
      });

      for (int pass = 0; pass < 10; pass++) {
        feature.sampleNextBatch();
      }

      Assertions.assertEquals(128, scheduled.get());
      Mockito.verify(world, Mockito.never()).getLoadedChunks();
      Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
    } finally {
      feature.onDeactivate();
    }
  }

  @Test
  void chunkUnloadCancelsQueuedOwnerWorkWithoutStaleResurrection() {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Chunk chunk = Mockito.mock(Chunk.class);
    ChunkLoadEvent load = Mockito.mock(ChunkLoadEvent.class);
    ChunkUnloadEvent unload = Mockito.mock(ChunkUnloadEvent.class);
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(chunk.getWorld()).thenReturn(world);
    Mockito.when(chunk.getX()).thenReturn(12);
    Mockito.when(chunk.getZ()).thenReturn(-7);
    Mockito.when(load.getChunk()).thenReturn(chunk);
    Mockito.when(unload.getChunk()).thenReturn(chunk);
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(32)).thenReturn(List.of());

    FeatureIrisBiomeChunkSharePieMap feature = new FeatureIrisBiomeChunkSharePieMap();
    feature.onActivate();
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(() -> J.runChunk(
          Mockito.same(world),
          Mockito.eq(12),
          Mockito.eq(-7),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(3));
        return true;
      });

      feature.onChunkLoad(load);
      feature.sampleNextBatch();
      Assertions.assertNotNull(ownerTask.get());
      feature.onChunkUnload(unload);
      Mockito.clearInvocations(world);
      ownerTask.get().run();

      Mockito.verifyNoInteractions(world);
      Assertions.assertTrue(feature.bucketSnapshot(worldId).isEmpty());
    } finally {
      feature.onDeactivate();
    }
  }

  @Test
  void ownerSamplesMaintainExactCountsAndUnloadRemovesItsSlice() {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Chunk plains = Mockito.mock(Chunk.class);
    Chunk desert = Mockito.mock(Chunk.class);
    ChunkUnloadEvent unload = Mockito.mock(ChunkUnloadEvent.class);
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt())).thenReturn(true);
    Mockito.when(world.getMinHeight()).thenReturn(-64);
    Mockito.when(world.getMaxHeight()).thenReturn(320);
    Mockito.when(world.getSeaLevel()).thenReturn(63);
    Mockito.when(world.getChunkAt(1, 0)).thenReturn(plains);
    Mockito.when(world.getChunkAt(2, 0)).thenReturn(desert);
    Mockito.when(plains.getWorld()).thenReturn(world);
    Mockito.when(plains.getX()).thenReturn(1);
    Mockito.when(plains.getZ()).thenReturn(0);
    Mockito.when(desert.getWorld()).thenReturn(world);
    Mockito.when(desert.getX()).thenReturn(2);
    Mockito.when(desert.getZ()).thenReturn(0);
    Mockito.when(unload.getChunk()).thenReturn(plains);
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(32)).thenReturn(List.of(
        new ObserverController.LoadedChunkTarget(worldId, 1, 0),
        new ObserverController.LoadedChunkTarget(worldId, 2, 0)
    ));

    TestBiomeMap feature = new TestBiomeMap();
    feature.onActivate();
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(() -> J.runChunk(
          Mockito.same(world),
          Mockito.anyInt(),
          Mockito.anyInt(),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        Runnable task = invocation.getArgument(3);
        task.run();
        return true;
      });

      feature.sampleNextBatch();
      Assertions.assertEquals(Map.of("Plains", 1L, "Desert", 1L), feature.bucketSnapshot(worldId));
      Assertions.assertEquals(List.of(63, 63), feature.sampleHeights);

      feature.onChunkUnload(unload);
      Assertions.assertEquals(Map.of("Desert", 1L), feature.bucketSnapshot(worldId));
      Mockito.verify(world, Mockito.never()).getLoadedChunks();
    } finally {
      feature.onDeactivate();
    }
  }

  private static List<ObserverController.LoadedChunkTarget> coordinates(UUID worldId, int count) {
    List<ObserverController.LoadedChunkTarget> coordinates = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      coordinates.add(new ObserverController.LoadedChunkTarget(worldId, index, -index));
    }
    return coordinates;
  }

  private static final class TestBiomeMap extends FeatureIrisBiomeChunkSharePieMap {
    private final List<Integer> sampleHeights = new ArrayList<>();

    @Override
    protected String labelForChunkBiome(Chunk chunk, int y) {
      sampleHeights.add(y);
      return chunk.getX() == 1 ? "Plains" : "Desert";
    }
  }
}
