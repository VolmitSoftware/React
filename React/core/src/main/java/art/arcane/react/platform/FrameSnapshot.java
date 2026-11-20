package art.arcane.react.platform;

import java.util.UUID;

public record FrameSnapshot<I>(
    UUID frameId,
    UUID worldId,
    int blockX,
    int blockY,
    int blockZ,
    double x,
    double y,
    double z,
    Facing facing,
    int rotation,
    I item,
    Object handle
) {
  public FrameSnapshot<I> withItem(I replacement) {
    return new FrameSnapshot<I>(frameId, worldId, blockX, blockY, blockZ, x, y, z, facing, rotation, replacement, handle);
  }
}
