package art.arcane.react.platform.timing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTickTimesTest {
  private static final UUID OVERWORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID NETHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Test
  void measuredWorldAveragesNanosPerTick() {
    WorldTickTimes.World world = WorldTickTimes.World.measured(OVERWORLD, "minecraft:overworld", 20, 40_000_000L, 6_500_000L);
    assertEquals(2D, world.meanMs(), 1.0E-9D);
    assertEquals(6.5D, world.maxMs(), 1.0E-9D);
  }

  @Test
  void worldWithoutTicksReportsZero() {
    WorldTickTimes.World world = WorldTickTimes.World.measured(OVERWORLD, null, 0, 0L, 0L);
    assertEquals(0D, world.meanMs());
    assertEquals("", world.key());
  }

  @Test
  void measuresOnlyOnceATickWasTimed() {
    WorldTickTimes times = new WorldTickTimes();
    times.publish(List.of(WorldTickTimes.World.measured(OVERWORLD, "minecraft:overworld", 0, 0L, 0L)));
    assertFalse(times.measuring(), "no timed tick yet");
    times.publish(List.of(WorldTickTimes.World.measured(OVERWORLD, "minecraft:overworld", 20, 20_000_000L, 1_000_000L)));
    assertTrue(times.measuring());
    times.publish(List.of(WorldTickTimes.World.measured(OVERWORLD, "minecraft:overworld", 0, 0L, 0L)));
    assertTrue(times.measuring(), "a paused server keeps its measured source");
  }

  @Test
  void publishDropsWorldsThatUnloaded() {
    WorldTickTimes times = new WorldTickTimes();
    times.publish(List.of(
        WorldTickTimes.World.measured(OVERWORLD, "minecraft:overworld", 20, 20_000_000L, 1_000_000L),
        WorldTickTimes.World.measured(NETHER, "minecraft:the_nether", 20, 60_000_000L, 9_000_000L)));
    assertEquals(3D, times.world(NETHER).orElseThrow().meanMs(), 1.0E-9D);
    times.publish(List.of(WorldTickTimes.World.measured(OVERWORLD, "minecraft:overworld", 20, 20_000_000L, 1_000_000L)));
    assertTrue(times.world(NETHER).isEmpty());
    assertEquals(1, times.worlds().size());
  }
}
