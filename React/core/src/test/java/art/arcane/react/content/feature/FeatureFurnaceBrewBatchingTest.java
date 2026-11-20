package art.arcane.react.content.feature;

import art.arcane.react.api.feature.PressureGate;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.FurnaceHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureFurnaceBrewBatchingTest {
  private final AtomicReference<FurnaceHost.TickHook> furnaceHook = new AtomicReference<>();
  private final AtomicReference<FurnaceHost.TickHook> brewingHook = new AtomicReference<>();
  private final AtomicReference<Consumer<ChunkRef>> chunkLoads = new AtomicReference<>();
  private final AtomicReference<Consumer<BlockHost.BlockEvent>> breaks = new AtomicReference<>();
  private final AtomicReference<Consumer<FurnaceHost.Station>> activity = new AtomicReference<>();
  private final AtomicReference<UUID> playerWorld = new AtomicReference<>();
  private final ReactWorld world = Mockito.mock(ReactWorld.class);
  private ExecutorService executor;
  private FurnaceHost furnaces;
  private BlockHost blocks;

  @BeforeEach
  void setUp() {
    executor = Executors.newSingleThreadExecutor();
    furnaces = Mockito.mock(FurnaceHost.class);
    Mockito.when(furnaces.hookTicks(Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
      furnaceHook.set(invocation.getArgument(0));
      brewingHook.set(invocation.getArgument(1));
      invocation.<Consumer<Boolean>>getArgument(2).accept(true);
      return Subscription.NONE;
    });
    Mockito.when(furnaces.onActivity(Mockito.any())).thenAnswer(invocation -> {
      activity.set(invocation.getArgument(0));
      return Subscription.NONE;
    });
    blocks = Mockito.mock(BlockHost.class);
    Mockito.when(blocks.onChunkLoad(Mockito.any())).thenAnswer(invocation -> {
      chunkLoads.set(invocation.getArgument(0));
      return Subscription.NONE;
    });
    Mockito.when(blocks.onBreak(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenAnswer(invocation -> {
      breaks.set(invocation.getArgument(2));
      return Subscription.NONE;
    });
    Mockito.when(blocks.onChunkUnload(Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(blocks.onWorldUnload(Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(blocks.onPlace(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(blocks.nextLoadedChunks(Mockito.anyInt())).thenReturn(List.of());
    WorldHost worlds = Mockito.mock(WorldHost.class);
    Mockito.when(worlds.world(Mockito.any())).thenReturn(Optional.of(world));
    Mockito.when(worlds.anyPlayerNear(Mockito.any(), Mockito.anyDouble()))
        .thenAnswer(invocation -> invocation.<GameLocation>getArgument(0).worldId().equals(playerWorld.get()));
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    Mockito.when(platform.furnaceHost()).thenReturn(Optional.of(furnaces));
    Mockito.when(platform.blockHost()).thenReturn(Optional.of(blocks));
    Mockito.when(platform.worldHost()).thenReturn(Optional.of(worlds));
    ReactPlatforms.unbind();
    ReactPlatforms.bind(platform);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    ReactPlatforms.unbind();
  }

  @Test
  void skipDebtStaysWithItsWorldAtEqualCoordinates() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    engage(feature);
    UUID overworld = UUID.randomUUID();
    UUID nether = UUID.randomUUID();

    for (int tick = 0; tick < 3; tick++) {
      assertEquals(FurnaceHost.SKIP, furnaceHook.get().decide(overworld, 10, 64, 10));
      assertEquals(FurnaceHost.SKIP, brewingHook.get().decide(overworld, 10, 64, 10));
    }

    playerWorld.set(nether);
    assertEquals(0, furnaceHook.get().decide(nether, 10, 64, 10));
    assertEquals(0, brewingHook.get().decide(nether, 10, 64, 10));

    playerWorld.set(overworld);
    assertEquals(3, furnaceHook.get().decide(overworld, 10, 64, 10));
    assertEquals(3, brewingHook.get().decide(overworld, 10, 64, 10));
    assertEquals(3L, feature.readAndResetSkippedFurnaceTicks());
    assertTrue(feature.isBridgeActive());
    feature.onDeactivate();
  }

  @Test
  void hookWithoutOutstandingDebtDoesNotTakeTheDebtLock() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    UUID worldId = UUID.randomUUID();

    Object furnaceLock = field(feature, "furnaceSkipDebt");
    synchronized (furnaceLock) {
      Future<Integer> result = executor.submit(() -> furnaceHook.get().decide(worldId, 1, 64, 1));
      assertEquals(0, result.get(2, TimeUnit.SECONDS));
    }
    Object brewingLock = field(feature, "brewingSkipDebt");
    synchronized (brewingLock) {
      Future<Integer> result = executor.submit(() -> brewingHook.get().decide(worldId, 1, 64, 1));
      assertEquals(0, result.get(2, TimeUnit.SECONDS));
    }
    feature.onDeactivate();
  }

  @Test
  void breakingUntrackedMaterialSkipsDebtLocksWhileDebtIsOutstanding() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    engage(feature);
    UUID worldId = UUID.randomUUID();
    assertEquals(FurnaceHost.SKIP, furnaceHook.get().decide(worldId, 5, 70, 5));
    assertEquals(FurnaceHost.SKIP, brewingHook.get().decide(worldId, 6, 70, 6));
    BlockHost.BlockEvent stone = Mockito.mock(BlockHost.BlockEvent.class);
    Mockito.when(stone.type()).thenReturn("STONE");

    Object furnaceLock = field(feature, "furnaceSkipDebt");
    Object brewingLock = field(feature, "brewingSkipDebt");
    synchronized (furnaceLock) {
      synchronized (brewingLock) {
        Future<?> result = executor.submit(() -> breaks.get().accept(stone));
        result.get(2, TimeUnit.SECONDS);
      }
    }
    Mockito.verify(stone, Mockito.never()).location();

    playerWorld.set(worldId);
    assertEquals(1, furnaceHook.get().decide(worldId, 5, 70, 5));
    feature.onDeactivate();
  }

  @Test
  void breakingATrackedFurnaceForgivesItsDebt() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    engage(feature);
    UUID worldId = UUID.randomUUID();
    activity.get().accept(new FurnaceHost.Station(worldId, 5, 70, 5, FurnaceHost.Kind.FURNACE));
    assertEquals(FurnaceHost.SKIP, furnaceHook.get().decide(worldId, 5, 70, 5));
    BlockHost.BlockEvent smoker = Mockito.mock(BlockHost.BlockEvent.class);
    Mockito.when(smoker.type()).thenReturn("SMOKER");
    Mockito.when(smoker.location()).thenReturn(new GameLocation(worldId, 5, 70, 5));

    breaks.get().accept(smoker);

    assertEquals(0L, field(feature, "trackedBlockCount", AtomicLong.class).get());
    playerWorld.set(worldId);
    assertEquals(0, furnaceHook.get().decide(worldId, 5, 70, 5));
    feature.onDeactivate();
  }

  @Test
  void chunkLoadQueuesSeedForTheBudgetedTickInsteadOfScanning() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    UUID worldId = UUID.randomUUID();
    ChunkRef chunk = new ChunkRef(worldId, 3, -2);
    Mockito.when(blocks.isChunkLoaded(worldId, 3, -2)).thenReturn(true);
    Mockito.when(furnaces.scan(chunk)).thenReturn(List.of(new FurnaceHost.Station(worldId, 50, 12, -20, FurnaceHost.Kind.FURNACE)));
    List<Runnable> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.eq(3), Mockito.eq(-2), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> scheduled.add(invocation.getArgument(3)));

      chunkLoads.get().accept(chunk);
      Mockito.verify(furnaces, Mockito.never()).scan(Mockito.any());

      feature.onTick();
      assertEquals(1, scheduled.size());
      scheduled.get(0).run();
    }

    assertEquals(1L, field(feature, "trackedBlockCount", AtomicLong.class).get());
    feature.onDeactivate();
  }

  @Test
  void chunkLoadBurstLeavesAQuarterOfTheReseedBudgetForTheLoadedChunkRotation() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    UUID worldId = UUID.randomUUID();
    for (int chunkX = 0; chunkX < 40; chunkX++) {
      chunkLoads.get().accept(new ChunkRef(worldId, chunkX, 0));
    }
    List<ChunkRef> rotation = new ArrayList<>();
    for (int chunkX = 0; chunkX < 8; chunkX++) {
      rotation.add(new ChunkRef(worldId, chunkX, 100));
    }
    Mockito.when(blocks.nextLoadedChunks(8)).thenReturn(rotation);
    List<Integer> loadSeeds = new ArrayList<>();
    List<Integer> rotationSeeds = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> invocation.<Integer>getArgument(2) == 0
              ? loadSeeds.add(invocation.getArgument(1))
              : rotationSeeds.add(invocation.getArgument(1)));

      feature.onTick();
    }

    assertEquals(24, loadSeeds.size());
    assertEquals(8, rotationSeeds.size());
    feature.onDeactivate();
  }

  @Test
  void activityTracksStationsOnce() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    UUID worldId = UUID.randomUUID();

    activity.get().accept(new FurnaceHost.Station(worldId, 1, 63, 0, FurnaceHost.Kind.FURNACE));
    activity.get().accept(new FurnaceHost.Station(worldId, 1, 63, 0, FurnaceHost.Kind.FURNACE));
    activity.get().accept(new FurnaceHost.Station(worldId, 2, 63, 0, FurnaceHost.Kind.BREWING_STAND));

    assertEquals(2L, field(feature, "trackedBlockCount", AtomicLong.class).get());
    assertFalse(field(feature, "chunkIndexByKey", Map.class).isEmpty());
    feature.onDeactivate();
  }

  @Test
  void measurementWaveCapsOwnerSchedulerFanoutAcrossEightThousandEntries() throws Exception {
    FeatureFurnaceBrewBatching feature = activeFeature();
    UUID worldId = UUID.randomUUID();
    Method add = FeatureFurnaceBrewBatching.class.getDeclaredMethod(
        "add", UUID.class, int.class, int.class, int.class, FurnaceHost.Kind.class);
    add.setAccessible(true);
    for (int index = 0; index < 8192; index++) {
      add.invoke(feature, worldId, index << 4, 64, 0, FurnaceHost.Kind.FURNACE);
    }
    setField(feature, "worlds", ReactPlatforms.get().worldHost().orElseThrow());

    AtomicInteger scheduledTasks = new AtomicInteger();
    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
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
    setField(feature, "reseedChunksPerTick", Integer.MAX_VALUE);
    setField(feature, "worlds", ReactPlatforms.get().worldHost().orElseThrow());
    setField(feature, "blocks", blocks);
    UUID worldId = UUID.randomUUID();
    List<ChunkRef> targets = new ArrayList<>(100_000);
    for (int index = 0; index < 100_000; index++) {
      targets.add(new ChunkRef(worldId, index, -index));
    }
    List<Runnable> scheduledTasks = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            scheduledTasks.add(invocation.getArgument(3));
            return true;
          });

      Method schedule = FeatureFurnaceBrewBatching.class.getDeclaredMethod("scheduleReseedTargets", List.class, long.class);
      schedule.setAccessible(true);
      schedule.invoke(feature, targets, 1L);
      assertEquals(256, scheduledTasks.size());

      feature.onDeactivate();
      for (Runnable task : scheduledTasks) {
        task.run();
      }
    }

    assertTrue(field(feature, "seedTasksInFlight", Set.class).isEmpty());
    assertTrue(field(feature, "measurementChunkRotation", Queue.class).isEmpty());
    Mockito.verify(blocks, Mockito.never()).isChunkLoaded(Mockito.any(), Mockito.anyInt(), Mockito.anyInt());
  }

  @Test
  void furnaceProjectionStopsAtTheNextFuelOrCookEvent() {
    assertEquals(39L, FeatureFurnaceBrewBatching.projectFurnaceSkippable(
        new FurnaceHost.FurnaceState("FURNACE", 40, 100, 200, true, false)));
    assertEquals(99L, FeatureFurnaceBrewBatching.projectFurnaceSkippable(
        new FurnaceHost.FurnaceState("FURNACE", 1000, 100, 200, true, false)));
    assertEquals(99L, FeatureFurnaceBrewBatching.projectFurnaceSkippable(
        new FurnaceHost.FurnaceState("BLAST_FURNACE", 0, 0, 0, true, true)));
    assertEquals(0L, FeatureFurnaceBrewBatching.projectFurnaceSkippable(
        new FurnaceHost.FurnaceState("SMOKER", 40, 0, 100, false, true)));
    assertEquals(0L, FeatureFurnaceBrewBatching.projectFurnaceSkippable(
        new FurnaceHost.FurnaceState("FURNACE", 0, 0, 200, true, false)));
  }

  @Test
  void brewingProjectionNeedsAnIngredientAndABottle() {
    assertEquals(149L, FeatureFurnaceBrewBatching.projectBrewingSkippable(new FurnaceHost.BrewingState(5, 150, true, true)));
    assertEquals(399L, FeatureFurnaceBrewBatching.projectBrewingSkippable(new FurnaceHost.BrewingState(5, 0, true, true)));
    assertEquals(0L, FeatureFurnaceBrewBatching.projectBrewingSkippable(new FurnaceHost.BrewingState(0, 0, true, true)));
    assertEquals(0L, FeatureFurnaceBrewBatching.projectBrewingSkippable(new FurnaceHost.BrewingState(5, 150, false, true)));
  }

  @Test
  void stationKindsMatchBukkitAndNamespacedNames() {
    assertEquals(FurnaceHost.Kind.FURNACE, FurnaceHost.Kind.of("BLAST_FURNACE"));
    assertEquals(FurnaceHost.Kind.FURNACE, FurnaceHost.Kind.of("minecraft:smoker"));
    assertEquals(FurnaceHost.Kind.BREWING_STAND, FurnaceHost.Kind.of("minecraft:brewing_stand"));
    assertNull(FurnaceHost.Kind.of("STONE"));
    assertNull(FurnaceHost.Kind.of(null));
  }

  private static FeatureFurnaceBrewBatching activeFeature() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    setField(feature, "active", true);
    field(feature, "lifecycleGeneration", AtomicLong.class).set(1L);
    return feature;
  }

  private static void engage(FeatureFurnaceBrewBatching feature) throws Exception {
    PressureGate gate = (PressureGate) field(feature, "gate");
    gate.update(1L, true, false, 0L, 0L);
    gate.update(2L, true, false, 0L, 0L);
    assertTrue(gate.isEngaged());
  }

  private static Object field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static <T> T field(Object target, String name, Class<T> type) throws Exception {
    return type.cast(field(target, name));
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
