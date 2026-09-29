package art.arcane.react.api.test.load;

import art.arcane.react.React;
import art.arcane.react.testutil.Fakes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class WorldLoadGeneratorTest {
  private static final int[][] RING = {{0, 0}, {1, 0}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {0, 1}};
  private static final BlockFace[] RING_FACING = {
      BlockFace.EAST, BlockFace.EAST, BlockFace.SOUTH, BlockFace.SOUTH,
      BlockFace.WEST, BlockFace.WEST, BlockFace.NORTH, BlockFace.NORTH
  };

  @Test
  void hopperNetworksAreFedClosedRingsThatEndRestores() {
    World world = Fakes.world("world");
    Map<Long, FakeBlock> blocks = blocksOf(world);
    WorldLoadGenerator generator = new WorldLoadGenerator();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<ItemStack> items = Mockito.mockStatic(ItemStack.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      stubHopperCreation(bukkit, items);
      generator.begin(world, new Location(world, 21D, 63D, 12D), new LoadProfile(0, 0, 2, 0, 0, 0, 0));

      assertEquals(16, blocks.size());
      for (int network = 0; network < 2; network++) {
        for (int i = 0; i < RING.length; i++) {
          FakeBlock placed = blocks.get(key(53 + network * 4 + RING[i][0], 71, 12 + RING[i][1]));
          Mockito.verify((Directional) placed.placedData).setFacing(RING_FACING[i]);
          Mockito.verify(placed.inventory).addItem(Mockito.any(ItemStack.class));
        }
      }

      generator.end();
      react.verify(() -> React.reportError(Mockito.anyString(), Mockito.any()), Mockito.never());
      react.verify(() -> React.warn(Mockito.anyString()), Mockito.never());
    }

    for (FakeBlock block : blocks.values()) {
      Mockito.verify(block.inventory).clear();
      Mockito.verify(block.original).update(true, false);
      assertSame(block.original, block.current.get());
    }
  }

  @Test
  void placementFailuresAreReportedOncePerPassWithThePlacedCount() {
    World world = Fakes.world("world");
    Map<Long, FakeBlock> blocks = blocksOf(world);
    IllegalStateException failure = new IllegalStateException("region not owned");
    blocks.computeIfAbsent(key(53, 71, 12), ignored -> new FakeBlock()).failPlacement(failure);
    blocks.computeIfAbsent(key(57, 71, 12), ignored -> new FakeBlock()).failPlacement(new IllegalStateException("region not owned"));
    WorldLoadGenerator generator = new WorldLoadGenerator();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<ItemStack> items = Mockito.mockStatic(ItemStack.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      stubHopperCreation(bukkit, items);
      generator.begin(world, new Location(world, 21D, 63D, 12D), new LoadProfile(0, 0, 2, 0, 0, 0, 0));

      react.verify(() -> React.reportError(Mockito.anyString(), Mockito.any()), Mockito.times(1));
      react.verify(() -> React.reportError(Mockito.contains("placed 14/16 hoppers at 53,71,12 in world"), Mockito.same(failure)));
      generator.end();
    }
  }

  @Test
  void hoppersThatDoNotBecomeContainersAreReportedAsAShortfall() {
    World world = Fakes.world("world");
    Map<Long, FakeBlock> blocks = blocksOf(world);
    blocks.computeIfAbsent(key(55, 71, 13), ignored -> new FakeBlock()).placeWithoutContainer();
    WorldLoadGenerator generator = new WorldLoadGenerator();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<ItemStack> items = Mockito.mockStatic(ItemStack.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      stubHopperCreation(bukkit, items);
      generator.begin(world, new Location(world, 21D, 63D, 12D), new LoadProfile(0, 0, 2, 0, 0, 0, 0));

      react.verify(() -> React.warn(Mockito.contains("placed 15/16 hoppers at 53,71,12 in world")), Mockito.times(1));
      react.verify(() -> React.reportError(Mockito.anyString(), Mockito.any()), Mockito.never());
      generator.end();
    }
  }

  @Test
  void restoreFailuresAreReportedWithCoordinatesAndTheRemainingBlocksStillRestore() {
    World world = Fakes.world("world");
    Map<Long, FakeBlock> blocks = blocksOf(world);
    IllegalStateException failure = new IllegalStateException("chunk unloaded");
    FakeBlock broken = blocks.computeIfAbsent(key(54, 71, 12), ignored -> new FakeBlock());
    broken.failRestore(failure, 54, 71, 12);
    WorldLoadGenerator generator = new WorldLoadGenerator();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<ItemStack> items = Mockito.mockStatic(ItemStack.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      stubHopperCreation(bukkit, items);
      generator.begin(world, new Location(world, 21D, 63D, 12D), new LoadProfile(0, 0, 1, 0, 0, 0, 0));
      generator.end();

      react.verify(() -> React.reportError(Mockito.contains("restore block at 54,71,12 in world"), Mockito.same(failure)), Mockito.times(1));
      react.verify(() -> React.reportError(Mockito.anyString(), Mockito.any()), Mockito.times(1));
    }

    for (FakeBlock block : blocks.values()) {
      if (block == broken) {
        continue;
      }
      Mockito.verify(block.original).update(true, false);
      assertSame(block.original, block.current.get());
    }
  }

  private static Map<Long, FakeBlock> blocksOf(World world) {
    Mockito.when(world.getMaxHeight()).thenReturn(320);
    Map<Long, FakeBlock> blocks = new HashMap<>();
    Mockito.when(world.getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenAnswer(invocation -> blocks.computeIfAbsent(
        key(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)),
        ignored -> new FakeBlock()).block);
    return blocks;
  }

  private static void stubHopperCreation(MockedStatic<Bukkit> bukkit, MockedStatic<ItemStack> items) {
    bukkit.when(() -> Bukkit.createBlockData(Material.HOPPER))
        .thenAnswer(invocation -> Mockito.mock(Directional.class, Mockito.withSettings().extraInterfaces(BlockData.class)));
    items.when(() -> ItemStack.of(Mockito.any(Material.class), Mockito.anyInt())).thenAnswer(invocation -> Mockito.mock(ItemStack.class));
  }

  private static long key(int x, int y, int z) {
    return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
  }

  private static final class FakeBlock {
    private final Block block = Mockito.mock(Block.class);
    private final BlockState original = Mockito.mock(BlockState.class);
    private final Container hopper = Mockito.mock(Container.class);
    private final Inventory inventory = Mockito.mock(Inventory.class);
    private final AtomicReference<BlockState> current = new AtomicReference<>(original);
    private BlockData placedData;

    private FakeBlock() {
      Mockito.when(block.getState()).thenAnswer(invocation -> current.get());
      Mockito.doAnswer(invocation -> {
        placedData = invocation.getArgument(0);
        current.set(hopper);
        return null;
      }).when(block).setBlockData(Mockito.any(BlockData.class), Mockito.eq(false));
      Mockito.when(hopper.getInventory()).thenReturn(inventory);
      Mockito.when(original.getBlock()).thenReturn(block);
      Mockito.when(original.update(true, false)).thenAnswer(invocation -> {
        current.set(original);
        return true;
      });
    }

    private void failPlacement(RuntimeException failure) {
      Mockito.doThrow(failure).when(block).setBlockData(Mockito.any(BlockData.class), Mockito.eq(false));
    }

    private void placeWithoutContainer() {
      BlockState plain = Mockito.mock(BlockState.class);
      Mockito.doAnswer(invocation -> {
        placedData = invocation.getArgument(0);
        current.set(plain);
        return null;
      }).when(block).setBlockData(Mockito.any(BlockData.class), Mockito.eq(false));
    }

    private void failRestore(RuntimeException failure, int x, int y, int z) {
      Mockito.when(original.getX()).thenReturn(x);
      Mockito.when(original.getY()).thenReturn(y);
      Mockito.when(original.getZ()).thenReturn(z);
      Mockito.when(original.update(true, false)).thenThrow(failure);
    }
  }
}
