package art.arcane.react.model;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class CircuitWorldKeyTest {
  @Test
  void negativeCoordinatesLinkAcrossEveryAxisBoundary() {
    CircuitWorld world = new CircuitWorld(UUID.randomUUID(), "world");
    world.event(-1, -64, -1, 1000L);
    world.event(0, -64, -1, 1001L);
    world.event(0, -64, 0, 1002L);
    world.event(0, -65, 0, 1003L);
    world.event(0, -1, 0, 1004L);
    world.event(0, 0, 0, 1005L);

    Assertions.assertEquals(2, world.countCircuits());
    Assertions.assertEquals(6, world.countBlocks());
    Assertions.assertTrue(world.isConsistent());

    world.event(0, -63, 0, 1006L);
    for (int y = -62; y <= -2; y++) {
      world.event(0, y, 0, 1007L);
    }

    Assertions.assertEquals(1, world.countCircuits());
    Assertions.assertTrue(world.isConsistent());
  }

  @Test
  void worldBorderCoordinatesStayDistinct() {
    CircuitWorld world = new CircuitWorld(UUID.randomUUID(), "world");
    world.event(29_999_999, 319, -29_999_999, 1000L);
    world.event(-29_999_999, 319, 29_999_999, 1000L);
    world.event(29_999_999, 320, -29_999_999, 1000L);
    world.rollWindow(1500L, 15_000L);

    Assertions.assertEquals(2, world.countCircuits());
    CircuitSnapshot worst = world.worst(1500L);
    Assertions.assertNotNull(worst);
    Assertions.assertEquals(2, worst.nodes());
    Assertions.assertEquals(29_999_999, worst.minX());
    Assertions.assertEquals(-29_999_999, worst.minZ());
    Assertions.assertEquals(319, worst.minY());
    Assertions.assertEquals(320, worst.maxY());
  }

  @Test
  void removalSplitsPackedPositionsBackIntoComponents() {
    CircuitWorld world = new CircuitWorld(UUID.randomUUID(), "world");
    world.event(-2, 70, 5, 1000L);
    world.event(-1, 70, 5, 1000L);
    world.event(0, 70, 5, 1000L);

    world.remove(-1, 70, 5, 1100L);

    Assertions.assertEquals(2, world.countCircuits());
    Assertions.assertEquals(2, world.countBlocks());
    Assertions.assertTrue(world.isConsistent());
    world.remove(-1, 70, 5, 1200L);
    Assertions.assertEquals(2, world.countBlocks());
  }

  @Test
  void serverKeysWorldsByIdentifierWithoutFormattingItPerTransition() throws ReflectiveOperationException {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getName()).thenReturn("world");
    CircuitServer server = new CircuitServer();

    for (int index = 0; index < 32; index++) {
      server.event(block(world, index, 64, 0), 1000L + index);
    }
    server.remove(block(world, 3, 64, 0), 2000L);

    Field worldsField = CircuitServer.class.getDeclaredField("circuitWorlds");
    worldsField.setAccessible(true);
    Map<?, ?> worlds = (Map<?, ?>) worldsField.get(server);
    Assertions.assertEquals(Set.of(worldId), worlds.keySet());
    Mockito.verify(world, Mockito.times(1)).getName();
    Assertions.assertEquals(2, server.countCircuits());
    Assertions.assertEquals(31, server.countBlocks());
  }

  @Test
  void sameNamedWorldsStaySeparateAndSnapshotsCarryTheirIdentifier() {
    World first = world("world");
    World second = world("world");
    CircuitServer server = new CircuitServer();

    server.event(block(first, 0, 64, 0), 1000L);
    server.event(block(first, 1, 64, 0), 1000L);
    server.event(block(second, 0, 64, 0), 1000L);
    server.rollWindow(1500L, 15_000L);

    Assertions.assertEquals(2, server.countCircuits());
    CircuitSnapshot throttled = server.throttleWorst(1500L, 10_000L);
    Assertions.assertNotNull(throttled);
    Assertions.assertEquals(first.getUID().toString(), throttled.worldId());
    Assertions.assertEquals(2, throttled.nodes());
    Assertions.assertTrue(server.event(block(first, 0, 64, 0), 1600L).blocked());
    Assertions.assertFalse(server.event(block(second, 0, 64, 0), 1600L).blocked());
  }

  private static World world(String name) {
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getName()).thenReturn(name);
    return world;
  }

  private static Block block(World world, int x, int y, int z) {
    Block block = Mockito.mock(Block.class);
    Mockito.when(block.getWorld()).thenReturn(world);
    Mockito.when(block.getX()).thenReturn(x);
    Mockito.when(block.getY()).thenReturn(y);
    Mockito.when(block.getZ()).thenReturn(z);
    return block;
  }
}
