package art.arcane.react.util.project.world;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class BlockEntityScan {
  private static final BlockEntityScan RUNTIME = probe(Chunk.class);

  private final boolean predicateScan;

  private BlockEntityScan(boolean predicateScan) {
    this.predicateScan = predicateScan;
  }

  public static BlockEntityScan runtime() {
    return RUNTIME;
  }

  static BlockEntityScan probe(Class<?> chunkType) {
    try {
      chunkType.getMethod("getTileEntities", Predicate.class, boolean.class);
      return new BlockEntityScan(true);
    } catch (NoSuchMethodException missing) {
      return new BlockEntityScan(false);
    }
  }

  public Collection<BlockState> scan(Chunk chunk, Set<Material> materials) {
    if (predicateScan) {
      return chunk.getTileEntities(block -> materials.contains(block.getType()), false);
    }
    BlockState[] states = chunk.getTileEntities();
    List<BlockState> matches = new ArrayList<>();
    for (BlockState state : states) {
      if (materials.contains(state.getType())) {
        matches.add(state);
      }
    }
    return matches;
  }
}
