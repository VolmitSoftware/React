package art.arcane.react.api.test.load;

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
    Mockito.when(world.getMaxHeight()).thenReturn(320);
    Map<Long, FakeBlock> blocks = new HashMap<>();
    Mockito.when(world.getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenAnswer(invocation -> blocks.computeIfAbsent(
        key(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)),
        ignored -> new FakeBlock()).block);
    WorldLoadGenerator generator = new WorldLoadGenerator();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<ItemStack> items = Mockito.mockStatic(ItemStack.class)) {
      bukkit.when(() -> Bukkit.createBlockData(Material.HOPPER))
          .thenAnswer(invocation -> Mockito.mock(Directional.class, Mockito.withSettings().extraInterfaces(BlockData.class)));
      items.when(() -> ItemStack.of(Mockito.any(Material.class), Mockito.anyInt())).thenAnswer(invocation -> Mockito.mock(ItemStack.class));
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
    }

    for (FakeBlock block : blocks.values()) {
      Mockito.verify(block.inventory).clear();
      Mockito.verify(block.original).update(true, false);
      assertSame(block.original, block.current.get());
    }
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
  }
}
