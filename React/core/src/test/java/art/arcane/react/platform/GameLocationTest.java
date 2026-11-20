package art.arcane.react.platform;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class GameLocationTest {
  @Test
  void blockAndChunkCoordinatesFloorNegativePositions() {
    GameLocation location = new GameLocation(UUID.randomUUID(), -0.5D, 64.9D, -16.5D);

    Assertions.assertEquals(-1, location.blockX());
    Assertions.assertEquals(64, location.blockY());
    Assertions.assertEquals(-17, location.blockZ());
    Assertions.assertEquals(-1, location.chunkX());
    Assertions.assertEquals(-2, location.chunkZ());
  }

  @Test
  void distanceIsInfiniteAcrossWorlds() {
    UUID world = UUID.randomUUID();
    GameLocation origin = new GameLocation(world, 0D, 0D, 0D);

    Assertions.assertEquals(25D, origin.distanceSquared(new GameLocation(world, 3D, 0D, 4D)));
    Assertions.assertEquals(Double.POSITIVE_INFINITY, origin.distanceSquared(new GameLocation(UUID.randomUUID(), 0D, 0D, 0D)));
    Assertions.assertEquals(Double.POSITIVE_INFINITY, origin.distanceSquared(null));
  }
}
