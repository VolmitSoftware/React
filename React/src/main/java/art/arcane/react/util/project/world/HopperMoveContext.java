package art.arcane.react.util.project.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public final class HopperMoveContext {
  private static final int RECENT_SLOT_MASK = 63;
  private static final HopperMoveContext[] RECENT = new HopperMoveContext[RECENT_SLOT_MASK + 1];

  private final InventoryMoveItemEvent event;
  private final Block sourceHopperBlock;
  private final Block destinationHopperBlock;
  private final Location hopperLocation;

  private HopperMoveContext(InventoryMoveItemEvent event) {
    this.event = event;
    Location sourceLocation = hopperInventoryLocation(event.getSource());
    Location destinationLocation = hopperInventoryLocation(event.getDestination());
    this.sourceHopperBlock = hopperBlockAt(sourceLocation);
    this.destinationHopperBlock = hopperBlockAt(destinationLocation);
    this.hopperLocation = sourceHopperBlock != null ? sourceLocation : destinationHopperBlock != null ? destinationLocation : null;
  }

  public static HopperMoveContext resolve(InventoryMoveItemEvent event) {
    int slot = (int) (Thread.currentThread().threadId() & RECENT_SLOT_MASK);
    HopperMoveContext recent = RECENT[slot];
    if (recent != null && recent.event == event) {
      return recent;
    }

    HopperMoveContext context = new HopperMoveContext(event);
    RECENT[slot] = context;
    return context;
  }

  public Block sourceHopperBlock() {
    return sourceHopperBlock;
  }

  public Block destinationHopperBlock() {
    return destinationHopperBlock;
  }

  public Block hopperBlock() {
    return sourceHopperBlock != null ? sourceHopperBlock : destinationHopperBlock;
  }

  public Location hopperLocation() {
    return hopperLocation;
  }

  private static Location hopperInventoryLocation(Inventory inventory) {
    if (inventory == null || inventory.getType() != InventoryType.HOPPER) {
      return null;
    }

    Location location = inventory.getLocation();
    return location == null || location.getWorld() == null ? null : location;
  }

  private static Block hopperBlockAt(Location location) {
    if (location == null) {
      return null;
    }

    Block block = location.getBlock();
    return block.getType() == Material.HOPPER ? block : null;
  }
}
