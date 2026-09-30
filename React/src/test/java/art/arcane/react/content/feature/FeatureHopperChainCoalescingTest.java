package art.arcane.react.content.feature;

import art.arcane.react.nms.NmsBridges;
import art.arcane.react.testutil.Fakes;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureHopperChainCoalescingTest {
  private static final String BLOCK_PACKAGE_PATH = "org/bukkit/block/";

  @Test
  void savedHopperTicksPerSecondMatchAcrossTickIntervals() throws Exception {
    double atOneSecond = ticksSavedOverOneSecond(1000, 1);
    double atQuarterSecond = ticksSavedOverOneSecond(250, 4);
    double belowClamp = ticksSavedOverOneSecond(100, 4);

    assertEquals(30D, atOneSecond, 1.0E-9);
    assertEquals(atOneSecond, atQuarterSecond, 1.0E-9);
    assertEquals(atOneSecond, belowClamp, 1.0E-9);
  }

  @Test
  void maintenanceTickCounterAdvancesByClampedInterval() throws Exception {
    FeatureHopperChainCoalescing feature = engagedFeature(100);
    try (MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class)) {
      bridges.when(NmsBridges::get).thenReturn(null);
      feature.onTick();
    }

    Field tickCounter = FeatureHopperChainCoalescing.class.getDeclaredField("tickCounter");
    tickCounter.setAccessible(true);
    assertEquals(5L, tickCounter.getLong(feature));
    feature.onDeactivate();
  }

  @Test
  void everyInventoryHolderBlockQueuesRepairWithoutReadingBlockState() throws Exception {
    Set<Material> holders = inventoryHolderBlockMaterials();
    assertTrue(holders.contains(Material.CHEST));
    assertTrue(holders.contains(Material.OAK_SHELF));
    assertFalse(holders.contains(Material.ENDER_CHEST));
    World world = Fakes.world("coalescing-holders");
    Player player = Mockito.mock(Player.class);

    for (Material material : holders) {
      FeatureHopperChainCoalescing feature = new FeatureHopperChainCoalescing();
      try (MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class)) {
        bridges.when(NmsBridges::get).thenReturn(null);
        feature.onActivate();
        Block block = block(world, material);

        feature.on(new BlockBreakEvent(block, player));

        Set<?> queuedRepairs = field(feature, "queuedRepairs", Set.class);
        assertEquals(9, queuedRepairs.size(), material.name());
        Mockito.verify(block, Mockito.never()).getState();
        Mockito.verify(block, Mockito.never()).getState(Mockito.anyBoolean());
        feature.onDeactivate();
      }
    }
  }

  @Test
  void unrelatedBlockSkipsRepairWithoutReadingBlockState() throws Exception {
    World world = Fakes.world("coalescing-stone");
    FeatureHopperChainCoalescing feature = new FeatureHopperChainCoalescing();
    try (MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class)) {
      bridges.when(NmsBridges::get).thenReturn(null);
      feature.onActivate();
      Block block = block(world, Material.STONE);

      feature.on(new BlockBreakEvent(block, Mockito.mock(Player.class)));

      assertTrue(field(feature, "queuedRepairs", Set.class).isEmpty());
      Mockito.verify(block, Mockito.never()).getState();
      Mockito.verify(block, Mockito.never()).getState(Mockito.anyBoolean());
      feature.onDeactivate();
    }
  }

  @Test
  void repairScansOnlyHopperBlockEntitiesWithoutSnapshots() throws Exception {
    FeatureHopperChainCoalescing feature = new FeatureHopperChainCoalescing();
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(world.isChunkLoaded(2, 3)).thenReturn(true);
    Mockito.when(world.getChunkAt(2, 3)).thenReturn(chunk);
    Mockito.when(chunk.getTileEntities()).thenReturn(new BlockState[0]);
    Mockito.when(chunk.getTileEntities(Mockito.<Predicate<? super Block>>any(), Mockito.eq(false)))
        .thenReturn(List.of());

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
         MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class)) {
      bridges.when(NmsBridges::get).thenReturn(null);
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduler.when(() -> J.runChunk(
          Mockito.same(world),
          Mockito.eq(2),
          Mockito.eq(3),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(3).run();
        return true;
      });
      feature.onActivate();

      Method queueRepair = FeatureHopperChainCoalescing.class.getDeclaredMethod(
          "queueRepair",
          nestedClass("ChunkCoordinate"),
          boolean.class
      );
      queueRepair.setAccessible(true);
      queueRepair.invoke(feature, coordinate(worldId, 2, 3), true);
      Method process = FeatureHopperChainCoalescing.class.getDeclaredMethod("processCoordinateRepairs", long.class);
      process.setAccessible(true);
      process.invoke(feature, field(feature, "lifecycleGeneration", AtomicLong.class).get());
      feature.onDeactivate();
    }

    Mockito.verify(chunk).getTileEntities(Mockito.<Predicate<? super Block>>any(), Mockito.eq(false));
    Mockito.verify(chunk, Mockito.never()).getTileEntities();
    Mockito.verify(chunk, Mockito.never()).getTileEntities(Mockito.anyBoolean());
  }

  private static double ticksSavedOverOneSecond(int tickIntervalMs, int ticks) throws Exception {
    FeatureHopperChainCoalescing feature = engagedFeature(tickIntervalMs);
    field(feature, "ticksSavedPerEvaluation", AtomicLong.class).set(30L);
    try (MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class)) {
      bridges.when(NmsBridges::get).thenReturn(null);
      for (int tick = 0; tick < ticks; tick++) {
        feature.onTick();
      }
    }
    double saved = feature.readAndResetTicksSaved();
    feature.onDeactivate();
    return saved;
  }

  private static FeatureHopperChainCoalescing engagedFeature(int tickIntervalMs) throws Exception {
    FeatureHopperChainCoalescing feature = new FeatureHopperChainCoalescing();
    setField(feature, "tickIntervalMS", tickIntervalMs);
    setField(feature, "engageOnTickMs", 0D);
    setField(feature, "releaseOnTickMs", -1D);
    try (MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class)) {
      bridges.when(NmsBridges::get).thenReturn(null);
      feature.onActivate();
    }
    setField(feature, "engaged", true);
    return feature;
  }

  private static Block block(World world, Material material) {
    Block block = Mockito.mock(Block.class);
    Mockito.when(block.getType()).thenReturn(material);
    Mockito.when(block.getWorld()).thenReturn(world);
    Mockito.when(block.getX()).thenReturn(40);
    Mockito.when(block.getY()).thenReturn(64);
    Mockito.when(block.getZ()).thenReturn(-24);
    return block;
  }

  private static Set<Material> inventoryHolderBlockMaterials() throws Exception {
    List<Class<?>> stateTypes = blockStateInterfaces();
    Set<Material> holders = EnumSet.noneOf(Material.class);
    for (Material material : Material.values()) {
      String name = material.name();
      if (name.startsWith("LEGACY_")) {
        continue;
      }
      Class<?> bestType = null;
      int bestLength = -1;
      for (Class<?> type : stateTypes) {
        String snake = snakeCase(type.getSimpleName());
        boolean matches = name.equals(snake) || name.endsWith("_" + snake);
        if (matches && snake.length() > bestLength) {
          bestType = type;
          bestLength = snake.length();
        }
      }
      if (bestType != null && InventoryHolder.class.isAssignableFrom(bestType)) {
        holders.add(material);
      }
    }
    return holders;
  }

  private static List<Class<?>> blockStateInterfaces() throws Exception {
    URL location = BlockState.class.getProtectionDomain().getCodeSource().getLocation();
    List<Class<?>> types = new ArrayList<>();
    try (JarFile jar = new JarFile(new File(location.toURI()))) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        String entry = entries.nextElement().getName();
        if (!entry.startsWith(BLOCK_PACKAGE_PATH)
            || !entry.endsWith(".class")
            || entry.indexOf('/', BLOCK_PACKAGE_PATH.length()) >= 0
            || entry.contains("$")) {
          continue;
        }
        String className = entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
        Class<?> type = Class.forName(className, false, BlockState.class.getClassLoader());
        if (type.isInterface() && type != BlockState.class && BlockState.class.isAssignableFrom(type)) {
          types.add(type);
        }
      }
    }
    return types;
  }

  private static String snakeCase(String simpleName) {
    StringBuilder builder = new StringBuilder(simpleName.length() + 8);
    for (int index = 0; index < simpleName.length(); index++) {
      char character = simpleName.charAt(index);
      if (index > 0 && Character.isUpperCase(character)) {
        builder.append('_');
      }
      builder.append(Character.toUpperCase(character));
    }
    return builder.toString();
  }

  private static Object coordinate(UUID worldId, int chunkX, int chunkZ) throws Exception {
    Constructor<?> constructor = nestedClass("ChunkCoordinate").getDeclaredConstructor(UUID.class, int.class, int.class);
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

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
