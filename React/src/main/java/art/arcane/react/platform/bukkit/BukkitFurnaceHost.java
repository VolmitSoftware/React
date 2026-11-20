package art.arcane.react.platform.bukkit;

import art.arcane.react.core.bridge.AgentHookSlot;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.FurnaceHost;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.project.world.BlockEntityScan;
import art.arcane.volmlib.nativelib.monitor.BrewingTickHook;
import art.arcane.volmlib.nativelib.monitor.BrewingTickResult;
import art.arcane.volmlib.nativelib.monitor.FurnaceTickHook;
import art.arcane.volmlib.nativelib.monitor.FurnaceTickResult;
import art.arcane.volmlib.nativelib.monitor.NativeMonitor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Furnace;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class BukkitFurnaceHost implements FurnaceHost {
  private static final Set<Material> TRACKED_MATERIALS = EnumSet.of(
      Material.FURNACE,
      Material.BLAST_FURNACE,
      Material.SMOKER,
      Material.BREWING_STAND
  );

  private final AgentHookSlot hookSlot = new AgentHookSlot();

  @Override
  public Subscription hookTicks(TickHook furnace, TickHook brewing, Consumer<Boolean> installed) {
    NativeMonitor bridge = NmsBridges.get();
    if (bridge == null) {
      return Subscription.NONE;
    }
    FurnaceTickHook furnaceHook = (World world, int x, int y, int z) -> furnaceResult(furnace.decide(world.getUID(), x, y, z));
    BrewingTickHook brewingHook = (World world, int x, int y, int z) -> brewingResult(brewing.decide(world.getUID(), x, y, z));
    hookSlot.install(() -> {
      boolean furnaceOk = bridge.installFurnaceTickHook(furnaceHook);
      boolean brewingOk = bridge.installBrewingTickHook(brewingHook);
      installed.accept(furnaceOk || brewingOk);
    });
    return () -> {
      NativeMonitor current = NmsBridges.get();
      hookSlot.release(() -> {
        if (current != null) {
          current.uninstallFurnaceTickHook();
          current.uninstallBrewingTickHook();
        }
      });
    };
  }

  static FurnaceTickResult furnaceResult(int decision) {
    return decision == SKIP ? FurnaceTickResult.SKIP : FurnaceTickResult.runAndAdvance(decision);
  }

  static BrewingTickResult brewingResult(int decision) {
    return decision == SKIP ? BrewingTickResult.SKIP : BrewingTickResult.runAndAdvance(decision);
  }

  @Override
  public Subscription onActivity(Consumer<Station> listener) {
    List<Subscription> subscriptions = new ArrayList<>(4);
    subscriptions.add(BukkitEvents.subscribe(InventoryOpenEvent.class, ListenerPriority.MONITOR, true,
        event -> touchHolder(event.getInventory(), listener)));
    subscriptions.add(BukkitEvents.subscribe(InventoryMoveItemEvent.class, ListenerPriority.MONITOR, true,
        event -> touchMove(event, listener)));
    subscriptions.add(BukkitEvents.subscribe(FurnaceBurnEvent.class, ListenerPriority.MONITOR, false,
        event -> touchFurnace(event.getBlock(), listener)));
    subscriptions.add(BukkitEvents.subscribe(FurnaceSmeltEvent.class, ListenerPriority.MONITOR, false,
        event -> touchFurnace(event.getBlock(), listener)));
    return () -> {
      for (Subscription subscription : subscriptions) {
        subscription.cancel();
      }
    };
  }

  static void touchMove(InventoryMoveItemEvent event, Consumer<Station> listener) {
    touchHolder(event.getDestination(), listener);
    touchHolder(event.getSource(), listener);
  }

  private static void touchFurnace(Block block, Consumer<Station> listener) {
    Location loc = block.getLocation();
    World world = loc.getWorld();
    if (world != null) {
      listener.accept(new Station(world.getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), Kind.FURNACE));
    }
  }

  private static void touchHolder(Inventory inventory, Consumer<Station> listener) {
    Kind kind = kind(inventory);
    if (kind == null) {
      return;
    }

    Location loc = inventory.getLocation();
    if (loc == null || loc.getWorld() == null) {
      return;
    }

    listener.accept(new Station(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), kind));
  }

  private static Kind kind(Inventory inventory) {
    if (inventory instanceof FurnaceInventory) {
      return Kind.FURNACE;
    }
    if (inventory instanceof BrewerInventory) {
      return Kind.BREWING_STAND;
    }
    return null;
  }

  @Override
  public List<Station> scan(ChunkRef ref) {
    World world = Bukkit.getWorld(ref.worldId());
    if (world == null || !world.isChunkLoaded(ref.chunkX(), ref.chunkZ())) {
      return List.of();
    }
    Chunk chunk = world.getChunkAt(ref.chunkX(), ref.chunkZ());
    if (chunk == null || !chunk.isLoaded()) {
      return List.of();
    }
    List<Station> stations = new ArrayList<>();
    for (BlockState state : BlockEntityScan.runtime().scan(chunk, TRACKED_MATERIALS)) {
      Kind kind = Kind.of(state.getType().name());
      if (kind != null) {
        stations.add(new Station(ref.worldId(), state.getX(), state.getY(), state.getZ(), kind));
      }
    }
    return stations;
  }

  @Override
  public Optional<FurnaceState> furnace(UUID worldId, int x, int y, int z) {
    if (!(state(worldId, x, y, z) instanceof Furnace furnace)) {
      return Optional.empty();
    }
    FurnaceInventory inventory = furnace.getSnapshotInventory();
    return Optional.of(new FurnaceState(furnace.getType().name(), furnace.getBurnTime(), furnace.getCookTime(),
        furnace.getCookTimeTotal(), !isEmpty(inventory.getSmelting()), !isEmpty(inventory.getFuel())));
  }

  @Override
  public Optional<BrewingState> brewing(UUID worldId, int x, int y, int z) {
    if (!(state(worldId, x, y, z) instanceof BrewingStand stand)) {
      return Optional.empty();
    }
    BrewerInventory inventory = stand.getSnapshotInventory();
    boolean hasBottle = !isEmpty(inventory.getItem(0)) || !isEmpty(inventory.getItem(1)) || !isEmpty(inventory.getItem(2));
    return Optional.of(new BrewingState(stand.getFuelLevel(), stand.getBrewingTime(), !isEmpty(inventory.getIngredient()), hasBottle));
  }

  private static BlockState state(UUID worldId, int x, int y, int z) {
    World world = Bukkit.getWorld(worldId);
    if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
      return null;
    }
    return world.getChunkAt(x >> 4, z >> 4).getBlock(x & 15, y, z & 15).getState();
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
  }
}
