package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.nms.NmsBridges;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

class FeatureLazyGravityPhysicsTest {
  private MockedStatic<React> react;
  private MockedStatic<NmsBridges> bridges;
  private World world;
  private FeatureLazyGravity feature;

  @BeforeEach
  void setUp() {
    react = Mockito.mockStatic(React.class);
    bridges = Mockito.mockStatic(NmsBridges.class);
    react.when(() -> React.hasNearbyPlayer(Mockito.any(Location.class), Mockito.anyDouble())).thenReturn(false);
    bridges.when(NmsBridges::get).thenReturn(null);
    world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(world.getMinHeight()).thenReturn(-64);
    Mockito.when(world.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt())).thenReturn(true);
    Block air = block(Material.AIR, 0, 0, 0);
    Block ground = block(Material.STONE, 0, 0, 0);
    Mockito.when(world.getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenAnswer(invocation ->
        invocation.<Integer>getArgument(1) > 60 ? air : ground);
    feature = new FeatureLazyGravity();
    feature.onActivate();
  }

  @AfterEach
  void tearDown() {
    feature.onDeactivate();
    bridges.close();
    react.close();
  }

  @Test
  void physicsUpdatesReturnBeforeTouchingTheBlockWhileNothingIsTracked() {
    Block block = block(Material.REDSTONE_WIRE, 3, 64, 3);

    feature.on(physics(block));

    Mockito.verifyNoInteractions(block);
  }

  @Test
  void invalidatedColumnsLeaveTheTrackedTaskCount() {
    feature.on(fallingBlockSpawn(10, 100, 10));
    Assertions.assertEquals(1L, feature.activeTaskCount());

    feature.on(physics(block(Material.SAND, 10, 80, 10)));

    Assertions.assertEquals(0L, feature.activeTaskCount());
    Block elsewhere = block(Material.REDSTONE_WIRE, 11, 80, 10);
    feature.on(physics(elsewhere));
    Mockito.verifyNoInteractions(elsewhere);
  }

  @Test
  void unrelatedColumnsKeepTheirTrackedTask() {
    feature.on(fallingBlockSpawn(10, 100, 10));

    feature.on(physics(block(Material.REDSTONE_WIRE, 11, 80, 10)));
    feature.on(physics(block(Material.REDSTONE_WIRE, 10, 80, 11)));

    Assertions.assertEquals(1L, feature.activeTaskCount());
  }

  @Test
  void manyTrackedColumnsInvalidateIndependently() {
    for (int x = 0; x < 256; x++) {
      feature.on(fallingBlockSpawn(x * 3, 100, -x));
    }
    Assertions.assertEquals(256L, feature.activeTaskCount());

    for (int x = 0; x < 256; x += 2) {
      feature.on(physics(block(Material.SAND, x * 3, 70, -x)));
    }

    Assertions.assertEquals(128L, feature.activeTaskCount());
  }

  private EntityChangeBlockEvent fallingBlockSpawn(int x, int y, int z) {
    FallingBlock falling = Mockito.mock(FallingBlock.class);
    Mockito.when(falling.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(falling.getLocation()).thenReturn(new Location(world, x + 0.5D, y, z + 0.5D));
    Mockito.when(falling.getVelocity()).thenReturn(new Vector());
    EntityChangeBlockEvent event = Mockito.mock(EntityChangeBlockEvent.class);
    Mockito.when(event.getEntity()).thenReturn(falling);
    return event;
  }

  private Block block(Material type, int x, int y, int z) {
    Block block = Mockito.mock(Block.class);
    Mockito.when(block.getType()).thenReturn(type);
    Mockito.when(block.getWorld()).thenAnswer(invocation -> world);
    Mockito.when(block.getX()).thenReturn(x);
    Mockito.when(block.getY()).thenReturn(y);
    Mockito.when(block.getZ()).thenReturn(z);
    return block;
  }

  private BlockPhysicsEvent physics(Block block) {
    BlockPhysicsEvent event = Mockito.mock(BlockPhysicsEvent.class);
    Mockito.when(event.getBlock()).thenReturn(block);
    return event;
  }
}
