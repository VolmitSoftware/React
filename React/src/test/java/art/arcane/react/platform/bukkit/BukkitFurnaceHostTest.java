package art.arcane.react.platform.bukkit;

import art.arcane.react.platform.FurnaceHost;
import art.arcane.volmlib.nativelib.monitor.BrewingTickResult;
import art.arcane.volmlib.nativelib.monitor.FurnaceTickResult;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitFurnaceHostTest {
  @Test
  void tickDecisionsMapToTheAgentResults() {
    assertSame(FurnaceTickResult.SKIP, BukkitFurnaceHost.furnaceResult(FurnaceHost.SKIP));
    assertSame(FurnaceTickResult.RUN_VANILLA, BukkitFurnaceHost.furnaceResult(0));
    assertEquals(3, BukkitFurnaceHost.furnaceResult(3).advanceTicks());
    assertFalse(BukkitFurnaceHost.furnaceResult(3).skip());
    assertSame(BrewingTickResult.SKIP, BukkitFurnaceHost.brewingResult(FurnaceHost.SKIP));
    assertSame(BrewingTickResult.RUN_VANILLA, BukkitFurnaceHost.brewingResult(0));
    assertEquals(5, BukkitFurnaceHost.brewingResult(5).advanceTicks());
  }

  @Test
  void inventoryMovesSkipLocationLookupsForNonFurnaceInventories() {
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(world.getUID()).thenReturn(worldId);
    Inventory hopper = inventory(Inventory.class, new Location(world, 0, 64, 0));
    Inventory chest = inventory(Inventory.class, new Location(world, 0, 63, 0));
    Inventory smoker = inventory(FurnaceInventory.class, new Location(world, 1, 63, 0));
    Inventory brewer = inventory(BrewerInventory.class, new Location(world, 2, 63, 0));
    ItemStack stack = Mockito.mock(ItemStack.class);
    List<FurnaceHost.Station> touched = new ArrayList<>();

    BukkitFurnaceHost.touchMove(new InventoryMoveItemEvent(hopper, stack, chest, true), touched::add);
    Mockito.verify(hopper, Mockito.never()).getLocation();
    Mockito.verify(chest, Mockito.never()).getLocation();
    assertTrue(touched.isEmpty());

    BukkitFurnaceHost.touchMove(new InventoryMoveItemEvent(hopper, stack, smoker, true), touched::add);
    BukkitFurnaceHost.touchMove(new InventoryMoveItemEvent(brewer, stack, hopper, true), touched::add);
    assertEquals(List.of(
        new FurnaceHost.Station(worldId, 1, 63, 0, FurnaceHost.Kind.FURNACE),
        new FurnaceHost.Station(worldId, 2, 63, 0, FurnaceHost.Kind.BREWING_STAND)
    ), touched);
  }

  private static Inventory inventory(Class<? extends Inventory> type, Location location) {
    Inventory inventory = Mockito.mock(type);
    Mockito.when(inventory.getLocation()).thenReturn(location);
    return inventory;
  }
}
