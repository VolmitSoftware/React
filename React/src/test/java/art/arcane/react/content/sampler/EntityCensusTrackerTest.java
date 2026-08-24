package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class EntityCensusTrackerTest {
  private React previous;
  private React plugin;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
    EntityCensusTracker.release();
    EntityCensusTracker.acquire();
  }

  @AfterEach
  void tearDown() {
    EntityCensusTracker.release();
    React.instance = previous;
  }

  @Test
  void thousandLoadedChunksHaveTheDocumentedRotationHorizon() {
    Assertions.assertEquals(64_000L, EntityCensusTracker.coverageHorizonMS(1000));
    Assertions.assertEquals(192_000L, EntityCensusTracker.coverageHorizonMS(1000, 300));
  }

  @Test
  void denseChunkCursorConvergesUnderThePerPassEntityBudget() {
    EntityCensusTracker.ChunkScanCursor cursor = new EntityCensusTracker.ChunkScanCursor();
    Set<Integer> visited = new HashSet<>();

    for (int pass = 0; pass < 3; pass++) {
      int start = cursor.claim(300, 128);
      for (int offset = 0; offset < 128; offset++) {
        visited.add((start + offset) % 300);
      }
    }

    Assertions.assertEquals(300, visited.size());
    Assertions.assertEquals(84, cursor.claim(300, 128));
  }

  @Test
  void foliaRefreshReadsTheCoordinateIndexOnlyInsideTheGlobalTaskAndRemainsSingleFlight() {
    Deque<Runnable> globalTasks = new ArrayDeque<>();
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(32)).thenReturn(List.of());

    try (MockedStatic<FoliaScheduler> scheduler = Mockito.mockStatic(FoliaScheduler.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      captureGlobalTasks(scheduler, globalTasks);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);

      EntityCensusTracker.refreshFolia();
      EntityCensusTracker.refreshFolia();

      Assertions.assertEquals(1, globalTasks.size());
      Mockito.verify(observer, Mockito.never()).nextLoadedChunkCoordinateBatch(Mockito.anyInt());

      globalTasks.removeFirst().run();

      Mockito.verify(observer).nextLoadedChunkCoordinateBatch(32);
      Assertions.assertEquals(0, EntityCensusTracker.groundItems());
    }
  }

  @Test
  void releasedGenerationCancelsQueuedGlobalCaptureAndAllowsRestart() {
    Deque<Runnable> globalTasks = new ArrayDeque<>();
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(32)).thenReturn(List.of());

    try (MockedStatic<FoliaScheduler> scheduler = Mockito.mockStatic(FoliaScheduler.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      captureGlobalTasks(scheduler, globalTasks);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      EntityCensusTracker.refreshFolia();
      Runnable staleCapture = globalTasks.removeFirst();

      EntityCensusTracker.release();
      EntityCensusTracker.acquire();
      staleCapture.run();

      Mockito.verify(observer, Mockito.never()).nextLoadedChunkCoordinateBatch(Mockito.anyInt());

      EntityCensusTracker.refreshFolia();

      Assertions.assertEquals(1, globalTasks.size());
      globalTasks.removeFirst().run();
      Mockito.verify(observer).nextLoadedChunkCoordinateBatch(32);
    }
  }

  @Test
  void unoccupiedLoadedChunkParticipatesInFoliaCensus() {
    Deque<Runnable> globalTasks = new ArrayDeque<>();
    ObserverController observer = Mockito.mock(ObserverController.class);
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(32)).thenReturn(List.of(
        new ObserverController.LoadedChunkTarget(worldId, 4, -7)
    ));
    Mockito.when(world.isChunkLoaded(4, -7)).thenReturn(true);
    Mockito.when(world.getChunkAt(4, -7, false)).thenReturn(chunk);
    Mockito.when(chunk.isEntitiesLoaded()).thenReturn(true);
    Item item = Mockito.mock(Item.class);
    Mockito.when(item.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(chunk.getEntities()).thenReturn(new Entity[]{item});
    Mockito.when(world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});

    try (MockedStatic<FoliaScheduler> scheduler = Mockito.mockStatic(FoliaScheduler.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      captureGlobalTasks(scheduler, globalTasks);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduling.when(() -> J.isOwnedByCurrentRegion(item)).thenReturn(true);
      scheduling.when(() -> J.runChunk(
          Mockito.eq(world),
          Mockito.eq(4),
          Mockito.eq(-7),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(3).run();
        return true;
      });

      EntityCensusTracker.refreshFolia();
      globalTasks.removeFirst().run();

      Assertions.assertEquals(1, EntityCensusTracker.groundItems());
      Mockito.verify(observer).nextLoadedChunkCoordinateBatch(32);
    }
  }

  @Test
  void hundredThousandLoadedCoordinatesDispatchOnlyOneBoundedWindow() {
    int loadedChunkCount = 100_000;
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class, Mockito.withSettings().stubOnly());
    Chunk repeatedChunk = Mockito.mock(Chunk.class, Mockito.withSettings().stubOnly());
    Chunk[] loadedChunks = new Chunk[loadedChunkCount];
    Arrays.fill(loadedChunks, repeatedChunk);
    AtomicInteger loadedArrayReads = new AtomicInteger(0);
    AtomicInteger nextChunkX = new AtomicInteger(0);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getKey()).thenReturn(new NamespacedKey("react-test", "census-scale"));
    Mockito.when(world.getName()).thenReturn("census-scale");
    Mockito.when(world.getSpawnLocation()).thenReturn(new Location(world, 0D, 64D, 0D));
    Mockito.when(world.getLoadedChunks()).thenAnswer(invocation -> {
      loadedArrayReads.incrementAndGet();
      return loadedChunks;
    });
    Mockito.when(repeatedChunk.getWorld()).thenReturn(world);
    Mockito.when(repeatedChunk.getX()).thenAnswer(invocation -> nextChunkX.getAndIncrement());
    Mockito.when(repeatedChunk.getZ()).thenReturn(0);
    Set<Integer> dispatchedChunkX = new HashSet<>();
    Deque<Runnable> globalTasks = new ArrayDeque<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<FoliaScheduler> scheduler = Mockito.mockStatic(FoliaScheduler.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      captureGlobalTasks(scheduler, globalTasks);
      ObserverController observer = new ObserverController();
      observer.start();
      Assertions.assertEquals(0, nextChunkX.get());
      observer.onTick();
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      scheduling.when(() -> J.runChunk(
          Mockito.eq(world),
          Mockito.anyInt(),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        dispatchedChunkX.add(invocation.getArgument(1));
        return true;
      });

      EntityCensusTracker.refreshFolia();
      globalTasks.removeFirst().run();

      Assertions.assertEquals(1, loadedArrayReads.get());
      Assertions.assertEquals(256, nextChunkX.get());
      Assertions.assertEquals(32, dispatchedChunkX.size());
      for (int chunkX = 0; chunkX < 32; chunkX++) {
        Assertions.assertTrue(dispatchedChunkX.contains(chunkX));
      }
      observer.stop();
    }
  }

  @Test
  void eventMaintainedCategoriesDeduplicateAndForgetEntities() {
    Item item = Mockito.mock(Item.class);
    UUID entityId = UUID.randomUUID();
    Mockito.when(item.getUniqueId()).thenReturn(entityId);

    EntityCensusTracker.observe(item);
    EntityCensusTracker.observe(item);

    Assertions.assertEquals(1, EntityCensusTracker.groundItems());

    EntityCensusTracker.forget(entityId);
    EntityCensusTracker.forget(entityId);

    Assertions.assertEquals(0, EntityCensusTracker.groundItems());
  }

  @Test
  void paperRefreshSamplesOneBoundedWindowWithoutReplacingEventMaintainedCounts() {
    World world = Mockito.mock(World.class);
    Chunk chunk = Mockito.mock(Chunk.class);
    Item eventObserved = Mockito.mock(Item.class);
    Item sampled = Mockito.mock(Item.class);
    Mockito.when(eventObserved.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(sampled.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(sampled.getChunk()).thenReturn(chunk);
    EntityCensusTracker.observe(eventObserved);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<WorldEntitySnapshots> snapshots = Mockito.mockStatic(WorldEntitySnapshots.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      snapshots.when(() -> WorldEntitySnapshots.next(world, 128)).thenReturn(List.of(sampled));

      EntityCensusTracker.refreshMainThread();

      snapshots.verify(() -> WorldEntitySnapshots.next(world, 128));
      Assertions.assertEquals(2, EntityCensusTracker.groundItems());
    }
  }

  private void captureGlobalTasks(MockedStatic<FoliaScheduler> scheduler, Deque<Runnable> globalTasks) {
    scheduler.when(() -> FoliaScheduler.runGlobal(
        Mockito.eq(plugin),
        Mockito.any(Runnable.class)
    )).thenAnswer(invocation -> {
      globalTasks.addLast(invocation.getArgument(1));
      return true;
    });
  }
}
