package art.arcane.react.platform.bukkit;

import art.arcane.react.nms.NmsBridges;
import art.arcane.react.platform.ExplosionHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.nativelib.monitor.NativeMonitor;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class BukkitExplosionHostTest {
  @Test
  void nonTntSpawnsDoNotReachPrimedListeners() {
    AtomicInteger primed = new AtomicInteger();
    TNTPrimed tnt = Mockito.mock(TNTPrimed.class);
    Mockito.when(tnt.getFuseTicks()).thenReturn(80);

    BukkitExplosionHost.primed(spawnEvent(Mockito.mock(Entity.class)), event -> primed.incrementAndGet());
    BukkitExplosionHost.primed(spawnEvent(tnt), event -> {
      primed.incrementAndGet();
      event.setFuseTicks(event.fuseTicks() + 7);
    });

    Assertions.assertEquals(1, primed.get());
    Mockito.verify(tnt).setFuseTicks(87);
  }

  @Test
  void entityExplosionResultsMapToDestroysBlocksWithoutReadingTheBlockList() {
    for (ExplosionResult result : ExplosionResult.values()) {
      EntityExplodeEvent event = explosionEvent(new ArrayList<>(), result);

      ExplosionHost.Explosion explosion = BukkitExplosionHost.entityExplosion(event);

      boolean destroys = result == ExplosionResult.DESTROY || result == ExplosionResult.DESTROY_WITH_DECAY;
      Assertions.assertEquals(destroys, explosion.destroysBlocks());
      Assertions.assertFalse(explosion.fromBlock());
      Mockito.verify(event, Mockito.never()).blockList();
      Mockito.verify(event, Mockito.never()).getYield();
    }
  }

  @Test
  void removedBlocksLeaveTheBukkitBlockList() {
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(world.getUID()).thenReturn(worldId);
    Block tnt = block(world, 0, 64, 0, Material.TNT);
    Block stone = block(world, 1, 64, 0, Material.STONE);
    List<Block> blocks = new ArrayList<>(List.of(tnt, stone));
    ExplosionHost.Explosion explosion = BukkitExplosionHost.entityExplosion(explosionEvent(blocks, ExplosionResult.DESTROY));

    Assertions.assertEquals(List.of(
        new ExplosionHost.ExplodedBlock(new GameLocation(worldId, 0, 64, 0), "TNT"),
        new ExplosionHost.ExplodedBlock(new GameLocation(worldId, 1, 64, 0), "STONE")
    ), explosion.blocks());

    explosion.removeBlocks(block -> block.type().equals("STONE"));

    Assertions.assertEquals(List.of(tnt), blocks);
  }

  @Test
  void foliaKeepsPacketBatchingMeasurementOnly() {
    NativeMonitor bridge = Mockito.mock(NativeMonitor.class);
    Mockito.when(bridge.installExplosionHook(Mockito.any())).thenReturn(true);
    AtomicBoolean observing = new AtomicBoolean();
    AtomicBoolean suppressing = new AtomicBoolean(true);

    try (MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bridges.when(NmsBridges::get).thenReturn(bridge);
      scheduler.when(J::isFoliaThreading).thenReturn(true);

      new BukkitExplosionHost().installPacketHooks(new ExplosionHost.PacketHooks() {
        @Override
        public void observe(GameLocation center) {
        }

        @Override
        public boolean suppress(UUID worldId, double x, double y, double z, float radius, int packetCount) {
          return false;
        }

        @Override
        public void installed(boolean observed, boolean suppressed) {
          observing.set(observed);
          suppressing.set(suppressed);
        }
      });
    }

    Assertions.assertTrue(observing.get());
    Assertions.assertFalse(suppressing.get());
    Mockito.verify(bridge, Mockito.never()).installExplosionPacketSuppressor(Mockito.any());
  }

  private static EntitySpawnEvent spawnEvent(Entity entity) {
    EntitySpawnEvent event = Mockito.mock(EntitySpawnEvent.class);
    Mockito.when(event.getEntity()).thenReturn(entity);
    return event;
  }

  private static EntityExplodeEvent explosionEvent(List<Block> blocks, ExplosionResult result) {
    EntityExplodeEvent event = Mockito.mock(EntityExplodeEvent.class);
    Mockito.when(event.blockList()).thenReturn(blocks);
    Mockito.when(event.getYield()).thenReturn(1F);
    Mockito.when(event.getExplosionResult()).thenReturn(result);
    return event;
  }

  private static Block block(World world, int x, int y, int z, Material material) {
    Block block = Mockito.mock(Block.class);
    Mockito.when(block.getLocation()).thenReturn(new Location(world, x, y, z));
    Mockito.when(block.getWorld()).thenReturn(world);
    Mockito.when(block.getType()).thenReturn(material);
    return block;
  }
}
