package art.arcane.react.platform;

import java.util.UUID;

/**
 * A position in a loaded world, by world id.
 */
public record GameLocation(UUID worldId, double x, double y, double z) {
  public int blockX() {
    return (int) Math.floor(x);
  }

  public int blockY() {
    return (int) Math.floor(y);
  }

  public int blockZ() {
    return (int) Math.floor(z);
  }

  public int chunkX() {
    return blockX() >> 4;
  }

  public int chunkZ() {
    return blockZ() >> 4;
  }

  public double distanceSquared(GameLocation other) {
    if (other == null || !worldId.equals(other.worldId)) {
      return Double.POSITIVE_INFINITY;
    }
    double dx = x - other.x;
    double dy = y - other.y;
    double dz = z - other.z;
    return dx * dx + dy * dy + dz * dz;
  }
}
