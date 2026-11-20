package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.bridge.AgentHookSlot;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.model.SampledWorld;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.platform.Facing;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HopperHost;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.BlockEntityScan;
import art.arcane.react.util.project.world.HopperMoveContext;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.monitor.HopperTickHook;
import art.arcane.volmlib.nativelib.monitor.NativeMonitor;
import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import art.arcane.volmlib.nativelib.monitor.TickDecision;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class BukkitHopperHost implements HopperHost, Listener {
  private static final Set<Material> HOPPER_MATERIALS = EnumSet.of(Material.HOPPER);

  private final List<MoveListener> listeners = new CopyOnWriteArrayList<>();
  private final AgentHookSlot hookSlot = new AgentHookSlot();
  private boolean registered;

  @Override
  public synchronized Subscription onMove(MoveListener listener) {
    listeners.add(listener);
    if (!registered && React.instance != null) {
      React.instance.registerListener(this);
      registered = true;
    }
    return () -> remove(listener);
  }

  private synchronized void remove(MoveListener listener) {
    listeners.remove(listener);
    if (listeners.isEmpty() && registered) {
      registered = false;
      if (React.instance != null) {
        React.instance.unregisterListener(this);
      }
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void on(InventoryMoveItemEvent event) {
    BukkitMove move = new BukkitMove(event);
    for (MoveListener listener : listeners) {
      if (event.isCancelled()) {
        return;
      }
      listener.onMove(move);
    }
  }

  @Override
  public Object chunk(UUID worldId, int chunkX, int chunkZ) {
    World world = Bukkit.getWorld(worldId);
    return world == null ? null : world.getChunkAt(chunkX, chunkZ, false);
  }

  @Override
  public List<HopperBlock> hoppers(Object chunk) {
    List<BlockState> states = new ArrayList<>(BlockEntityScan.runtime().scan((Chunk) chunk, HOPPER_MATERIALS));
    return new AbstractList<>() {
      @Override
      public HopperBlock get(int index) {
        BlockState state = states.get(index);
        return new HopperBlock(state.getX(), state.getY(), state.getZ(), hopperFacing(state.getBlockData()));
      }

      @Override
      public int size() {
        return states.size();
      }
    };
  }

  @Override
  public String blockType(UUID worldId, int x, int y, int z) {
    World world = Bukkit.getWorld(worldId);
    return world == null ? null : world.getBlockAt(x, y, z).getType().name();
  }

  @Override
  public boolean isContainer(UUID worldId, int x, int y, int z) {
    World world = Bukkit.getWorld(worldId);
    return world != null && world.getBlockAt(x, y, z).getState() instanceof InventoryHolder;
  }

  @Override
  public boolean transferOne(UUID worldId, int fromX, int fromY, int fromZ, int toX, int toY, int toZ, BooleanSupplier permit) {
    World world = Bukkit.getWorld(worldId);
    if (world == null) {
      return false;
    }
    Inventory source = inventoryAt(world, fromX, fromY, fromZ);
    if (source == null || source.isEmpty()) {
      return false;
    }
    Inventory target = inventoryAt(world, toX, toY, toZ);
    if (target == null) {
      return false;
    }
    ItemStack[] contents = source.getStorageContents();
    for (int slot = 0; slot < contents.length; slot++) {
      ItemStack stack = contents[slot];
      if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
        continue;
      }
      ItemStack transfer = stack.clone();
      transfer.setAmount(1);
      if (!permit.getAsBoolean()) {
        return false;
      }
      Map<Integer, ItemStack> overflow = target.addItem(transfer);
      if (!overflow.isEmpty()) {
        return false;
      }
      stack.setAmount(stack.getAmount() - 1);
      if (stack.getAmount() <= 0) {
        contents[slot] = null;
      }
      source.setStorageContents(contents);
      return true;
    }
    return false;
  }

  @Override
  public boolean isOwnedByCurrentRegion(UUID worldId, double x, double y, double z) {
    World world = Bukkit.getWorld(worldId);
    return world != null && J.isOwnedByCurrentRegion(new Location(world, x, y, z));
  }

  @Override
  public Subscription installTickFilter(TickFilter filter, Consumer<Boolean> installed) {
    NativeMonitor bridge = NmsBridges.get();
    if (bridge == null) {
      return Subscription.NONE;
    }
    HopperTickHook hook = (World world, int x, int y, int z) -> filter.skip(world.getUID(), x, y, z)
        ? TickDecision.SKIP
        : TickDecision.RUN_VANILLA;
    hookSlot.install(() -> installed.accept(bridge.installHopperTickHook(hook)));
    return () -> hookSlot.release(bridge::uninstallHopperTickHook);
  }

  @Override
  public Subscription onIndexChange(IndexListener listener) {
    List<Subscription> subscriptions = List.of(
        BukkitEvents.subscribe(ItemSpawnEvent.class, ListenerPriority.MONITOR, true, event -> forward(event, listener)),
        BukkitEvents.subscribe(ItemDespawnEvent.class, ListenerPriority.MONITOR, true, event -> forward(event, listener)),
        BukkitEvents.subscribe(EntityPickupItemEvent.class, ListenerPriority.MONITOR, true, event -> forward(event, listener)),
        BukkitEvents.subscribe(EntityRemoveEvent.class, ListenerPriority.MONITOR, false, event -> forward(event, listener)),
        BukkitEvents.subscribe(InventoryPickupItemEvent.class, ListenerPriority.MONITOR, true, event -> forward(event, listener)),
        BukkitEvents.subscribe(EntitiesLoadEvent.class, ListenerPriority.MONITOR, false, event -> forward(event, listener)),
        BukkitEvents.subscribe(EntitiesUnloadEvent.class, ListenerPriority.MONITOR, false, event -> forward(event, listener)),
        BukkitEvents.subscribe(BlockPlaceEvent.class, ListenerPriority.MONITOR, true, event -> forward(event, listener)),
        BukkitEvents.subscribe(BlockBreakEvent.class, ListenerPriority.MONITOR, true, event -> forward(event, listener)),
        BukkitEvents.subscribe(BlockExplodeEvent.class, ListenerPriority.MONITOR, true, event -> forward(event, listener)),
        BukkitEvents.subscribe(EntityExplodeEvent.class, ListenerPriority.MONITOR, true, event -> forward(event, listener)),
        BukkitEvents.subscribe(BlockPistonExtendEvent.class, ListenerPriority.MONITOR, true, event -> forward(event, listener)),
        BukkitEvents.subscribe(BlockPistonRetractEvent.class, ListenerPriority.MONITOR, true, event -> forward(event, listener)));
    return () -> subscriptions.forEach(Subscription::cancel);
  }

  public static void forward(ItemSpawnEvent event, IndexListener listener) {
    listener.itemSeen(event.getEntity());
  }

  public static void forward(ItemDespawnEvent event, IndexListener listener) {
    listener.itemGone(event.getEntity().getUniqueId());
  }

  public static void forward(EntityPickupItemEvent event, IndexListener listener) {
    listener.itemGone(event.getItem().getUniqueId());
  }

  public static void forward(EntityRemoveEvent event, IndexListener listener) {
    if (event.getEntity() instanceof Item item) {
      listener.itemGone(item.getUniqueId());
    }
  }

  public static void forward(InventoryPickupItemEvent event, IndexListener listener) {
    listener.itemGone(event.getItem().getUniqueId());
  }

  public static void forward(EntitiesLoadEvent event, IndexListener listener) {
    for (Entity entity : event.getEntities()) {
      if (entity instanceof Item item) {
        listener.itemSeen(item);
      }
    }
  }

  public static void forward(EntitiesUnloadEvent event, IndexListener listener) {
    for (Entity entity : event.getEntities()) {
      if (entity instanceof Item) {
        listener.itemGone(entity.getUniqueId());
      }
    }
  }

  public static void forward(BlockPlaceEvent event, IndexListener listener) {
    Block block = event.getBlock();
    if (block.getType() == Material.HOPPER) {
      listener.hopperPlaced(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }

  public static void forward(BlockBreakEvent event, IndexListener listener) {
    Block block = event.getBlock();
    if (block.getType() == Material.HOPPER) {
      listener.hopperGone(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }

  public static void forward(BlockExplodeEvent event, IndexListener listener) {
    hoppersDestroyed(event.getBlock().getWorld().getUID(), event.blockList(), listener);
  }

  public static void forward(EntityExplodeEvent event, IndexListener listener) {
    hoppersDestroyed(event.getLocation().getWorld().getUID(), event.blockList(), listener);
  }

  public static void forward(BlockPistonExtendEvent event, IndexListener listener) {
    hoppersMoved(event.getBlock().getWorld().getUID(), event.getBlocks(), event.getDirection(), 1, listener);
  }

  public static void forward(BlockPistonRetractEvent event, IndexListener listener) {
    hoppersMoved(event.getBlock().getWorld().getUID(), event.getBlocks(), event.getDirection(), -1, listener);
  }

  private static void hoppersDestroyed(UUID worldId, List<Block> blocks, IndexListener listener) {
    for (Block block : blocks) {
      if (block.getType() == Material.HOPPER) {
        listener.hopperGone(worldId, block.getX(), block.getY(), block.getZ());
      }
    }
  }

  private static void hoppersMoved(UUID worldId, List<Block> blocks, BlockFace direction, int sign, IndexListener listener) {
    for (Block moved : blocks) {
      if (moved.getType() == Material.HOPPER) {
        listener.hopperGone(worldId, moved.getX(), moved.getY(), moved.getZ());
        listener.hopperPlaced(worldId,
            moved.getX() + sign * direction.getModX(),
            moved.getY() + sign * direction.getModY(),
            moved.getZ() + sign * direction.getModZ());
      }
    }
  }

  @Override
  public boolean supportsHopperAccess() {
    return NativeAdapters.find(NativeWorldAccess.class).isPresent();
  }

  @Override
  public HopperAccess hopperAccess(UUID worldId, int x, int y, int z) {
    NativeWorldAccess access = NativeAdapters.find(NativeWorldAccess.class).orElse(null);
    World world = Bukkit.getWorld(worldId);
    if (access == null || world == null) {
      return null;
    }
    NativeWorldAccess.HopperAccess hopper = access.hopper(world, x, y, z);
    return hopper == null ? null : new NativeHopper(hopper);
  }

  @Override
  public Object item(UUID itemId) {
    Entity entity = Bukkit.getEntity(itemId);
    return entity instanceof Item ? entity : null;
  }

  @Override
  public UUID itemId(Object item) {
    return ((Item) item).getUniqueId();
  }

  @Override
  public boolean isLiveItem(Object item) {
    Item bukkitItem = (Item) item;
    return bukkitItem.isValid() && !bukkitItem.isDead();
  }

  @Override
  public GameLocation itemLocation(Object item) {
    return BukkitGameEntity.location(((Item) item).getLocation());
  }

  @Override
  public Object[] chunkEntities(Object chunk) {
    return ((Chunk) chunk).getEntities();
  }

  @Override
  public boolean isItem(Object entity) {
    return entity instanceof Item;
  }

  @Override
  public Object[] regionPlayers() {
    EntityController controller = React.controller(EntityController.class);
    Player[] players = controller == null ? null : controller.getFoliaPlayers();
    return players == null ? new Object[0] : players;
  }

  @Override
  public GameLocation playerLocation(Object player) {
    Player bukkitPlayer = (Player) player;
    if (!bukkitPlayer.isOnline() || !J.isOwnedByCurrentRegion(bukkitPlayer)) {
      return null;
    }
    return BukkitGameEntity.location(bukkitPlayer.getLocation());
  }

  @Override
  public int mergeNearbyItems(UUID worldId, double x, double y, double z, double radius, int maxMergedEntities) {
    World world = Bukkit.getWorld(worldId);
    if (world == null || maxMergedEntities <= 0) {
      return 0;
    }
    Location center = new Location(world, x, y, z);
    List<Item> items = new ArrayList<>();
    for (Entity entity : world.getNearbyEntities(center, radius, radius, radius, e -> e instanceof Item)) {
      Item item = (Item) entity;
      if (!item.isDead() && item.isValid() && item.getItemStack().getAmount() > 0) {
        items.add(item);
      }
    }

    int merged = 0;
    for (int i = 0; i < items.size(); i++) {
      Item base = items.get(i);
      if (!base.isValid() || base.isDead()) {
        continue;
      }

      ItemStack baseStack = base.getItemStack();
      for (int j = i + 1; j < items.size(); j++) {
        if (merged >= maxMergedEntities) {
          return merged;
        }

        Item other = items.get(j);
        if (!other.isValid() || other.isDead()) {
          continue;
        }

        ItemStack otherStack = other.getItemStack();
        if (!baseStack.isSimilar(otherStack)) {
          continue;
        }

        int room = baseStack.getMaxStackSize() - baseStack.getAmount();
        if (room <= 0) {
          break;
        }

        int transfer = Math.min(room, otherStack.getAmount());
        if (transfer <= 0) {
          continue;
        }

        baseStack.setAmount(baseStack.getAmount() + transfer);
        base.setItemStack(baseStack);
        otherStack.setAmount(otherStack.getAmount() - transfer);
        if (otherStack.getAmount() <= 0) {
          other.remove();
          merged++;
        } else {
          other.setItemStack(otherStack);
        }
      }
    }

    return merged;
  }

  @Override
  public List<SampledChunkRate> sampledChunks(String samplerId) {
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null || observer.getSampled() == null) {
      return List.of();
    }
    List<SampledChunkRate> rates = new ArrayList<>();
    for (SampledWorld sampledWorld : observer.getSampled().getWorlds().values()) {
      String worldKey = sampledWorld.getWorldKey();
      for (SampledChunk sampledChunk : sampledWorld.getChunks().values()) {
        double rate = sampledChunk.optional(samplerId).map(value -> value.get()).orElse(0D);
        rates.add(new SampledChunkRate(worldKey, sampledChunk.getChunkX(), sampledChunk.getChunkZ(), rate));
      }
    }
    return rates;
  }

  @Override
  public int minBuildHeight(UUID worldId) {
    World world = Bukkit.getWorld(worldId);
    return world == null ? 0 : world.getMinHeight();
  }

  @Override
  public boolean unloadChunk(UUID worldId, int chunkX, int chunkZ) {
    World world = Bukkit.getWorld(worldId);
    if (world == null) {
      return false;
    }
    world.getChunkAt(chunkX, chunkZ).unload(true);
    return !world.isChunkLoaded(chunkX, chunkZ);
  }

  private static Inventory inventoryAt(World world, int x, int y, int z) {
    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
      return null;
    }
    BlockState state = world.getBlockAt(x, y, z).getState();
    return state instanceof InventoryHolder holder ? holder.getInventory() : null;
  }

  private static Facing hopperFacing(BlockData data) {
    if (data instanceof org.bukkit.block.data.type.Hopper hopperData) {
      BlockFace facing = hopperData.getFacing();
      return switch (facing) {
        case DOWN -> Facing.DOWN;
        case NORTH -> Facing.NORTH;
        case SOUTH -> Facing.SOUTH;
        case EAST -> Facing.EAST;
        case WEST -> Facing.WEST;
        default -> null;
      };
    }
    return null;
  }

  private record NativeHopper(NativeWorldAccess.HopperAccess access) implements HopperAccess {
    @Override
    public boolean addItem(Object item) {
      return access.addItem((Item) item);
    }

    @Override
    public boolean isEmpty() {
      return access.isEmpty();
    }

    @Override
    public int cooldown() {
      return access.cooldown();
    }

    @Override
    public void cooldown(int ticks) {
      access.cooldown(ticks);
    }
  }

  private static final class BukkitMove implements Move {
    private final InventoryMoveItemEvent event;
    private final HopperMoveContext context;
    private GameLocation hopper;
    private boolean resolved;

    private BukkitMove(InventoryMoveItemEvent event) {
      this.event = event;
      this.context = HopperMoveContext.resolve(event);
    }

    @Override
    public GameLocation hopper() {
      if (!resolved) {
        resolved = true;
        hopper = BukkitGameEntity.location(context.hopperLocation());
      }
      return hopper;
    }

    @Override
    public boolean sourceIsHopper() {
      return context.sourceHopperBlock() != null;
    }

    @Override
    public boolean destinationIsHopper() {
      return context.destinationHopperBlock() != null;
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }
  }
}
