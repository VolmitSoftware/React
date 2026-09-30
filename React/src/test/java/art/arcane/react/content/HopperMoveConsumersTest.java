package art.arcane.react.content;

import art.arcane.react.React;
import art.arcane.react.api.event.layer.ServerTickEvent;
import art.arcane.react.content.feature.FeatureHopperTokenBucket;
import art.arcane.react.content.feature.FeatureIncidentMode;
import art.arcane.react.content.sampler.SamplerHopperEventSpan;
import art.arcane.react.content.tweak.TweakHopperLimit;
import art.arcane.react.testutil.FakeInventories;
import art.arcane.react.testutil.Fakes;
import org.bukkit.World;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HopperMoveConsumersTest {
  @BeforeAll
  static void initializeInventoryTypes() {
    FakeInventories.initializeInventoryTypes();
  }

  @Test
  void tokenBucketChargesTheHopperChunkWithoutSnapshots() throws Exception {
    World world = Fakes.world("world");
    Inventory chest = FakeInventories.chest(world, 0, 65, 0);
    Inventory hopper = FakeInventories.hopperBlock(world, 0, 64, 0);
    Inventory otherChest = FakeInventories.chest(world, 64, 64, 64);
    FeatureHopperTokenBucket bucket = new FeatureHopperTokenBucket();
    set(bucket, "bypassWhenNearbyPlayers", false);
    set(bucket, "bucketCapacity", 1D);
    set(bucket, "refillPerSecond", 0D);
    bucket.onActivate();

    InventoryMoveItemEvent first = FakeInventories.move(chest, hopper);
    InventoryMoveItemEvent second = FakeInventories.move(chest, hopper);
    InventoryMoveItemEvent chestOnly = FakeInventories.move(chest, otherChest);
    bucket.on(first);
    bucket.on(second);
    bucket.on(chestOnly);

    Mockito.verify(chest, Mockito.never()).getHolder();
    Mockito.verify(hopper, Mockito.never()).getHolder();
    Mockito.verify(otherChest, Mockito.never()).getHolder();
    assertFalse(first.isCancelled());
    assertTrue(second.isCancelled());
    assertFalse(chestOnly.isCancelled());
  }

  @Test
  void incidentModeLimitsHopperMovesWithoutSnapshots() throws Exception {
    World world = Fakes.world("world");
    Inventory hopper = FakeInventories.hopperBlock(world, 0, 64, 0);
    Inventory chest = FakeInventories.chest(world, 0, 63, 0);
    FeatureIncidentMode incidentMode = new FeatureIncidentMode();
    set(incidentMode, "bypassNearPlayers", false);
    set(incidentMode, "maxHopperMovesPerWindow", 1);
    set(incidentMode, "rateWindowMS", 60000);
    incidentMode.onActivate();
    set(incidentMode, "incident", true);

    InventoryMoveItemEvent first = FakeInventories.move(hopper, chest);
    InventoryMoveItemEvent second = FakeInventories.move(hopper, chest);
    incidentMode.on(first);
    incidentMode.on(second);

    Mockito.verify(hopper, Mockito.never()).getHolder();
    Mockito.verify(chest, Mockito.never()).getHolder();
    assertFalse(first.isCancelled());
    assertTrue(second.isCancelled());
  }

  @Test
  void hopperLimitOnlyThrottlesMovesIntoHopperBlocks() {
    World world = Fakes.world("world");
    Inventory chest = FakeInventories.chest(world, 0, 65, 0);
    Inventory hopper = FakeInventories.hopperBlock(world, 0, 64, 0);
    Inventory minecart = FakeInventories.hopperMinecart(world, 4.5D, 64D, 4.5D);
    SamplerHopperEventSpan span = Mockito.mock(SamplerHopperEventSpan.class);
    Mockito.when(span.sample()).thenReturn(5D);
    TweakHopperLimit tweak = new TweakHopperLimit();
    InventoryMoveItemEvent intoHopper = FakeInventories.move(chest, hopper);
    InventoryMoveItemEvent outOfHopper = FakeInventories.move(hopper, chest);
    InventoryMoveItemEvent intoMinecart = FakeInventories.move(chest, minecart);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.sampler(SamplerHopperEventSpan.class)).thenReturn(span);
      tweak.on(intoHopper);
      tweak.on(outOfHopper);
      tweak.on(intoMinecart);
    }

    Mockito.verify(chest, Mockito.never()).getHolder();
    Mockito.verify(hopper, Mockito.never()).getHolder();
    Mockito.verify(minecart, Mockito.never()).getHolder();
    assertTrue(intoHopper.isCancelled());
    assertFalse(outOfHopper.isCancelled());
    assertFalse(intoMinecart.isCancelled());
  }

  @Test
  void hopperEventSpanRecordsHopperMovesWithoutSnapshots() {
    World world = Fakes.world("world");
    Inventory chest = FakeInventories.chest(world, 0, 65, 0);
    Inventory otherChest = FakeInventories.chest(world, 1, 65, 0);
    Inventory hopper = FakeInventories.hopperBlock(world, 0, 64, 0);
    SamplerHopperEventSpan span = new SamplerHopperEventSpan();
    span.start();

    span.on(FakeInventories.move(chest, otherChest));
    span.on(FakeInventories.move(chest, otherChest));
    span.on(new ServerTickEvent());
    assertEquals(0D, span.onSample());

    span.on(FakeInventories.move(chest, hopper));
    long started = System.nanoTime();
    while (System.nanoTime() - started < 2_000_000L) {
      Thread.onSpinWait();
    }
    span.on(FakeInventories.move(hopper, otherChest));
    span.on(new ServerTickEvent());

    Mockito.verify(chest, Mockito.never()).getHolder();
    Mockito.verify(otherChest, Mockito.never()).getHolder();
    Mockito.verify(hopper, Mockito.never()).getHolder();
    assertTrue(span.onSample() > 0D);
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
