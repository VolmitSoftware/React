package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.PressureGate;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.testutil.Fakes;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.nativelib.monitor.BrewingTickHook;
import art.arcane.volmlib.nativelib.monitor.BrewingTickResult;
import art.arcane.volmlib.nativelib.monitor.FurnaceTickHook;
import art.arcane.volmlib.nativelib.monitor.FurnaceTickResult;
import art.arcane.volmlib.nativelib.monitor.NativeMonitor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureFurnaceBrewBatchingTest {
  private final AtomicReference<FurnaceTickHook> furnaceHook = new AtomicReference<>();
  private final AtomicReference<BrewingTickHook> brewingHook = new AtomicReference<>();
  private final AtomicReference<World> playerWorld = new AtomicReference<>();
  private ExecutorService executor;
  private MockedStatic<NmsBridges> bridges;
  private MockedStatic<React> react;

  @BeforeEach
  void setUp() {
    executor = Executors.newSingleThreadExecutor();
    NativeMonitor bridge = Mockito.mock(NativeMonitor.class);
    Mockito.when(bridge.installFurnaceTickHook(Mockito.any())).thenAnswer(invocation -> {
      furnaceHook.set(invocation.getArgument(0));
      return true;
    });
    Mockito.when(bridge.installBrewingTickHook(Mockito.any())).thenAnswer(invocation -> {
      brewingHook.set(invocation.getArgument(0));
      return true;
    });
    bridges = Mockito.mockStatic(NmsBridges.class);
    bridges.when(NmsBridges::get).thenReturn(bridge);
    react = Mockito.mockStatic(React.class);
    react.when(() -> React.hasNearbyPlayer(Mockito.any(Location.class), Mockito.anyDouble()))
        .thenAnswer(invocation -> invocation.<Location>getArgument(0).getWorld() == playerWorld.get());
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    react.close();
    bridges.close();
  }

  @Test
  void skipDebtStaysWithItsWorldAtEqualCoordinates() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    engage(feature);
    World overworld = Fakes.world("furnace-overworld");
    World nether = Fakes.world("furnace-nether");

    for (int tick = 0; tick < 3; tick++) {
      assertTrue(furnaceHook.get().decide(overworld, 10, 64, 10).skip());
      assertTrue(brewingHook.get().decide(overworld, 10, 64, 10).skip());
    }

    playerWorld.set(nether);
    assertSame(FurnaceTickResult.RUN_VANILLA, furnaceHook.get().decide(nether, 10, 64, 10));
    assertSame(BrewingTickResult.RUN_VANILLA, brewingHook.get().decide(nether, 10, 64, 10));

    playerWorld.set(overworld);
    assertEquals(3, furnaceHook.get().decide(overworld, 10, 64, 10).advanceTicks());
    assertEquals(3, brewingHook.get().decide(overworld, 10, 64, 10).advanceTicks());
    feature.onDeactivate();
  }

  @Test
  void hookWithoutOutstandingDebtDoesNotTakeTheDebtLock() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    World world = Fakes.world("furnace-idle");

    Object furnaceLock = field(feature, "furnaceSkipDebt");
    synchronized (furnaceLock) {
      Future<FurnaceTickResult> result = executor.submit(() -> furnaceHook.get().decide(world, 1, 64, 1));
      assertSame(FurnaceTickResult.RUN_VANILLA, result.get(2, TimeUnit.SECONDS));
    }
    Object brewingLock = field(feature, "brewingSkipDebt");
    synchronized (brewingLock) {
      Future<BrewingTickResult> result = executor.submit(() -> brewingHook.get().decide(world, 1, 64, 1));
      assertSame(BrewingTickResult.RUN_VANILLA, result.get(2, TimeUnit.SECONDS));
    }
    feature.onDeactivate();
  }

  @Test
  void breakingUntrackedMaterialSkipsDebtLocksWhileDebtIsOutstanding() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    engage(feature);
    World world = Fakes.world("furnace-break");
    assertTrue(furnaceHook.get().decide(world, 5, 70, 5).skip());
    assertTrue(brewingHook.get().decide(world, 6, 70, 6).skip());
    Block stone = block(world, Material.STONE, 5, 70, 5);
    BlockBreakEvent event = new BlockBreakEvent(stone, Mockito.mock(Player.class));

    Object furnaceLock = field(feature, "furnaceSkipDebt");
    Object brewingLock = field(feature, "brewingSkipDebt");
    synchronized (furnaceLock) {
      synchronized (brewingLock) {
        Future<?> result = executor.submit(() -> feature.on(event));
        result.get(2, TimeUnit.SECONDS);
      }
    }
    Mockito.verify(stone, Mockito.never()).getLocation();

    playerWorld.set(world);
    assertEquals(1, furnaceHook.get().decide(world, 5, 70, 5).advanceTicks());
    feature.onDeactivate();
  }

  @Test
  void chunkLoadQueuesSeedForTheBudgetedTickInsteadOfScanning() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    World world = Fakes.world("furnace-seed");
    Chunk chunk = Fakes.chunk(world, 3, -2);
    Mockito.when(chunk.isLoaded()).thenReturn(true);
    BlockState furnace = Mockito.mock(BlockState.class);
    Mockito.when(furnace.getType()).thenReturn(Material.BLAST_FURNACE);
    Mockito.when(furnace.getX()).thenReturn(50);
    Mockito.when(furnace.getY()).thenReturn(12);
    Mockito.when(furnace.getZ()).thenReturn(-20);
    Mockito.when(furnace.getLocation()).thenReturn(new Location(world, 50, 12, -20));
    Mockito.when(chunk.getTileEntities()).thenReturn(new BlockState[]{furnace});
    Mockito.when(chunk.getTileEntities(Mockito.<Predicate<? super Block>>any(), Mockito.eq(false)))
        .thenReturn(List.of(furnace));
    Mockito.when(world.isChunkLoaded(3, -2)).thenReturn(true);
    Mockito.when(world.getChunkAt(3, -2)).thenReturn(chunk);
    List<Runnable> scheduled = new ArrayList<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(world.getUID())).thenReturn(world);
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.eq(3), Mockito.eq(-2), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> scheduled.add(invocation.getArgument(3)));

      feature.on(new ChunkLoadEvent(chunk, false));
      Mockito.verify(chunk, Mockito.never()).getTileEntities();
      Mockito.verify(chunk, Mockito.never()).getTileEntities(Mockito.<Predicate<? super Block>>any(), Mockito.anyBoolean());

      feature.onTick();
      assertEquals(1, scheduled.size());
      scheduled.getFirst().run();
    }

    assertEquals(1L, field(feature, "trackedBlockCount", AtomicLong.class).get());
    Mockito.verify(chunk, Mockito.never()).getTileEntities();
    feature.onDeactivate();
  }

  @Test
  void chunkLoadBurstLeavesAQuarterOfTheReseedBudgetForTheLoadedChunkRotation() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    World world = Fakes.world("furnace-churn");
    for (int chunkX = 0; chunkX < 40; chunkX++) {
      feature.on(new ChunkLoadEvent(Fakes.chunk(world, chunkX, 0), true));
    }
    List<ObserverController.LoadedChunkTarget> rotation = new ArrayList<>();
    for (int chunkX = 0; chunkX < 8; chunkX++) {
      rotation.add(new ObserverController.LoadedChunkTarget(world.getUID(), chunkX, 100));
    }
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(8)).thenReturn(rotation);
    react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
    List<Integer> loadSeeds = new ArrayList<>();
    List<Integer> rotationSeeds = new ArrayList<>();
    React previous = React.instance;
    React.instance = Mockito.mock(React.class);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(world.getUID())).thenReturn(world);
      scheduler.when(() -> J.runChunk(Mockito.same(world), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> invocation.<Integer>getArgument(2) == 0
              ? loadSeeds.add(invocation.getArgument(1))
              : rotationSeeds.add(invocation.getArgument(1)));

      feature.onTick();
    } finally {
      React.instance = previous;
    }

    assertEquals(24, loadSeeds.size());
    assertEquals(8, rotationSeeds.size());
    feature.onDeactivate();
  }

  @Test
  void inventoryMovesSkipLocationLookupsForNonFurnaceInventories() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    feature.onActivate();
    World world = Fakes.world("furnace-moves");
    Inventory hopper = inventory(Inventory.class, new Location(world, 0, 64, 0));
    Inventory chest = inventory(Inventory.class, new Location(world, 0, 63, 0));
    Inventory smoker = inventory(FurnaceInventory.class, new Location(world, 1, 63, 0));
    Inventory brewer = inventory(BrewerInventory.class, new Location(world, 2, 63, 0));
    ItemStack stack = Mockito.mock(ItemStack.class);

    feature.on(new InventoryMoveItemEvent(hopper, stack, chest, true));
    Mockito.verify(hopper, Mockito.never()).getLocation();
    Mockito.verify(chest, Mockito.never()).getLocation();
    assertEquals(0L, field(feature, "trackedBlockCount", AtomicLong.class).get());

    feature.on(new InventoryMoveItemEvent(hopper, stack, smoker, true));
    feature.on(new InventoryMoveItemEvent(brewer, stack, hopper, true));
    assertEquals(2L, field(feature, "trackedBlockCount", AtomicLong.class).get());
    assertFalse(field(feature, "chunkIndexByKey", Map.class).isEmpty());
    feature.onDeactivate();
  }

  private static void engage(FeatureFurnaceBrewBatching feature) throws Exception {
    PressureGate gate = (PressureGate) field(feature, "gate");
    gate.update(1L, true, false, 0L, 0L);
    gate.update(2L, true, false, 0L, 0L);
    assertTrue(gate.isEngaged());
  }

  private static Block block(World world, Material material, int x, int y, int z) {
    Block block = Mockito.mock(Block.class);
    Mockito.when(block.getType()).thenReturn(material);
    Mockito.when(block.getWorld()).thenReturn(world);
    Mockito.when(block.getX()).thenReturn(x);
    Mockito.when(block.getY()).thenReturn(y);
    Mockito.when(block.getZ()).thenReturn(z);
    Mockito.when(block.getLocation()).thenReturn(new Location(world, x, y, z));
    return block;
  }

  private static Inventory inventory(Class<? extends Inventory> type, Location location) {
    Inventory inventory = Mockito.mock(type);
    Mockito.when(inventory.getLocation()).thenReturn(location);
    return inventory;
  }

  private static Object field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static <T> T field(Object target, String name, Class<T> type) throws Exception {
    return type.cast(field(target, name));
  }
}
