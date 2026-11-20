package art.arcane.react.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** The block subject of a sampler counter on a mod server, so its count can be attributed to a chunk. */
public record ModBlockRef(ServerLevel level, BlockPos pos) {
}
