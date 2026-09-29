package art.arcane.react.util.project.world;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockEntityScanTest {

  @Test
  void fallsBackToFilteredSnapshotScanWhenPredicateScanIsAbsent() {
    Chunk chunk = Mockito.mock(Chunk.class);
    BlockState hopper = state(Material.HOPPER);
    BlockState chest = state(Material.CHEST);
    BlockState furnace = state(Material.FURNACE);
    Mockito.when(chunk.getTileEntities()).thenReturn(new BlockState[]{hopper, chest, furnace});

    Collection<BlockState> matches = BlockEntityScan.probe(LegacyChunk.class)
        .scan(chunk, EnumSet.of(Material.HOPPER, Material.FURNACE));

    assertEquals(List.of(hopper, furnace), List.copyOf(matches));
    Mockito.verify(chunk, Mockito.never()).getTileEntities(
        Mockito.<Predicate<? super Block>>any(),
        Mockito.anyBoolean());
  }

  @Test
  void usesLivePredicateScanWhenAvailable() {
    Chunk chunk = Mockito.mock(Chunk.class);
    BlockState hopper = state(Material.HOPPER);
    AtomicReference<Predicate<? super Block>> predicate = new AtomicReference<>();
    Mockito.when(chunk.getTileEntities(Mockito.<Predicate<? super Block>>any(), Mockito.eq(false)))
        .thenAnswer(invocation -> {
          predicate.set(invocation.getArgument(0));
          return List.of(hopper);
        });

    Collection<BlockState> matches = BlockEntityScan.probe(Chunk.class).scan(chunk, EnumSet.of(Material.HOPPER));

    assertEquals(List.of(hopper), List.copyOf(matches));
    assertTrue(predicate.get().test(block(Material.HOPPER)));
    assertFalse(predicate.get().test(block(Material.CHEST)));
    Mockito.verify(chunk, Mockito.never()).getTileEntities();
    Mockito.verify(chunk, Mockito.never()).getTileEntities(Mockito.anyBoolean());
  }

  @Test
  void runtimeScanUsesPredicateScanOnPaperApi() {
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(chunk.getTileEntities(Mockito.<Predicate<? super Block>>any(), Mockito.eq(false)))
        .thenReturn(List.of());

    BlockEntityScan.runtime().scan(chunk, EnumSet.of(Material.HOPPER));

    Mockito.verify(chunk).getTileEntities(Mockito.<Predicate<? super Block>>any(), Mockito.eq(false));
    Mockito.verify(chunk, Mockito.never()).getTileEntities();
  }

  private static BlockState state(Material material) {
    BlockState state = Mockito.mock(BlockState.class);
    Mockito.when(state.getType()).thenReturn(material);
    return state;
  }

  private static Block block(Material material) {
    Block block = Mockito.mock(Block.class);
    Mockito.when(block.getType()).thenReturn(material);
    return block;
  }

  private interface LegacyChunk {
    BlockState[] getTileEntities();
  }
}
