package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

class MapControllerChunkRepairQueueTest {
  private React previous;
  private React plugin;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    Mockito.when(plugin.isEnabled()).thenReturn(true);
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void startupCapturesThousandsOnceAndOnePassSeedsAndDispatchesOnlyTheConfiguredBatch()
      throws ReflectiveOperationException {
    int loadedChunkCount = 4_096;
    int batchSize = 23;
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Chunk[] chunks = new Chunk[loadedChunkCount];
    AtomicInteger coordinateReads = new AtomicInteger();
    for (int index = 0; index < loadedChunkCount; index++) {
      int coordinate = index;
      Chunk chunk = Mockito.mock(Chunk.class);
      Mockito.when(chunk.getX()).thenAnswer(invocation -> {
        coordinateReads.incrementAndGet();
        return coordinate;
      });
      Mockito.when(chunk.getZ()).thenAnswer(invocation -> {
        coordinateReads.incrementAndGet();
        return -coordinate;
      });
      chunks[index] = chunk;
    }
    Mockito.when(world.getLoadedChunks()).thenReturn(chunks);

    MapController controller = new MapController();
    controller.start();
    controller.setItemFrameChunkBatchSize(batchSize);
    controller.setStartupBoostItemFrameChunkBatchSize(batchSize);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Location.class))).thenReturn(false);
      scheduling.when(() -> J.runChunk(
          Mockito.eq(world),
          Mockito.anyInt(),
          Mockito.anyInt(),
          Mockito.any(Runnable.class)
      )).thenReturn(true);

      captureLoadedChunks(controller);
      Assertions.assertEquals(0, coordinateReads.get());
      scheduling.verify(() -> J.runChunk(
          Mockito.any(World.class),
          Mockito.anyInt(),
          Mockito.anyInt(),
          Mockito.any(Runnable.class)
      ), Mockito.never());

      repairLoadedChunkBatch(controller);

      Assertions.assertEquals(batchSize * 2, coordinateReads.get());
      Mockito.verify(world, Mockito.times(1)).getLoadedChunks();
      scheduling.verify(() -> J.runChunk(
          Mockito.eq(world),
          Mockito.anyInt(),
          Mockito.anyInt(),
          Mockito.any(Runnable.class)
      ), Mockito.times(batchSize));
    } finally {
      controller.stop();
    }
  }

  @Test
  void stoppedRuntimeRejectsAnAcceptedChunkRepairBeforeReadingTheChunk()
      throws ReflectiveOperationException {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});
    Mockito.when(chunk.getX()).thenReturn(4);
    Mockito.when(chunk.getZ()).thenReturn(-7);

    MapController controller = new MapController();
    controller.start();
    controller.setItemFrameChunkBatchSize(1);
    controller.setStartupBoostItemFrameChunkBatchSize(1);
    AtomicReference<Runnable> accepted = new AtomicReference<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Location.class))).thenReturn(false);
      scheduling.when(() -> J.runChunk(
          Mockito.eq(world),
          Mockito.eq(4),
          Mockito.eq(-7),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        accepted.set(invocation.getArgument(3));
        return true;
      });

      captureLoadedChunks(controller);
      repairLoadedChunkBatch(controller);
      Assertions.assertNotNull(accepted.get());

      controller.stop();
      accepted.get().run();

      Mockito.verify(world, Mockito.never()).isChunkLoaded(Mockito.anyInt(), Mockito.anyInt());
      Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
    }
  }

  private void captureLoadedChunks(MapController controller) throws ReflectiveOperationException {
    Method method = MapController.class.getDeclaredMethod("captureLoadedFrameRepairSeeds");
    method.setAccessible(true);
    method.invoke(controller);
  }

  private void repairLoadedChunkBatch(MapController controller) throws ReflectiveOperationException {
    Method method = MapController.class.getDeclaredMethod("repairOneLoadedChunkItemFrames", Supplier.class);
    method.setAccessible(true);
    method.invoke(controller, (Supplier<Object>) () -> null);
  }
}
