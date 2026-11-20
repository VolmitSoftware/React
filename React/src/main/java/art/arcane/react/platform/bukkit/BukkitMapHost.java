package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.api.rendering.MapRuntime;
import art.arcane.react.core.controller.MapPaperFrameChangeListener;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.DirtyRect;
import art.arcane.react.platform.Facing;
import art.arcane.react.platform.FrameSnapshot;
import art.arcane.react.platform.MapHost;
import art.arcane.react.platform.MapHostListener;
import art.arcane.react.platform.MapItemTags;
import art.arcane.react.platform.MapItemView;
import art.arcane.react.platform.ViewerSnapshot;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class BukkitMapHost implements MapHost<ItemStack> {
  public static final String KEY_REACT = "react";
  public static final String KEY_RENDERER = "react-renderer";
  public static final String KEY_TOKEN = "react-map-token";

  private final NamespacedKey nsReact;
  private final NamespacedKey nsRenderer;
  private final NamespacedKey nsMapToken;
  private final Plugin plugin;
  private final Map<Integer, BukkitMapPipe> pipes = new ConcurrentHashMap<>();
  private volatile MapRuntime<ItemStack> runtime;
  private Listener eventListener;
  private Listener frameChangeListener;
  private volatile boolean frameChangeEventsAvailable;
  private Method silentSetItem;
  private boolean silentSetItemResolved;

  public BukkitMapHost(Plugin plugin) {
    this.plugin = plugin;
    this.nsReact = new NamespacedKey("react", KEY_REACT);
    this.nsRenderer = new NamespacedKey("react", KEY_RENDERER);
    this.nsMapToken = new NamespacedKey("react", KEY_TOKEN);
  }

  public void attach(MapRuntime<ItemStack> runtime) {
    this.runtime = runtime;
  }

  public boolean isFrameChangeEventsAvailable() {
    return frameChangeEventsAvailable;
  }

  // ---- map ids ----------------------------------------------------------------------------

  @Override
  public int allocateMapId(UUID worldId) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    if (world == null) {
      return -1;
    }
    return Bukkit.createMap(world).getId();
  }

  @Override
  public void ensureMapData(int mapId, UUID worldId) {
  }

  @Override
  public boolean mapIdsAreWorldBound() {
    return true;
  }

  @Override
  public UUID mapWorld(int mapId) {
    MapView view = view(mapId);
    World world = view == null ? null : view.getWorld();
    return world == null ? null : world.getUID();
  }

  @SuppressWarnings("deprecation")
  public MapView view(int mapId) {
    if (mapId < 0) {
      return null;
    }
    try {
      return Bukkit.getMap(mapId);
    } catch (Throwable ex) {
      React.verbose(() -> "Failed to read map view " + mapId + ": " + ex.getClass().getSimpleName(), ex);
      return null;
    }
  }

  @Override
  public void claim(int mapId) {
    MapRuntime<ItemStack> current = runtime;
    MapView view = view(mapId);
    if (view == null || current == null || !current.isActive()) {
      return;
    }

    synchronized (view) {
      if (!clearRenderers(view)) {
        return;
      }

      BukkitMapPipe pipe = new BukkitMapPipe(current, this, mapId);
      boolean added = false;
      try {
        view.addRenderer(pipe);
        added = true;
        BukkitMapPipe previous = pipes.put(mapId, pipe);
        if (previous != null && previous != pipe) {
          previous.close();
        }
        if (!view.isLocked()) {
          view.setLocked(true);
        }
      } finally {
        if (!added || !current.isActive()) {
          detach(view, pipe);
        }
      }
    }
  }

  @Override
  public void release(int mapId) {
    BukkitMapPipe pipe = pipes.remove(mapId);
    if (pipe == null) {
      return;
    }

    MapView view = view(mapId);
    if (view == null) {
      pipe.close();
      return;
    }
    detach(view, pipe);
  }

  @Override
  public boolean isClaimed(int mapId) {
    BukkitMapPipe pipe = pipes.get(mapId);
    MapView view = view(mapId);
    if (pipe == null || view == null || !pipe.isActive() || !pipe.isOwnedBy(runtime)) {
      return false;
    }

    List<MapRenderer> renderers = view.getRenderers();
    return renderers.size() == 1 && renderers.get(0) == pipe;
  }

  public void releaseAll() {
    for (Integer mapId : new ArrayList<>(pipes.keySet())) {
      release(mapId);
    }
    pipes.clear();
  }

  private boolean clearRenderers(MapView view) {
    boolean cleared = true;
    for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
      if (renderer instanceof BukkitMapPipe pipe) {
        pipe.close();
      }

      try {
        view.removeRenderer(renderer);
        if (renderer instanceof BukkitMapPipe pipe) {
          pipes.remove(pipe.mapId(), pipe);
        }
      } catch (Throwable e) {
        cleared = false;
        String rendererClass = renderer == null ? "unknown" : renderer.getClass().getSimpleName();
        React.warn("Map renderer cleanup failed: mapId=" + view.getId()
            + " renderer=" + rendererClass
            + " cause=" + summarize(e)
            + " action=kept-existing-renderer", e);
      }
    }
    return cleared;
  }

  private void detach(MapView view, BukkitMapPipe pipe) {
    pipe.close();
    try {
      synchronized (view) {
        view.removeRenderer(pipe);
      }
    } catch (Throwable e) {
      React.reportError(new IllegalStateException("Failed to detach React map renderer pipe during controller lifecycle cleanup", e));
    } finally {
      pipes.remove(pipe.mapId(), pipe);
    }
  }

  @Override
  public void publish(int mapId, byte[] surface, DirtyRect changed) {
  }

  @Override
  public void send(UUID viewerId, int mapId, byte[] surface, DirtyRect rect) {
    Player player = Bukkit.getPlayer(viewerId);
    MapView view = view(mapId);
    if (player != null && view != null) {
      player.sendMap(view);
    }
  }

  @Override
  public boolean pullsHeldMaps() {
    return true;
  }

  // ---- items ----------------------------------------------------------------------------

  @Override
  public MapItemView readMapItem(ItemStack item) {
    if (item == null || item.getType() != Material.FILLED_MAP || !(item.getItemMeta() instanceof MapMeta meta)) {
      return null;
    }

    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    byte flag = pdc.getOrDefault(nsReact, PersistentDataType.BYTE, (byte) 0);
    MapItemTags tags = new MapItemTags(
        flag == 1,
        pdc.get(nsRenderer, PersistentDataType.STRING),
        pdc.get(nsMapToken, PersistentDataType.STRING)
    );
    MapView view = meta.hasMapView() ? meta.getMapView() : null;
    int mapId = view == null ? -1 : view.getId();
    return new MapItemView(mapId, tags, meta.getLore(), meta.getDisplayName() != null);
  }

  @Override
  public ItemStack writeMapItem(ItemStack item, int mapId, MapItemTags tags, List<String> lore) {
    if (item == null || !(item.getItemMeta() instanceof MapMeta meta)) {
      return item;
    }

    if (mapId >= 0) {
      MapView current = meta.hasMapView() ? meta.getMapView() : null;
      if (current == null || current.getId() != mapId) {
        MapView view = view(mapId);
        if (view != null) {
          meta.setMapView(view);
        }
      }
    }

    if (meta.getDisplayName() != null) {
      // Avoid item-frame hover popups obscuring nearby maps by not naming the item.
      meta.setDisplayName(null);
    }
    if (!Objects.equals(meta.getLore(), lore)) {
      meta.setLore(lore);
    }

    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    if (tags.reactFlag()) {
      pdc.set(nsReact, PersistentDataType.BYTE, (byte) 1);
    }
    if (tags.rendererId() != null) {
      pdc.set(nsRenderer, PersistentDataType.STRING, tags.rendererId());
    }
    if (tags.token() != null) {
      pdc.set(nsMapToken, PersistentDataType.STRING, tags.token());
    }
    item.setItemMeta(meta);
    return item;
  }

  @Override
  public ItemStack newMapItem(int mapId, MapItemTags tags, List<String> lore) {
    return writeMapItem(new ItemStack(Material.FILLED_MAP), mapId, tags, lore);
  }

  // ---- inventories ----------------------------------------------------------------------

  @Override
  public void giveToMainHand(UUID viewerId, ItemStack item) {
    Player player = Bukkit.getPlayer(viewerId);
    if (player == null || item == null) {
      return;
    }

    ItemStack held = player.getInventory().getItemInMainHand();
    ItemStack displaced = held.getType() == Material.AIR ? null : held.clone();
    player.getInventory().setItemInMainHand(item);
    if (displaced != null) {
      addOrDrop(player, displaced);
    }
  }

  @Override
  public void giveToInventory(UUID viewerId, ItemStack item) {
    Player player = Bukkit.getPlayer(viewerId);
    if (player != null && item != null) {
      addOrDrop(player, item);
    }
  }

  private static void addOrDrop(Player player, ItemStack item) {
    for (ItemStack overflow : player.getInventory().addItem(item).values()) {
      player.getWorld().dropItem(player.getLocation(), overflow);
    }
  }

  @Override
  public void rewriteInventoryMaps(UUID viewerId, Function<ItemStack, ItemStack> rewrite) {
    Player player = Bukkit.getPlayer(viewerId);
    if (player == null) {
      return;
    }

    ItemStack[] contents = player.getInventory().getContents();
    boolean updated = false;
    for (int i = 0; i < contents.length; i++) {
      ItemStack item = contents[i];
      if (item == null) {
        continue;
      }

      ItemStack replacement = rewrite.apply(item);
      if (replacement != null) {
        contents[i] = replacement;
        updated = true;
      }
    }

    if (updated) {
      player.getInventory().setContents(contents);
    }
  }

  @Override
  public boolean moveToMainHand(UUID viewerId, Predicate<ItemStack> match) {
    Player player = Bukkit.getPlayer(viewerId);
    if (player == null) {
      return false;
    }

    PlayerInventory inventory = player.getInventory();
    ItemStack found = firstMatch(inventory, match);
    if (found == null) {
      return false;
    }
    if (match.test(inventory.getItemInMainHand())) {
      return true;
    }

    inventory.remove(found);
    if (!inventory.getItemInMainHand().getType().equals(Material.AIR)) {
      ItemStack displaced = inventory.getItemInMainHand();
      inventory.setItemInMainHand(found);
      addOrDrop(player, displaced);
    } else {
      inventory.setItemInMainHand(found);
    }
    return true;
  }

  public static ItemStack firstMatch(PlayerInventory inventory, Predicate<ItemStack> match) {
    if (match.test(inventory.getItemInMainHand())) {
      return inventory.getItemInMainHand();
    }
    if (match.test(inventory.getItemInOffHand())) {
      return inventory.getItemInOffHand();
    }
    for (ItemStack item : inventory.getContents()) {
      if (match.test(item)) {
        return item;
      }
    }
    return null;
  }

  // ---- frames -----------------------------------------------------------------------------

  @Override
  public boolean framesInChunk(UUID worldId, int chunkX, int chunkZ, Consumer<FrameSnapshot<ItemStack>> sink) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
      return false;
    }

    for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
      if (entity instanceof ItemFrame frame) {
        sink.accept(snapshotOf(frame, true));
      }
    }
    return true;
  }

  @Override
  public FrameSnapshot<ItemStack> frame(UUID frameId, boolean withItem) {
    Entity entity = entity(frameId);
    if (!(entity instanceof ItemFrame frame) || !frame.isValid() || frame.getWorld() == null) {
      return null;
    }
    return snapshotOf(frame, withItem);
  }

  @Override
  public boolean frameExists(UUID frameId) {
    return entity(frameId) != null;
  }

  public FrameSnapshot<ItemStack> snapshotOf(ItemFrame frame, boolean withItem) {
    Location location = frame.getLocation();
    return new FrameSnapshot<ItemStack>(
        frame.getUniqueId(),
        frame.getWorld().getUID(),
        location.getBlockX(),
        location.getBlockY(),
        location.getBlockZ(),
        location.getX(),
        location.getY(),
        location.getZ(),
        facing(frame.getFacing()),
        frame.getRotation().ordinal(),
        withItem ? frame.getItem() : null,
        frame
    );
  }

  static Facing facing(BlockFace face) {
    if (face == null) {
      return null;
    }
    return switch (face) {
      case NORTH -> Facing.NORTH;
      case SOUTH -> Facing.SOUTH;
      case EAST -> Facing.EAST;
      case WEST -> Facing.WEST;
      case UP -> Facing.UP;
      case DOWN -> Facing.DOWN;
      default -> null;
    };
  }

  @Override
  public void setFrameItemQuietly(FrameSnapshot<ItemStack> snapshot, ItemStack item) {
    Object handle = snapshot.handle();
    ItemFrame frame = handle instanceof ItemFrame itemFrame ? itemFrame : (entity(snapshot.frameId()) instanceof ItemFrame found ? found : null);
    if (frame == null) {
      return;
    }

    if (!silentSetItemResolved) {
      silentSetItemResolved = true;
      try {
        silentSetItem = ItemFrame.class.getMethod("setItem", ItemStack.class, boolean.class);
      } catch (Throwable ex) {
        React.verbose(() -> "Silent ItemFrame#setItem(ItemStack, boolean) not available: " + summarize(ex), ex);
        silentSetItem = null;
      }
    }

    if (silentSetItem != null) {
      try {
        silentSetItem.invoke(frame, item, false);
        return;
      } catch (Throwable ex) {
        React.verbose(() -> "Failed invoking silent ItemFrame setter, falling back: " + summarize(ex), ex);
      }
    }

    frame.setItem(item);
  }

  @Override
  public void setFrameRotation(UUID frameId, int rotation) {
    if (entity(frameId) instanceof ItemFrame frame && frame.isValid()) {
      frame.setRotation(Rotation.values()[Math.floorMod(rotation, 8)]);
    }
  }

  private Entity entity(UUID id) {
    if (id == null) {
      return null;
    }
    try {
      return Bukkit.getEntity(id);
    } catch (Throwable ex) {
      React.verbose(() -> "Failed to resolve tracked map frame entity " + id + ": " + summarize(ex), ex);
      return null;
    }
  }

  // ---- worlds and viewers -------------------------------------------------------------------

  @Override
  public boolean isWorldLoaded(UUID worldId) {
    return worldId != null && Bukkit.getWorld(worldId) != null;
  }

  @Override
  public UUID defaultWorld() {
    List<World> worlds = Bukkit.getWorlds();
    return worlds.isEmpty() ? null : worlds.get(0).getUID();
  }

  @Override
  public void loadedChunks(Consumer<ChunkRef> sink) {
    for (World world : Bukkit.getWorlds()) {
      UUID worldId = world.getUID();
      for (Chunk chunk : world.getLoadedChunks()) {
        if (chunk != null) {
          sink.accept(new ChunkRef(worldId, chunk.getX(), chunk.getZ()));
        }
      }
    }
  }

  @Override
  public List<ViewerSnapshot> viewers() {
    List<ViewerSnapshot> viewers = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      ViewerSnapshot snapshot = snapshotOf(player);
      if (snapshot != null) {
        viewers.add(snapshot);
      }
    }
    return viewers;
  }

  public ViewerSnapshot snapshotOf(Player player) {
    if (player == null || player.getWorld() == null) {
      return null;
    }

    Location location = player.getLocation();
    if (location.getWorld() == null) {
      return null;
    }

    Location eye = player.getEyeLocation();
    org.bukkit.util.Vector direction = eye.getDirection();
    double dirX = direction.getX();
    double dirY = direction.getY();
    double dirZ = direction.getZ();
    double lengthSquared = (dirX * dirX) + (dirY * dirY) + (dirZ * dirZ);
    boolean hasDirection = lengthSquared > 1.0E-6D;
    if (hasDirection) {
      double inverse = 1.0D / Math.sqrt(lengthSquared);
      dirX *= inverse;
      dirY *= inverse;
      dirZ *= inverse;
    }

    return new ViewerSnapshot(
        player.getUniqueId(),
        location.getWorld().getUID(),
        location.getX(),
        location.getY(),
        location.getZ(),
        eye.getX(),
        eye.getY(),
        eye.getZ(),
        dirX,
        dirY,
        dirZ,
        hasDirection,
        mapIdOf(player.getInventory().getItemInMainHand()),
        mapIdOf(player.getInventory().getItemInOffHand())
    );
  }

  private static int mapIdOf(ItemStack item) {
    if (item == null || item.getType() != Material.FILLED_MAP || !(item.getItemMeta() instanceof MapMeta meta)) {
      return -1;
    }
    MapView view = meta.hasMapView() ? meta.getMapView() : null;
    return view == null ? -1 : view.getId();
  }

  @Override
  public UUID viewerWorld(UUID viewerId) {
    Player player = Bukkit.getPlayer(viewerId);
    return player == null || player.getWorld() == null ? null : player.getWorld().getUID();
  }

  @Override
  public boolean isOnline(UUID viewerId) {
    Player player = Bukkit.getPlayer(viewerId);
    return player != null && player.isOnline();
  }

  @Override
  public boolean hasLineOfSight(UUID viewerId, UUID frameId, UUID worldId, double x, double y, double z) {
    Player player = Bukkit.getPlayer(viewerId);
    if (player == null || player.getWorld() == null) {
      return false;
    }

    if (!J.isFoliaThreading()) {
      Entity frame = entity(frameId);
      return frame != null && player.hasLineOfSight(frame);
    }

    World world = player.getWorld();
    if (!world.getUID().equals(worldId)) {
      return false;
    }
    Location source = new Location(world, x, y, z);
    return J.isOwnedByCurrentRegion(source) && player.hasLineOfSight(source);
  }

  // ---- threading --------------------------------------------------------------------------

  @Override
  public boolean runOnFrameOwner(UUID frameId, long delayTicks, Runnable work, Runnable retired) {
    Entity entity = entity(frameId);
    if (entity == null) {
      return false;
    }

    if (delayTicks <= 0L && (!J.isFoliaThreading() || J.isOwnedByCurrentRegion(entity))) {
      work.run();
      return true;
    }
    return J.runEntity(entity, work, (int) Math.max(0L, delayTicks), retired);
  }

  @Override
  public boolean runOnViewerOwner(UUID viewerId, long delayTicks, Runnable work) {
    Player player = Bukkit.getPlayer(viewerId);
    if (player == null) {
      return false;
    }

    if (delayTicks <= 0L && (!J.isFoliaThreading() || J.isOwnedByCurrentRegion(player))) {
      work.run();
      return true;
    }
    return J.runEntity(player, work, (int) Math.max(0L, delayTicks));
  }

  @Override
  public boolean runOnChunkOwner(UUID worldId, int chunkX, int chunkZ, Runnable work) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    if (world == null) {
      return false;
    }

    if (!J.isFoliaThreading()) {
      work.run();
      return true;
    }

    Location anchor = new Location(world, (chunkX << 4) + 8.0D, 64.0D, (chunkZ << 4) + 8.0D);
    if (J.isOwnedByCurrentRegion(anchor)) {
      work.run();
      return true;
    }
    return J.runChunk(world, chunkX, chunkZ, work);
  }

  // ---- events -------------------------------------------------------------------------------

  @Override
  public void bind(MapHostListener<ItemStack> listener) {
    unbind();
    if (plugin == null || listener == null) {
      return;
    }

    eventListener = new Events(listener);
    Bukkit.getPluginManager().registerEvents(eventListener, plugin);
    registerFrameChangeListener(listener);
  }

  private void registerFrameChangeListener(MapHostListener<ItemStack> listener) {
    // Capability probe: the Paper frame-change event is the only one that fires on the actual
    // place/remove/rotate gesture. Never gate this on a server version string.
    String probeFailure = null;
    try {
      Class.forName("io.papermc.paper.event.player.PlayerItemFrameChangeEvent");
    } catch (Throwable ex) {
      probeFailure = ex.getClass().getSimpleName();
    }

    if (probeFailure != null) {
      React.verbose("Item-frame change events unavailable (" + probeFailure + "); using interact/damage fallback.");
      return;
    }

    frameChangeListener = new MapPaperFrameChangeListener(frame -> listener.onFrameChanged(frame.getUniqueId()));
    Bukkit.getPluginManager().registerEvents(frameChangeListener, plugin);
    frameChangeEventsAvailable = true;
  }

  @Override
  public void unbind() {
    if (eventListener != null) {
      HandlerList.unregisterAll(eventListener);
      eventListener = null;
    }
    if (frameChangeListener != null) {
      HandlerList.unregisterAll(frameChangeListener);
      frameChangeListener = null;
    }
    frameChangeEventsAvailable = false;
  }

  private static String summarize(Throwable throwable) {
    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String message = root.getMessage();
    return message == null || message.isBlank() ? root.getClass().getSimpleName() : root.getClass().getSimpleName() + ": " + message;
  }

  private final class Events implements Listener {
    private final MapHostListener<ItemStack> listener;

    private Events(MapHostListener<ItemStack> listener) {
      this.listener = listener;
    }

    @EventHandler
    public void on(PlayerJoinEvent e) {
      listener.onViewerJoin(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void on(PlayerTeleportEvent e) {
      if (e.getTo() == null || e.getFrom() == null || e.getFrom().getWorld() == null || e.getTo().getWorld() == null) {
        return;
      }
      if (!e.getFrom().getWorld().equals(e.getTo().getWorld())) {
        listener.onViewerChangedWorld(e.getPlayer().getUniqueId(), e.getTo().getWorld().getUID());
      }
    }

    @EventHandler
    public void on(EntitiesLoadEvent e) {
      // Chunk entities are not populated at ChunkLoadEvent time on modern Paper; this handler already runs
      // on the owning region thread, so frames are refreshed inline.
      Chunk chunk = e.getChunk();
      List<FrameSnapshot<ItemStack>> frames = new ArrayList<>();
      for (Entity entity : e.getEntities()) {
        if (entity instanceof ItemFrame frame) {
          frames.add(snapshotOf(frame, true));
        }
      }
      listener.onFramesLoaded(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ(), frames);
    }

    @EventHandler
    public void on(EntitiesUnloadEvent e) {
      for (Entity entity : e.getEntities()) {
        if (entity instanceof ItemFrame frame) {
          listener.onFrameUnloaded(frame.getUniqueId());
        }
      }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ChunkLoadEvent e) {
      listener.onChunkLoaded(e.getWorld().getUID(), e.getChunk().getX(), e.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ChunkUnloadEvent e) {
      listener.onChunkUnloaded(e.getWorld().getUID(), e.getChunk().getX(), e.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(WorldUnloadEvent e) {
      listener.onWorldUnloaded(e.getWorld().getUID());
    }

    @EventHandler(ignoreCancelled = true)
    public void on(HangingBreakEvent e) {
      if (e.getEntity() instanceof ItemFrame frame) {
        listener.onFrameBroken(frame.getUniqueId());
      }
    }

    @EventHandler(ignoreCancelled = true)
    public void on(PlayerInteractEntityEvent e) {
      if (!frameChangeEventsAvailable && e.getRightClicked() instanceof ItemFrame frame) {
        listener.onFrameChanged(frame.getUniqueId());
      }
    }

    @EventHandler(ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent e) {
      if (!frameChangeEventsAvailable && e.getEntity() instanceof ItemFrame frame) {
        listener.onFrameChanged(frame.getUniqueId());
      }
    }
  }
}
