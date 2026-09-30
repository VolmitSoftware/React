package art.arcane.react.content.feature;

import art.arcane.react.testutil.FakeInventories;
import art.arcane.react.testutil.Fakes;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureChunkQuarantineTest {
  @BeforeAll
  static void initializeInventoryTypes() {
    FakeInventories.initializeInventoryTypes();
  }

  @Test
  void idleEventsReturnBeforeReadingTheEventOrTrackingChunks() throws Exception {
    FeatureChunkQuarantine quarantine = quarantine();
    World world = Fakes.world("world");
    Block physicsBlock = Mockito.mock(Block.class);
    Block redstoneBlock = Mockito.mock(Block.class);
    LivingEntity spawned = Mockito.mock(LivingEntity.class);
    Inventory chest = FakeInventories.chest(world, 0, 65, 0);
    Inventory hopper = FakeInventories.hopperBlock(world, 0, 64, 0);

    quarantine.on(new BlockPhysicsEvent(physicsBlock, Mockito.mock(BlockData.class)));
    quarantine.on(new BlockRedstoneEvent(redstoneBlock, 0, 15));
    quarantine.on(new CreatureSpawnEvent(spawned, CreatureSpawnEvent.SpawnReason.NATURAL));
    quarantine.on(FakeInventories.move(chest, hopper));

    Mockito.verifyNoInteractions(physicsBlock, redstoneBlock, spawned, chest, hopper);
    assertTrue(states(quarantine).isEmpty());
  }

  @Test
  void quarantinedChunksStayEnforcedAfterPressureEnds() throws Exception {
    FeatureChunkQuarantine quarantine = quarantine();
    set(quarantine, "scoreTrigger", 1D);
    set(quarantine, "pressure", true);
    World world = Fakes.world("world");
    Block block = FakeInventories.block(world, 3, 64, 3, Material.REDSTONE_WIRE);

    BlockPhysicsEvent first = physics(quarantine, block);
    BlockPhysicsEvent second = physics(quarantine, block);
    BlockPhysicsEvent third = physics(quarantine, block);
    set(quarantine, "pressure", false);
    BlockPhysicsEvent afterPressure = physics(quarantine, block);

    assertFalse(first.isCancelled());
    assertFalse(second.isCancelled());
    assertTrue(third.isCancelled());
    assertTrue(afterPressure.isCancelled());
  }

  @Test
  void aNewChunkAtCapacityEvictsTheLeastRecentlyActiveChunk() throws Exception {
    FeatureChunkQuarantine quarantine = quarantine();
    set(quarantine, "pressure", true);
    set(quarantine, "maxTrackedChunks", 2);
    set(quarantine, "scoreTrigger", 1D);
    World world = Fakes.world("world");
    Block a = FakeInventories.block(world, 0, 64, 0, Material.STONE);
    Block b = FakeInventories.block(world, 16, 64, 0, Material.STONE);
    Block c = FakeInventories.block(world, 32, 64, 0, Material.STONE);

    physics(quarantine, a);
    physics(quarantine, b);
    physics(quarantine, a);
    physics(quarantine, c);
    physics(quarantine, c);
    BlockPhysicsEvent third = physics(quarantine, c);

    assertEquals(Set.of(chunkKey(0, 0), chunkKey(2, 0)), trackedChunks(quarantine, world));
    assertTrue(third.isCancelled());
  }

  @Test
  void maintenanceExpiresStaleChunksBeyondTheScanWindow() throws Exception {
    FeatureChunkQuarantine quarantine = quarantine();
    set(quarantine, "pressure", true);
    set(quarantine, "onlyDuringPressure", false);
    set(quarantine, "maintenanceIntervalMS", 0);
    set(quarantine, "maxExpiryRemovalsPerCycle", 16);
    set(quarantine, "maxExpiryScansPerCycle", 16);
    World world = Fakes.world("world");
    Set<Long> stale = new HashSet<>();
    for (int i = 0; i < 40; i++) {
      physics(quarantine, FakeInventories.block(world, i * 16, 64, 0, Material.STONE));
      stale.add(chunkKey(i, 0));
    }
    ageAllChunks(quarantine, world, 3_600_000L);
    List<Long> fresh = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
      physics(quarantine, FakeInventories.block(world, i * 16, 64, 160, Material.STONE));
      fresh.add(chunkKey(i, 10));
    }

    for (int cycle = 0; cycle < 10; cycle++) {
      runMaintenance(quarantine);
    }

    Set<Long> tracked = trackedChunks(quarantine, world);
    for (Long key : stale) {
      assertFalse(tracked.contains(key), "stale chunk " + key + " was never expired");
    }
    assertTrue(tracked.containsAll(fresh));
    assertEquals(fresh.size(), tracked.size());
  }

  @Test
  void hopperMovesChargeTheHopperChunkWithoutSnapshots() throws Exception {
    FeatureChunkQuarantine quarantine = quarantine();
    set(quarantine, "pressure", true);
    World world = Fakes.world("world");
    Inventory chest = FakeInventories.chest(world, 40, 65, -20);
    Inventory hopper = FakeInventories.hopperBlock(world, 40, 64, -20);
    Inventory minecart = FakeInventories.hopperMinecart(world, 100.5D, 64D, 100.5D);

    quarantine.on(FakeInventories.move(chest, hopper));
    quarantine.on(FakeInventories.move(chest, minecart));

    Mockito.verify(chest, Mockito.never()).getHolder();
    Mockito.verify(hopper, Mockito.never()).getHolder();
    Mockito.verify(minecart, Mockito.never()).getHolder();
    assertEquals(Set.of(chunkKey(2, -2)), trackedChunks(quarantine, world));
  }

  private static FeatureChunkQuarantine quarantine() throws Exception {
    FeatureChunkQuarantine quarantine = new FeatureChunkQuarantine();
    quarantine.onActivate();
    set(quarantine, "bypassNearPlayers", false);
    set(quarantine, "samplePhysicsEveryN", 1);
    return quarantine;
  }

  private static BlockPhysicsEvent physics(FeatureChunkQuarantine quarantine, Block block) {
    BlockPhysicsEvent event = new BlockPhysicsEvent(block, Mockito.mock(BlockData.class));
    quarantine.on(event);
    return event;
  }

  private static void runMaintenance(FeatureChunkQuarantine quarantine) throws Exception {
    Method method = FeatureChunkQuarantine.class.getDeclaredMethod("runMaintenance");
    method.setAccessible(true);
    method.invoke(quarantine);
  }

  private static void ageAllChunks(FeatureChunkQuarantine quarantine, World world, long ageMS) throws Exception {
    long agedAt = System.currentTimeMillis() - ageMS;
    for (Object state : chunkMap(quarantine, world).values()) {
      Field start = state.getClass().getDeclaredField("start");
      Field lastHit = state.getClass().getDeclaredField("lastHit");
      start.setAccessible(true);
      lastHit.setAccessible(true);
      start.setLong(state, agedAt);
      lastHit.setLong(state, agedAt);
    }
  }

  private static Set<Long> trackedChunks(FeatureChunkQuarantine quarantine, World world) throws Exception {
    return new HashSet<>(chunkMap(quarantine, world).keySet());
  }

  private static Long2ObjectMap<?> chunkMap(FeatureChunkQuarantine quarantine, World world) throws Exception {
    Object chunkMap = states(quarantine).get(world.getUID());
    return chunkMap == null ? Long2ObjectMaps.emptyMap() : (Long2ObjectMap<?>) chunkMap;
  }

  private static Map<UUID, ?> states(FeatureChunkQuarantine quarantine) throws Exception {
    Field field = FeatureChunkQuarantine.class.getDeclaredField("states");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<UUID, ?> states = (Map<UUID, ?>) field.get(quarantine);
    return states;
  }

  private static long chunkKey(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
