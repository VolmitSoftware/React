package art.arcane.react.testutil;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Hopper;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.mockito.Mockito;

public final class FakeInventories {
  private FakeInventories() {
  }

  public static void initializeInventoryTypes() {
    FakeRegistries.initialize(InventoryType.class);
  }

  public static InventoryMoveItemEvent move(Inventory source, Inventory destination) {
    return new InventoryMoveItemEvent(source, Mockito.mock(ItemStack.class), destination, true);
  }

  public static Inventory hopperBlock(World world, int x, int y, int z) {
    Block block = block(world, x, y, z, Material.HOPPER);
    Chunk chunk = block.getChunk();
    Location location = block.getLocation();
    Hopper snapshot = Mockito.mock(Hopper.class);
    Mockito.when(snapshot.getBlock()).thenReturn(block);
    Mockito.when(snapshot.getChunk()).thenReturn(chunk);
    Mockito.when(snapshot.getLocation()).thenReturn(location);
    Inventory inventory = placed(InventoryType.HOPPER, location);
    Mockito.when(inventory.getHolder()).thenReturn(snapshot);
    return inventory;
  }

  public static Inventory chest(World world, int x, int y, int z) {
    Block block = block(world, x, y, z, Material.CHEST);
    Location location = block.getLocation();
    Chest snapshot = Mockito.mock(Chest.class);
    Mockito.when(snapshot.getBlock()).thenReturn(block);
    Inventory inventory = placed(InventoryType.CHEST, location);
    Mockito.when(inventory.getHolder()).thenReturn(snapshot);
    return inventory;
  }

  public static Inventory hopperMinecart(World world, double x, double y, double z) {
    Location location = new Location(world, x, y, z);
    Block rail = block(world, location.getBlockX(), location.getBlockY(), location.getBlockZ(), Material.RAIL);
    Mockito.when(world.getBlockAt(location)).thenReturn(rail);
    Inventory inventory = placed(InventoryType.HOPPER, location);
    HopperMinecart minecart = Mockito.mock(HopperMinecart.class);
    Mockito.when(inventory.getHolder()).thenReturn(minecart);
    return inventory;
  }

  public static Inventory unplacedHopper() {
    return placed(InventoryType.HOPPER, null);
  }

  public static Block blockOf(Inventory inventory) {
    Location location = inventory.getLocation();
    return location.getWorld().getBlockAt(location);
  }

  public static Block block(World world, int x, int y, int z, Material type) {
    Block block = Mockito.mock(Block.class);
    Location location = new Location(world, x, y, z);
    Chunk chunk = Fakes.chunk(world, x >> 4, z >> 4);
    Mockito.when(block.getType()).thenReturn(type);
    Mockito.when(block.getWorld()).thenReturn(world);
    Mockito.when(block.getX()).thenReturn(x);
    Mockito.when(block.getY()).thenReturn(y);
    Mockito.when(block.getZ()).thenReturn(z);
    Mockito.when(block.getLocation()).thenReturn(location);
    Mockito.when(block.getChunk()).thenReturn(chunk);
    Mockito.when(world.getBlockAt(location)).thenReturn(block);
    Mockito.when(world.getBlockAt(x, y, z)).thenReturn(block);
    return block;
  }

  private static Inventory placed(InventoryType type, Location location) {
    Inventory inventory = Mockito.mock(Inventory.class);
    Mockito.when(inventory.getType()).thenReturn(type);
    Mockito.when(inventory.getLocation()).thenReturn(location);
    return inventory;
  }
}
