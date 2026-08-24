package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class ObserverControllerLoadedChunkIndexTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void repeatedViewerQueriesUseTheEventMaintainedSpatialIndex() {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(world.getKey()).thenReturn(new NamespacedKey("react-test", "world"));
    Mockito.when(world.getName()).thenReturn("world");
    Mockito.when(world.getSpawnLocation()).thenReturn(new Location(world, 32D, 64D, -48D));
    List<Chunk> chunks = new ArrayList<>();
    for (int chunkX = -4; chunkX <= 4; chunkX++) {
      for (int chunkZ = -4; chunkZ <= 4; chunkZ++) {
        chunks.add(chunk(world, chunkX, chunkZ));
      }
    }
    Mockito.when(world.getLoadedChunks()).thenReturn(chunks.toArray(Chunk[]::new));

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      ObserverController controller = new ObserverController();
      controller.start();
      controller.onTick();

      HeatmapWorldRef snapshot = controller.heatmapWorld("world").orElseThrow();
      Assertions.assertEquals("react-test:world", snapshot.worldKey());
      Assertions.assertEquals(2, snapshot.spawnChunkX());
      Assertions.assertEquals(-3, snapshot.spawnChunkZ());

      for (int viewer = 0; viewer < 1_000; viewer++) {
        Assertions.assertEquals(13, controller.loadedChunkCoordinatesInRadius(world, 0, 0, 2).size());
      }

      Mockito.verify(world, Mockito.times(1)).getLoadedChunks();

      Chunk added = chunk(world, 8, 8);
      ChunkLoadEvent load = Mockito.mock(ChunkLoadEvent.class);
      Mockito.when(load.getChunk()).thenReturn(added);
      controller.on(load);
      Assertions.assertEquals(
          List.of(new ObserverController.LoadedChunkCoordinate(8, 8)),
          controller.loadedChunkCoordinatesInRadius(world, 8, 8, 0)
      );

      ChunkUnloadEvent unload = Mockito.mock(ChunkUnloadEvent.class);
      Mockito.when(unload.getChunk()).thenReturn(added);
      controller.on(unload);
      Assertions.assertTrue(controller.loadedChunkCoordinatesInRadius(world, 8, 8, 0).isEmpty());

      WorldUnloadEvent cancelledUnload = Mockito.mock(WorldUnloadEvent.class);
      Mockito.when(cancelledUnload.getWorld()).thenReturn(world);
      Mockito.when(cancelledUnload.isCancelled()).thenReturn(true);
      controller.on(cancelledUnload);
      Assertions.assertEquals(13, controller.loadedChunkCoordinatesInRadius(world, 0, 0, 2).size());
      controller.stop();
    }
  }

  @Test
  void sparseWideQueryScansLoadedChunksInsteadOfEveryCoordinateCell() {
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getKey()).thenReturn(new NamespacedKey("react-test", "sparse"));
    Mockito.when(world.getName()).thenReturn("sparse");
    Mockito.when(world.getSpawnLocation()).thenReturn(new Location(world, 0D, 64D, 0D));
    Chunk chunk = chunk(world, 4, 6);
    Mockito.when(world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      ObserverController controller = new ObserverController();
      controller.start();
      controller.onTick();
      Mockito.clearInvocations(chunk);

      Assertions.assertEquals(
          List.of(new ObserverController.LoadedChunkCoordinate(4, 6)),
          controller.loadedChunkCoordinatesInRadius(world, 0, 0, 160)
      );
      Mockito.verify(chunk, Mockito.never()).getX();
      Mockito.verify(chunk, Mockito.never()).getZ();
      controller.stop();
    }
  }

  @Test
  void heatmapWorldResolutionUsesPublishedMetadataAndPrefersTheMostLoadedWorld() {
    World quiet = world("quiet", 0, 0);
    World active = world("active", 7, -9);
    Chunk quietChunk = chunk(quiet, 0, 0);
    Chunk firstActiveChunk = chunk(active, 10, 10);
    Chunk secondActiveChunk = chunk(active, 11, 10);
    Mockito.when(quiet.getLoadedChunks()).thenReturn(new Chunk[]{quietChunk});
    Mockito.when(active.getLoadedChunks()).thenReturn(new Chunk[]{firstActiveChunk, secondActiveChunk});

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(quiet, active));
      ObserverController controller = new ObserverController();
      controller.start();
      controller.onTick();
      Mockito.clearInvocations(quiet, active);

      for (int request = 0; request < 1_000; request++) {
        HeatmapWorldRef selected = controller.heatmapWorld((String) null).orElseThrow();
        Assertions.assertEquals("react-test:active", selected.worldKey());
        Assertions.assertEquals(7, selected.spawnChunkX());
        Assertions.assertEquals(-9, selected.spawnChunkZ());
      }

      Mockito.verifyNoInteractions(quiet, active);
      bukkit.verify(Bukkit::getWorlds);
      bukkit.verifyNoMoreInteractions();
      controller.stop();
    }
  }

  @Test
  void initialLoadedChunkCaptureReadsCoordinatesInBoundedWavesAndReleasesTheArray()
      throws ReflectiveOperationException {
    int loadedChunkCount = 513;
    World world = world("seed-wave", 0, 0);
    Chunk repeatedChunk = Mockito.mock(Chunk.class);
    Chunk[] loadedChunks = new Chunk[loadedChunkCount];
    Arrays.fill(loadedChunks, repeatedChunk);
    AtomicInteger coordinateReads = new AtomicInteger(0);
    Mockito.when(repeatedChunk.getWorld()).thenReturn(world);
    Mockito.when(repeatedChunk.getX()).thenAnswer(invocation -> coordinateReads.getAndIncrement());
    Mockito.when(repeatedChunk.getZ()).thenReturn(0);
    Mockito.when(world.getLoadedChunks()).thenReturn(loadedChunks);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      ObserverController controller = new ObserverController();
      controller.start();
      Object seed = onlyInitialSeed(controller);

      Assertions.assertEquals(0, coordinateReads.get());
      Assertions.assertSame(loadedChunks, retainedSeedArray(seed));

      controller.onTick();
      Assertions.assertEquals(256, coordinateReads.get());
      Assertions.assertSame(loadedChunks, retainedSeedArray(seed));

      controller.onTick();
      Assertions.assertEquals(512, coordinateReads.get());
      Assertions.assertSame(loadedChunks, retainedSeedArray(seed));

      controller.onTick();
      Assertions.assertEquals(loadedChunkCount, coordinateReads.get());
      Assertions.assertNull(retainedSeedArray(seed));
      Assertions.assertTrue(initialSeeds(controller).isEmpty());
      controller.stop();
    }
  }

  @Test
  void stopReleasesAnUndrainedInitialChunkArray() throws ReflectiveOperationException {
    World world = world("seed-stop", 0, 0);
    Chunk chunk = chunk(world, 3, -5);
    Chunk[] loadedChunks = new Chunk[]{chunk};
    Mockito.when(world.getLoadedChunks()).thenReturn(loadedChunks);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      ObserverController controller = new ObserverController();
      controller.start();
      Object seed = onlyInitialSeed(controller);
      Assertions.assertSame(loadedChunks, retainedSeedArray(seed));

      controller.stop();

      Assertions.assertNull(retainedSeedArray(seed));
      Assertions.assertTrue(initialSeeds(controller).isEmpty());
    }
  }

  @Test
  void unloadBeforeASeedWavePreventsLateCoordinateResurrection() {
    World world = world("seed-unload", 0, 0);
    Chunk chunk = chunk(world, 12, -9);
    Mockito.when(world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      ObserverController controller = new ObserverController();
      controller.start();
      ChunkUnloadEvent unload = Mockito.mock(ChunkUnloadEvent.class);
      Mockito.when(unload.getChunk()).thenReturn(chunk);

      controller.on(unload);
      controller.onTick();

      Assertions.assertTrue(controller.loadedChunkCoordinatesInRadius(world, 12, -9, 0).isEmpty());
      controller.stop();
    }
  }

  private World world(String name, int spawnChunkX, int spawnChunkZ) {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(world.getKey()).thenReturn(new NamespacedKey("react-test", name));
    Mockito.when(world.getName()).thenReturn(name);
    Mockito.when(world.getSpawnLocation()).thenReturn(new Location(
        world,
        spawnChunkX * 16D,
        64D,
        spawnChunkZ * 16D
    ));
    return world;
  }

  private Chunk chunk(World world, int chunkX, int chunkZ) {
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(chunk.getWorld()).thenReturn(world);
    Mockito.when(chunk.getX()).thenReturn(chunkX);
    Mockito.when(chunk.getZ()).thenReturn(chunkZ);
    return chunk;
  }

  private Object onlyInitialSeed(ObserverController controller) throws ReflectiveOperationException {
    Map<?, ?> seeds = initialSeeds(controller);
    Assertions.assertEquals(1, seeds.size());
    return seeds.values().iterator().next();
  }

  private Map<?, ?> initialSeeds(ObserverController controller) throws ReflectiveOperationException {
    Field field = ObserverController.class.getDeclaredField("initialChunkSeedsByWorld");
    field.setAccessible(true);
    return (Map<?, ?>) field.get(controller);
  }

  private Chunk[] retainedSeedArray(Object seed) throws ReflectiveOperationException {
    Field field = seed.getClass().getDeclaredField("loadedChunks");
    field.setAccessible(true);
    return (Chunk[]) field.get(seed);
  }
}
