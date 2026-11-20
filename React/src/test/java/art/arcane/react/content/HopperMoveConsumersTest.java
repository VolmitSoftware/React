package art.arcane.react.content;

import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.bukkit.BukkitHopperHost;
import art.arcane.react.platform.bukkit.BukkitSamplerEventBridge;

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
    BukkitHopperHost host = hopperHost();
    InventoryMoveItemEvent first = FakeInventories.move(chest, hopper);
    InventoryMoveItemEvent second = FakeInventories.move(chest, hopper);
    InventoryMoveItemEvent chestOnly = FakeInventories.move(chest, otherChest);
    bucket.onActivate();
    try {
      host.on(first);
      host.on(second);
      host.on(chestOnly);
    } finally {
      bucket.onDeactivate();
    }

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
    InventoryMoveItemEvent first = FakeInventories.move(hopper, chest);
    InventoryMoveItemEvent second = FakeInventories.move(hopper, chest);
    BukkitHopperHost host = hopperHost();
    incidentMode.onActivate();
    try {
      set(incidentMode, "incident", true);
      host.on(first);
      host.on(second);
    } finally {
      incidentMode.onDeactivate();
    }

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

    BukkitHopperHost host = hopperHost();
    try (MockedStatic<SamplerHosts> samplers = Mockito.mockStatic(SamplerHosts.class)) {
      samplers.when(() -> SamplerHosts.sampler(SamplerHopperEventSpan.ID)).thenReturn(span);
      tweak.onActivate();
      try {
        host.on(intoHopper);
        host.on(outOfHopper);
        host.on(intoMinecart);
      } finally {
        tweak.onDeactivate();
      }
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
    BukkitSamplerEventBridge.HopperMove hopperMoves = new BukkitSamplerEventBridge.HopperMove();
    BukkitSamplerEventBridge.ServerTick ticks = new BukkitSamplerEventBridge.ServerTick();
    span.start();

    hopperMoves.on(FakeInventories.move(chest, otherChest));
    hopperMoves.on(FakeInventories.move(chest, otherChest));
    ticks.on(new ServerTickEvent());
    assertEquals(0D, span.onSample());

    hopperMoves.on(FakeInventories.move(chest, hopper));
    long started = System.nanoTime();
    while (System.nanoTime() - started < 2_000_000L) {
      Thread.onSpinWait();
    }
    hopperMoves.on(FakeInventories.move(hopper, otherChest));
    ticks.on(new ServerTickEvent());

    Mockito.verify(chest, Mockito.never()).getHolder();
    Mockito.verify(otherChest, Mockito.never()).getHolder();
    Mockito.verify(hopper, Mockito.never()).getHolder();
    assertTrue(span.onSample() > 0D);
    span.stop();
  }

  private static BukkitHopperHost hopperHost() {
    return (BukkitHopperHost) ReactPlatforms.get().hopperHost().orElseThrow();
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
