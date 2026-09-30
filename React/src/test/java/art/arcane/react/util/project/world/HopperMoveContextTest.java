package art.arcane.react.util.project.world;

import art.arcane.react.testutil.FakeInventories;
import art.arcane.react.testutil.Fakes;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class HopperMoveContextTest {
  @BeforeAll
  static void initializeInventoryTypes() {
    FakeInventories.initializeInventoryTypes();
  }

  @Test
  void chestIntoHopperResolvesTheDestinationHopperBlockWithoutSnapshots() {
    World world = Fakes.world("world");
    Inventory chest = FakeInventories.chest(world, 4, 65, 4);
    Inventory hopper = FakeInventories.hopperBlock(world, 4, 64, 4);
    Block hopperBlock = FakeInventories.blockOf(hopper);

    HopperMoveContext context = HopperMoveContext.resolve(FakeInventories.move(chest, hopper));

    assertNull(context.sourceHopperBlock());
    assertSame(hopperBlock, context.destinationHopperBlock());
    assertSame(hopperBlock, context.hopperBlock());
    assertEquals(new Location(world, 4, 64, 4), context.hopperLocation());
    Mockito.verify(chest, Mockito.never()).getHolder();
    Mockito.verify(hopper, Mockito.never()).getHolder();
  }

  @Test
  void hopperIntoHopperPrefersTheSourceHopper() {
    World world = Fakes.world("world");
    Inventory source = FakeInventories.hopperBlock(world, 0, 64, 0);
    Inventory destination = FakeInventories.hopperBlock(world, 1, 64, 0);
    Block sourceBlock = FakeInventories.blockOf(source);
    Block destinationBlock = FakeInventories.blockOf(destination);

    HopperMoveContext context = HopperMoveContext.resolve(FakeInventories.move(source, destination));

    assertSame(sourceBlock, context.sourceHopperBlock());
    assertSame(destinationBlock, context.destinationHopperBlock());
    assertSame(sourceBlock, context.hopperBlock());
    assertEquals(new Location(world, 0, 64, 0), context.hopperLocation());
    Mockito.verify(source, Mockito.never()).getHolder();
    Mockito.verify(destination, Mockito.never()).getHolder();
  }

  @Test
  void hopperMinecartsAreNotHopperBlocks() {
    World world = Fakes.world("world");
    Inventory minecart = FakeInventories.hopperMinecart(world, 8.5D, 64D, 8.5D);
    Inventory chest = FakeInventories.chest(world, 8, 63, 8);

    HopperMoveContext pulling = HopperMoveContext.resolve(FakeInventories.move(chest, minecart));
    HopperMoveContext pushing = HopperMoveContext.resolve(FakeInventories.move(minecart, chest));

    assertNull(pulling.hopperBlock());
    assertNull(pulling.hopperLocation());
    assertNull(pushing.hopperBlock());
    assertNull(pushing.hopperLocation());
    Mockito.verify(minecart, Mockito.never()).getHolder();
    Mockito.verify(chest, Mockito.never()).getHolder();
  }

  @Test
  void inventoriesWithoutALocationAreNotHopperBlocks() {
    World world = Fakes.world("world");
    Inventory unplaced = FakeInventories.unplacedHopper();
    Inventory chest = FakeInventories.chest(world, 0, 64, 0);

    HopperMoveContext context = HopperMoveContext.resolve(FakeInventories.move(unplaced, chest));

    assertNull(context.sourceHopperBlock());
    assertNull(context.destinationHopperBlock());
    assertNull(context.hopperLocation());
    Mockito.verify(unplaced, Mockito.never()).getHolder();
  }

  @Test
  void chestsNeverResolveTheirLocation() {
    World world = Fakes.world("world");
    Inventory source = FakeInventories.chest(world, 0, 64, 0);
    Inventory destination = FakeInventories.chest(world, 1, 64, 0);

    HopperMoveContext context = HopperMoveContext.resolve(FakeInventories.move(source, destination));

    assertNull(context.hopperBlock());
    Mockito.verify(source, Mockito.never()).getLocation();
    Mockito.verify(destination, Mockito.never()).getLocation();
  }

  @Test
  void everyHandlerOfOneEventSharesOneResolution() {
    World world = Fakes.world("world");
    Inventory chest = FakeInventories.chest(world, 2, 65, 2);
    Inventory hopper = FakeInventories.hopperBlock(world, 2, 64, 2);
    InventoryMoveItemEvent event = FakeInventories.move(chest, hopper);

    HopperMoveContext first = HopperMoveContext.resolve(event);
    HopperMoveContext second = HopperMoveContext.resolve(event);
    HopperMoveContext nextMove = HopperMoveContext.resolve(FakeInventories.move(chest, hopper));

    assertSame(first, second);
    assertNotSame(first, nextMove);
    Mockito.verify(chest, Mockito.times(2)).getType();
    Mockito.verify(hopper, Mockito.times(2)).getType();
    Mockito.verify(hopper, Mockito.times(2)).getLocation();
  }
}
