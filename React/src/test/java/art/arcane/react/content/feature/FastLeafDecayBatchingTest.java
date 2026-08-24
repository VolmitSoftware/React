package art.arcane.react.content.feature;

import art.arcane.react.util.common.scheduling.J;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FastLeafDecayBatchingTest {
  @Test
  void partitionsAChunkBoundaryScanIntoOwnedChunkBatches() {
    FeatureFastLeafDecay feature = new FeatureFastLeafDecay();
    World world = Mockito.mock(World.class);
    FeatureFastLeafDecay.IBlock root = new FeatureFastLeafDecay.IBlock(world, 15, 64, 15);
    Map<FeatureFastLeafDecay.IChunk, List<FeatureFastLeafDecay.IBlock>> batches = new HashMap<>();

    feature.indexRootChunks(batches, root);

    Assertions.assertEquals(4, batches.size());
    Assertions.assertTrue(batches.values().stream().allMatch(roots -> roots.equals(List.of(root))));
  }

  @Test
  void scansTheCompleteSymmetricRadiusWithoutSharedLoopState() throws ReflectiveOperationException {
    FeatureFastLeafDecay feature = new FeatureFastLeafDecay();
    setDouble(feature, "maxSyncSpikeMS", 60_000D);
    World world = Mockito.mock(World.class);
    Mockito.when(world.getMinHeight()).thenReturn(-64);
    Mockito.when(world.getMaxHeight()).thenReturn(320);
    ChunkSnapshot snapshot = Mockito.mock(ChunkSnapshot.class);
    BlockData data = Mockito.mock(BlockData.class);
    Mockito.when(snapshot.getBlockData(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(data);
    FeatureFastLeafDecay.IBlock root = new FeatureFastLeafDecay.IBlock(world, 8, 64, 8);
    FeatureFastLeafDecay.IChunk chunk = new FeatureFastLeafDecay.IChunk(world, 0, 0);

    boolean complete = feature.scanRootInChunk(
        root,
        chunk,
        snapshot,
        new LongOpenHashSet(),
        new FeatureFastLeafDecay.SyncBudget(60_000D),
        0L
    );

    Assertions.assertTrue(complete);
    Mockito.verify(snapshot, Mockito.times(1331)).getBlockData(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
  }

  @Test
  void usesNativeLocalizedSoundInsteadOfPlayerFanout() throws ReflectiveOperationException {
    FeatureFastLeafDecay feature = new FeatureFastLeafDecay();
    setDouble(feature, "soundChance", 2D);
    setBoolean(feature, "fastBlockChanges", false);
    World world = Mockito.mock(World.class);
    Block block = Mockito.mock(Block.class);
    Leaves leaves = Mockito.mock(Leaves.class);
    Location location = Mockito.mock(Location.class);
    Mockito.when(world.getBlockAt(1, 64, 2)).thenReturn(block);
    Mockito.when(block.getWorld()).thenReturn(world);
    Mockito.when(block.getLocation()).thenReturn(location);
    Mockito.when(block.getBlockData()).thenReturn(leaves);
    Mockito.when(leaves.isPersistent()).thenReturn(false);
    Mockito.when(leaves.getDistance()).thenReturn(7);

    feature.addBlockForDecay(new FeatureFastLeafDecay.IBlock(world, 1, 64, 2), leaves);

    Mockito.verify(world).playSound(location, "minecraft:block.azalea_leaves.fall", 0.26F, 0.2F);
    Mockito.verify(world, Mockito.never()).getPlayers();
    Mockito.verify(block).breakNaturally();
  }

  @Test
  void syncBudgetIsSharedAcrossChunkTasks() {
    FeatureFastLeafDecay.SyncBudget budget = new FeatureFastLeafDecay.SyncBudget(1D);

    Assertions.assertTrue(budget.charge(600_000L));
    Assertions.assertFalse(budget.charge(500_000L));
  }

  @Test
  void deactivatedFeatureRejectsNewDecayRoots() {
    FeatureFastLeafDecay feature = new FeatureFastLeafDecay();
    World world = Mockito.mock(World.class);
    Block block = Mockito.mock(Block.class);
    Mockito.when(block.getWorld()).thenReturn(world);
    Mockito.when(block.getX()).thenReturn(1);
    Mockito.when(block.getY()).thenReturn(64);
    Mockito.when(block.getZ()).thenReturn(2);
    feature.onActivate();
    feature.onDeactivate();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      feature.decay(block);
      feature.onTick();

      scheduler.verifyNoInteractions();
    }
  }

  private static void setDouble(FeatureFastLeafDecay feature, String name, double value) throws ReflectiveOperationException {
    Field field = FeatureFastLeafDecay.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setDouble(feature, value);
  }

  private static void setBoolean(FeatureFastLeafDecay feature, String name, boolean value) throws ReflectiveOperationException {
    Field field = FeatureFastLeafDecay.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setBoolean(feature, value);
  }
}
