/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.rendering.MapRendererPipe;
import art.arcane.react.api.rendering.MegamapDuplicateSplitter;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.RendererAdaptMetrics;
import art.arcane.react.api.rendering.RendererBiletoolsMetrics;
import art.arcane.react.api.rendering.RendererHiddenoreMetrics;
import art.arcane.react.api.rendering.RendererHolouiMetrics;
import art.arcane.react.api.rendering.RendererIrisMetrics;
import art.arcane.react.api.rendering.RendererIrisWorldMetrics;
import art.arcane.react.api.rendering.RendererReactMetrics;
import art.arcane.react.api.rendering.RendererUnknown;
import art.arcane.react.api.rendering.RendererWormholesMetrics;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.feature.FeatureUnknown;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@EqualsAndHashCode(callSuper = true)
@Data
@ConfigDescription("Manages React map item metadata, renderer repair, and live map packet delivery for inventories and item frames.")
public class MapController extends TickedObject implements IController, Listener {
  private static final NamespacedKey nsReact = new NamespacedKey(React.instance, "react");
  private static final NamespacedKey nsRenderer = new NamespacedKey(React.instance, "react-renderer");
  private static final NamespacedKey nsMapToken = new NamespacedKey(React.instance, "react-map-token");
  private static final Set<String> disabledRendererIds = Set.of(
      "iris-biome-chunk-share-pie-map"
  );
  private static final int megamapSplitsPerPass = 8;
  private static final int megamapRotationResetsPerPass = 8;
  @ConfigDoc(value = "Main maintenance cadence for map repair logic in milliseconds.", impact = "Lower values repair inventories and item-frames sooner after reload; higher values reduce maintenance overhead.")
  private long maintenanceTickIntervalMs = 500L;
  @ConfigDoc(value = "Cadence for scanning player inventories for React map repair in milliseconds.", impact = "Lower values repair map metadata and renderers faster; higher values reduce per-tick work.")
  private long inventoryRepairCadenceMs = 250L;
  @ConfigDoc(value = "Number of online player inventories repaired per maintenance pass.", impact = "Higher values reduce recovery time after reload; lower values reduce work each pass.")
  private int inventoryRepairBatchSize = 3;
  @ConfigDoc(value = "Cadence for scanning loaded chunks for item-frame map repair in milliseconds.", impact = "Lower values fix item-frame maps faster after reload; higher values reduce maintenance overhead.")
  private long itemFrameRepairCadenceMs = 250L;
  @ConfigDoc(value = "Number of loaded chunks scanned for item-frame maps per maintenance pass.", impact = "Higher values speed up frame-map recovery after reload; lower values reduce per-pass cost.")
  private int itemFrameChunkBatchSize = 8;
  @ConfigDoc(value = "Warmup duration after startup/reload where map recovery uses aggressive repair batching (milliseconds).", impact = "Higher values prioritize fast post-reload recovery longer; lower values return to normal cadence sooner.")
  private long startupBoostDurationMs = 12_000L;
  @ConfigDoc(value = "Inventory repair batch size while startup warmup is active.", impact = "Higher values make map inventories recover faster right after reload at the cost of extra temporary work.")
  private int startupBoostInventoryBatchSize = 12;
  @ConfigDoc(value = "Item-frame chunk repair batch size while startup warmup is active.", impact = "Higher values make frame maps recover faster right after reload at the cost of extra temporary work.")
  private int startupBoostItemFrameChunkBatchSize = 32;
  @ConfigDoc(value = "Packet push interval for active item-frame maps in milliseconds.", impact = "Lower values refresh map visuals faster but increase packet and render pressure.")
  private long frameMapPushIntervalMs = 600L;
  @ConfigDoc(value = "Packet push interval used during startup warmup in milliseconds.", impact = "Lower values make frame maps appear faster after reload; higher values reduce temporary packet bursts during warmup.")
  private long startupFrameMapPushIntervalMs = 100L;
  @ConfigDoc(value = "Packet push interval for nearby viewers who are not actively looking at the frame map (milliseconds).", impact = "Higher values reduce idle packet volume when players are nearby but not watching the map.")
  private long frameMapIdlePushIntervalMs = 6_000L;
  @ConfigDoc(value = "Maximum block radius from an item-frame map where players receive push updates.", impact = "Higher values cover more viewers but increase packet fanout.")
  private double frameMapPushRadiusBlocks = 24D;
  @ConfigDoc(value = "Allows held-map updates outside frameMapPushRadiusBlocks.", impact = "Disable to hard-stop map packets outside radius; enable if holding should bypass distance checks.")
  private boolean frameMapPushOutsideRangeWhenHolding = false;
  @ConfigDoc(value = "Minimum view-direction dot product required to treat a player as actively looking at a frame map.", impact = "Higher values require tighter aim at the map before high-frequency updates are sent.")
  private double frameMapLookDotThreshold = 0.45D;
  @ConfigDoc(value = "If enabled, frame map pushes require line-of-sight for nearby viewers (holders bypass this).", impact = "Enabling eliminates packets to occluded viewers but adds lightweight visibility checks.")
  private boolean frameMapRequireLineOfSight = true;
  @ConfigDoc(value = "Retention duration for frame-map push bookkeeping in milliseconds.", impact = "Higher values keep state longer with less churn; lower values prune bookkeeping more aggressively.")
  private long frameMapPushStateRetentionMs = 600_000L;
  @ConfigDoc(value = "Minimum interval between canvas redraws for held React maps in milliseconds.", impact = "Lower values rotate and refresh held maps more smoothly; higher values cut render work when many players view the same map.")
  private long heldMapRedrawIntervalMs = 150L;
  @ConfigDoc(value = "Minimum interval between canvas redraws for item-frame React maps in milliseconds.", impact = "Lower values refresh frame dashboards faster; higher values collapse redundant per-viewer redraws on busy map walls.")
  private long frameMapRedrawIntervalMs = 300L;
  @ConfigDoc(value = "Interval between full item-stack revalidations of tracked frame maps in milliseconds.", impact = "Lower values detect swapped frame items sooner; higher values avoid repeated item metadata reads every maintenance tick.")
  private long frameMapValidateIntervalMs = 2_000L;
  @ConfigDoc(value = "Combines adjacent item-frame React maps with the same renderer into one large tiled megamap display.", impact = "Disable to render every frame map as an independent 128x128 dashboard.")
  private boolean megamapEnabled = true;
  @ConfigDoc(value = "Total tile budget for one combined megamap wall.", impact = "Walls larger than the budget fall back to zoomed single-dashboard rendering.")
  private int megamapMaxTiles = 32;
  @ConfigDoc(value = "Cloned React maps placed in item frames are automatically given fresh map ids so adjacent copies can combine into a megamap wall.", impact = "Disable to keep cloned maps sharing one picture and never combining.")
  private boolean megamapSplitDuplicates = true;
  private transient volatile MegamapGrid.MegamapSolution megamapSolution;
  private transient AtomicInteger megamapStateVersion;
  private transient int megamapSolvedVersion;
  private transient boolean megamapSolvedEnabled;
  private transient Map<String, ReactRenderer> renderers;
  private transient Map<String, ReactRenderer> resolvedRenderers;
  private transient Map<String, RendererMetadata> rendererMetadata;
  private transient Map<FramePushKey, Long> frameMapPushMsByViewerKey;
  private transient Map<Integer, Long> frameMapLastSeenByMapId;
  private transient Map<UUID, ActiveFrameMap> activeFrameMaps;
  private transient Map<Integer, UUID> frameIdsByMapId;
  private transient Set<UUID> megamapSplitsInFlight;
  // Declared as Listener so the Paper-only listener type never leaks into a generated
  // accessor signature that reflection would resolve on Spigot.
  private transient Listener frameChangeListener;
  private transient boolean frameChangeEventsAvailable;
  private transient AtomicBoolean maintenanceTickQueued;
  private transient Method itemFrameSetItemSilentMethod;
  private transient boolean itemFrameSetItemSilentMethodResolved;
  private transient long lastInventoryRepairMs;
  private transient long lastItemFrameRepairMs;
  private transient int inventoryRepairCursor;
  private transient int itemFrameWorldCursor;
  private transient int itemFrameChunkCursor;
  private transient List<ReactRenderer> irisMetricsRenderers;
  private transient ReactRenderer adaptMetricsRenderer;
  private transient ReactRenderer wormholesMetricsRenderer;
  private transient ReactRenderer holouiMetricsRenderer;
  private transient ReactRenderer hiddenoreMetricsRenderer;
  private transient ReactRenderer biletoolsMetricsRenderer;
  private transient ReactRenderer reactMetricsRenderer;
  private transient long startupBoostUntilMs;

  public MapController() {
    super("react", "map", 250);
  }

  public void updateMapView(MapView view, ReactRenderer newRenderer) {
    clearRenderers(view);
    view.addRenderer(new MapRendererPipe(newRenderer));
  }

  public MapView createView(World world, ReactRenderer renderer) {
    MapView view = Bukkit.createMap(world);
    clearRenderers(view);
    view.addRenderer(new MapRendererPipe(renderer));
    return view;
  }

  public ReactRenderer getRenderer(ItemStack item) {
    if (isReactMap(item)) {
      MapMeta meta = (MapMeta) item.getItemMeta();
      if (meta == null) {
        return resolveRenderer(FeatureUnknown.ID);
      }

      return resolveRenderer(getStoredRendererId(meta));
    }

    return resolveRenderer(FeatureUnknown.ID);
  }

  public ReactRenderer getRendererById(String rendererId) {
    return resolveRenderer(rendererId);
  }

  public void setRenderer(Player player, ReactRenderer renderer) {
    if (hasReactMap(player)) {
      setRenderer(getReactMap(player), renderer, player.getWorld());
    }
  }

  public void setRenderer(ItemStack map, ReactRenderer renderer) {
    setRenderer(map, renderer, null);
  }

  private void setRenderer(ItemStack map, ReactRenderer renderer, World worldHint) {
    if (renderer == null || !isReactMap(map)) {
      return;
    }

    MapMeta meta = (MapMeta) map.getItemMeta();
    if (meta == null) {
      return;
    }

    MapView view = meta.getMapView();
    if (view != null && view.getWorld() != null) {
      updateMapView(view, renderer);
    } else {
      World world = worldHint;
      if (world == null && !Bukkit.getWorlds().isEmpty()) {
        world = Bukkit.getWorlds().get(0);
      }
      if (world != null) {
        meta.setMapView(createView(world, renderer));
      }
    }

    applyRendererMetadata(meta, renderer);
    map.setItemMeta(meta);
  }

  public boolean hasReactMap(Player player) {
    return getReactMap(player) != null;
  }

  public ItemStack getReactMap(Player player) {
    if (isReactMap(player.getInventory().getItemInMainHand())) {
      return player.getInventory().getItemInMainHand();
    }

    if (isReactMap(player.getInventory().getItemInOffHand())) {
      return player.getInventory().getItemInOffHand();
    }

    for (ItemStack i : player.getInventory().getContents()) {
      if (isReactMap(i)) {
        return i;
      }
    }

    return null;
  }

  public void switchToMap(Player player) {
    if (hasReactMap(player)) {
      if (!isReactMap(player.getInventory().getItemInMainHand())) {
        ItemStack is = getReactMap(player);
        player.getInventory().remove(getReactMap(player));

        if (!player.getInventory().getItemInMainHand().getType().equals(Material.AIR)) {
          ItemStack iss = player.getInventory().getItemInMainHand();
          player.getInventory().setItemInMainHand(is);

          for (ItemStack i : player.getInventory().addItem(iss).values()) {
            player.getWorld().dropItem(player.getLocation(), i);
          }
        } else {
          player.getInventory().setItemInMainHand(is);
        }
      }
    }
  }

  public void openRenderer(Player player, ReactRenderer renderer) {
    if (renderer == null) {
      return;
    }

    giveMap(player, renderer);
  }

  public void giveMap(Player player, ReactRenderer renderer) {
    if (renderer == null || player == null) {
      return;
    }

    if (!player.getInventory().getItemInMainHand().getType().equals(Material.AIR)) {
      for (ItemStack i : player.getInventory().addItem(player.getInventory().getItemInMainHand()).values()) {
        player.getWorld().dropItem(player.getLocation(), i);
      }

      player.getInventory().setItemInMainHand(null);
    }

    player.getInventory().setItemInMainHand(createMap(player.getWorld(), renderer));
  }

  public void giveMapToInventory(Player player, ReactRenderer renderer) {
    if (renderer == null || player == null) {
      return;
    }

    ItemStack map = createMap(player.getWorld(), renderer);
    for (ItemStack overflow : player.getInventory().addItem(map).values()) {
      player.getWorld().dropItem(player.getLocation(), overflow);
    }
  }

  public boolean isReactMap(ItemStack item) {
    return reactMapMeta(item) != null;
  }

  public void updateMapViews(Player player, boolean force) {
    updateMapViews(player, player.getWorld(), force);
  }

  public void updateMapViews(Player player, World world, boolean force) {
    ItemStack[] is = player.getInventory().getContents();
    boolean updated = false;
    for (int i = 0; i < is.length; i++) {
      ItemStack item = is[i];

      if (item == null) {
        continue;
      }

      if (repairMapItem(item, world, force)) {
        updated = true;
      }
    }

    if (updated) {
      player.getInventory().setContents(is);
    }
  }

  @EventHandler
  public void on(PlayerJoinEvent e) {
    join(e.getPlayer());
  }

  public void join(Player p) {
    updateMapViews(p, true);
  }

  public Location resolveMapAnchor(MapView view, Player viewerFallback) {
    Location anchor = findFrameAnchor(view);
    if (anchor != null) {
      return anchor;
    }

    if (viewerFallback != null && viewerFallback.getWorld() != null) {
      return viewerFallback.getLocation().clone();
    }

    if (view != null && view.getWorld() != null) {
      return view.getWorld().getSpawnLocation().clone();
    }

    return null;
  }

  public boolean shouldRenderForPlayer(MapView view, Player player) {
    if (view == null || player == null || player.getWorld() == null) {
      return false;
    }

    Integer mapId = mapIdOf(view);
    if (mapId == null) {
      return false;
    }

    long now = System.currentTimeMillis();
    Location anchor = findFrameAnchor(view);
    if (anchor == null || anchor.getWorld() == null) {
      if (isKnownFrameMap(mapId, now)) {
        boolean holding = isHoldingMap(player, mapId);
        return holding && frameMapPushOutsideRangeWhenHolding;
      }

      return true;
    }

    boolean holding = isHoldingMap(player, mapId);
    boolean holdingBypassesRange = holding && frameMapPushOutsideRangeWhenHolding;
    if (!holdingBypassesRange) {
      if (!anchor.getWorld().equals(player.getWorld())) {
        return false;
      }

      if (player.getLocation().distanceSquared(anchor) > effectiveFrameMapPushRadiusSq()) {
        return false;
      }
    }

    return true;
  }

  public boolean hasFrameAnchor(MapView view) {
    return findFrameAnchor(view) != null;
  }

  public long redrawIntervalMsFor(MapView view) {
    Integer mapId = mapIdOf(view);
    if (mapId != null && isKnownFrameMap(mapId, System.currentTimeMillis())) {
      return Math.max(0L, frameMapRedrawIntervalMs);
    }

    return Math.max(0L, heldMapRedrawIntervalMs);
  }

  @EventHandler
  public void on(PlayerTeleportEvent e) {
    if (e.getTo() == null || e.getFrom() == null || e.getFrom().getWorld() == null || e.getTo().getWorld() == null) {
      return;
    }

    if (!e.getFrom().getWorld().equals(e.getTo().getWorld())) {
      updateMapViews(e.getPlayer(), e.getTo().getWorld(), false);
    }
  }

  @EventHandler
  public void on(EntitiesLoadEvent e) {
    // Chunk entities are not populated at ChunkLoadEvent time on modern Paper; this
    // handler already runs on the owning region thread, so refresh frames inline.
    Supplier<FrameMapViewerSnapshot> viewerSnapshot = memoizedViewerSnapshot();
    for (Entity entity : e.getEntities()) {
      if (entity instanceof ItemFrame frame) {
        refreshItemFrame(frame, true, viewerSnapshot);
      }
    }
  }

  @EventHandler
  public void on(EntitiesUnloadEvent e) {
    if (activeFrameMaps == null || activeFrameMaps.isEmpty()) {
      return;
    }

    for (Entity entity : e.getEntities()) {
      if (entity instanceof ItemFrame frame) {
        untrackFrameMap(frame.getUniqueId());
      }
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void on(HangingBreakEvent e) {
    if (e.getEntity() instanceof ItemFrame frame) {
      untrackFrameMap(frame.getUniqueId());
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void on(PlayerInteractEntityEvent e) {
    if (frameChangeEventsAvailable || !(e.getRightClicked() instanceof ItemFrame frame)) {
      return;
    }

    scheduleFrameRefresh(frame);
  }

  @EventHandler(ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (frameChangeEventsAvailable || !(e.getEntity() instanceof ItemFrame frame)) {
      return;
    }

    scheduleFrameRefresh(frame);
  }

  public void scheduleFrameRefresh(ItemFrame frame) {
    if (frame == null) {
      return;
    }

    // Frame change events fire before the item lands, so re-read the frame one tick
    // later from its owning region.
    J.runEntity(frame, () -> refreshItemFrame(frame, false, memoizedViewerSnapshot()), 1);
  }

  private Supplier<FrameMapViewerSnapshot> memoizedViewerSnapshot() {
    return new Supplier<>() {
      private FrameMapViewerSnapshot snapshot;

      @Override
      public FrameMapViewerSnapshot get() {
        if (snapshot == null) {
          snapshot = buildFrameMapViewerSnapshot();
        }
        return snapshot;
      }
    };
  }

  public ItemStack createMap(World world, ReactRenderer renderer) {
    ItemStack item = new ItemStack(Material.FILLED_MAP);
    MapMeta meta = (MapMeta) item.getItemMeta();
    if (meta == null) {
      return item;
    }

    meta.setMapView(createView(world, renderer));
    applyRendererMetadata(meta, renderer);
    item.setItemMeta(meta);

    return item;
  }

  @Override
  public String getName() {
    return "Map";
  }

  @Override
  public void start() {
    renderers = new ConcurrentHashMap<>();
    resolvedRenderers = new ConcurrentHashMap<>();
    rendererMetadata = new ConcurrentHashMap<>();
    frameMapPushMsByViewerKey = new ConcurrentHashMap<>();
    frameMapLastSeenByMapId = new ConcurrentHashMap<>();
    activeFrameMaps = new ConcurrentHashMap<>();
    frameIdsByMapId = new ConcurrentHashMap<>();
    megamapSplitsInFlight = ConcurrentHashMap.newKeySet();
    frameChangeListener = null;
    frameChangeEventsAvailable = false;
    megamapSolution = MegamapGrid.MegamapSolution.EMPTY;
    megamapStateVersion = new AtomicInteger(1);
    megamapSolvedVersion = 0;
    megamapSolvedEnabled = !megamapEnabled;
    maintenanceTickQueued = new AtomicBoolean(false);
    itemFrameSetItemSilentMethod = null;
    itemFrameSetItemSilentMethodResolved = false;
    lastInventoryRepairMs = 0L;
    lastItemFrameRepairMs = 0L;
    inventoryRepairCursor = 0;
    itemFrameWorldCursor = 0;
    itemFrameChunkCursor = 0;
    renderers.put(FeatureUnknown.ID, new RendererUnknown());
    irisMetricsRenderers = new ArrayList<>();
    irisMetricsRenderers.addAll(RendererIrisMetrics.dashboards());
    irisMetricsRenderers.addAll(RendererIrisWorldMetrics.dashboards());
    adaptMetricsRenderer = new RendererAdaptMetrics();
    wormholesMetricsRenderer = new RendererWormholesMetrics();
    holouiMetricsRenderer = new RendererHolouiMetrics();
    hiddenoreMetricsRenderer = new RendererHiddenoreMetrics();
    biletoolsMetricsRenderer = new RendererBiletoolsMetrics();
    reactMetricsRenderer = new RendererReactMetrics();
    startupBoostUntilMs = System.currentTimeMillis() + Math.max(0L, startupBoostDurationMs);
    applyMaintenanceTickInterval();
  }

  @Override
  public void stop() {
    if (maintenanceTickQueued != null) {
      maintenanceTickQueued.set(false);
    }
    if (frameMapPushMsByViewerKey != null) {
      frameMapPushMsByViewerKey.clear();
    }
    if (frameMapLastSeenByMapId != null) {
      frameMapLastSeenByMapId.clear();
    }
    if (activeFrameMaps != null) {
      activeFrameMaps.clear();
    }
    if (frameIdsByMapId != null) {
      frameIdsByMapId.clear();
    }
    if (megamapSplitsInFlight != null) {
      megamapSplitsInFlight.clear();
    }
    if (frameChangeListener != null) {
      HandlerList.unregisterAll(frameChangeListener);
      frameChangeListener = null;
    }
    frameChangeEventsAvailable = false;
    if (resolvedRenderers != null) {
      resolvedRenderers.clear();
    }
    if (rendererMetadata != null) {
      rendererMetadata.clear();
    }
    megamapSolution = MegamapGrid.MegamapSolution.EMPTY;
    megamapSolvedVersion = 0;
  }

  @Override
  public void postStart() {
    startupBoostUntilMs = System.currentTimeMillis() + Math.max(0L, startupBoostDurationMs);
    for (Sampler i : React.controller(SampleController.class).getSamplers().all()) {
      if (isAbsentIntegrationRenderer(i)) {
        continue;
      }
      registerRenderer(i);
    }

    for (Feature i : React.controller(FeatureController.class).getFeatures().all()) {
      if (i instanceof ReactRenderer f) {
        if (isAbsentIntegrationRenderer(f)) {
          continue;
        }
        registerRenderer(f);
      }
    }

    syncIntegrationRenderers();

    for (Player i : Bukkit.getOnlinePlayers()) {
      if (!J.isFoliaThreading()) {
        join(i);
        continue;
      }

      J.runEntity(i, () -> join(i));
    }

    registerFrameChangeListener();
    refreshLoadedItemFrames();
  }

  private void registerFrameChangeListener() {
    // Capability probe: the Paper frame-change event is the only one that fires on the
    // actual place/remove/rotate gesture. Never gate this on a server version string.
    String probeFailure = null;
    try {
      Class.forName("io.papermc.paper.event.player.PlayerItemFrameChangeEvent");
    } catch (Throwable ex) {
      probeFailure = ex.getClass().getSimpleName();
    }

    if (probeFailure != null || React.instance == null) {
      React.verbose("Item-frame change events unavailable ("
          + (probeFailure == null ? "no plugin instance" : probeFailure)
          + "); using interact/damage fallback.");
      return;
    }

    frameChangeListener = new MapFrameChangeListener(this);
    Bukkit.getPluginManager().registerEvents(frameChangeListener, React.instance);
    frameChangeEventsAvailable = true;
  }

  @Override
  public void onTick() {
    applyMaintenanceTickInterval();
    Runnable maintenanceTick = () -> {
      try {
        // Building the viewer snapshot touches every online player; memoize lazily so
        // ticks with no frame-map work cost nothing per player.
        Supplier<FrameMapViewerSnapshot> viewerSnapshot = memoizedViewerSnapshot();
        syncIntegrationRenderers();
        repairOneOnlinePlayerInventory();
        pushTrackedFrameMaps(viewerSnapshot);
        repairOneLoadedChunkItemFrames(viewerSnapshot);
        if (rebuildMegamapTiles()) {
          reconcileMegamapDefects();
        }
      } finally {
        if (maintenanceTickQueued != null) {
          maintenanceTickQueued.set(false);
        }
      }
    };

    if (Bukkit.isPrimaryThread()) {
      maintenanceTick.run();
      return;
    }

    if (maintenanceTickQueued != null && maintenanceTickQueued.compareAndSet(false, true)) {
      J.sync(maintenanceTick);
    }
  }

  private void syncIntegrationRenderers() {
    registerRenderer(reactMetricsRenderer);
    syncIntegrationCapabilityRenderers("iris");
    syncIntegrationCapabilityRenderers("adapt");
    syncIntegrationCapabilityRenderers("wormholes");
    syncIntegrationCapabilityRenderers("holoui");
    syncIntegrationCapabilityRenderers("hiddenore");
    syncIntegrationCapabilityRenderers("biletools");
  }

  private void syncIntegrationCapabilityRenderers(String capability) {
    if (renderers == null || capability == null || capability.isBlank()) {
      return;
    }

    String normalizedCapability = capability.toLowerCase(Locale.ROOT).trim();
    String prefix = normalizedCapability + "-";
    boolean available = IntegrationCapabilitySupport.isCapabilityPresent(
        React.controller(IntegrationController.class),
        normalizedCapability
    );

    if (!available) {
      // Do not remove these renderer ids while integration is negotiating after reload.
      return;
    }

    for (ReactRenderer dashboard : integrationDashboardsFor(normalizedCapability)) {
      registerRenderer(dashboard);
    }

    SampleController sampleController = React.controller(SampleController.class);
    if (sampleController != null && sampleController.getSamplers() != null) {
      for (Sampler sampler : sampleController.getSamplers().all()) {
        if (sampler == null || !normalizeRendererId(sampler.getId()).startsWith(prefix)) {
          continue;
        }
        registerRenderer(sampler);
      }
    }

    FeatureController featureController = React.controller(FeatureController.class);
    if (featureController != null && featureController.getFeatures() != null) {
      for (Feature feature : featureController.getFeatures().all()) {
        if (!(feature instanceof ReactRenderer reactRenderer) || !normalizeRendererId(feature.getId()).startsWith(prefix)) {
          continue;
        }
        registerRenderer(reactRenderer);
      }
    }
  }

  private List<ReactRenderer> integrationDashboardsFor(String capability) {
    return switch (capability) {
      case "iris" -> irisMetricsRenderers == null ? List.of() : irisMetricsRenderers;
      case "adapt" -> adaptMetricsRenderer == null ? List.of() : List.of(adaptMetricsRenderer);
      case "wormholes" -> wormholesMetricsRenderer == null ? List.of() : List.of(wormholesMetricsRenderer);
      case "holoui" -> holouiMetricsRenderer == null ? List.of() : List.of(holouiMetricsRenderer);
      case "hiddenore" -> hiddenoreMetricsRenderer == null ? List.of() : List.of(hiddenoreMetricsRenderer);
      case "biletools" -> biletoolsMetricsRenderer == null ? List.of() : List.of(biletoolsMetricsRenderer);
      default -> List.of();
    };
  }

  private boolean isAbsentIntegrationRenderer(ReactRenderer renderer) {
    if (renderer == null || renderer.getId() == null) {
      return false;
    }

    String pluginId = IntegrationCapabilitySupport.integrationPluginFor(normalizeRendererId(renderer.getId()));
    if (pluginId == null) {
      return false;
    }

    return !IntegrationCapabilitySupport.isCapabilityPresent(
        React.controller(IntegrationController.class),
        pluginId
    );
  }

  public void registerRenderer(ReactRenderer renderer) {
    if (renderers == null || renderer == null || renderer.getId() == null) {
      return;
    }

    String normalized = normalizeRendererId(renderer.getId());
    if (disabledRendererIds.contains(normalized)) {
      if (renderers.remove(renderer.getId()) != null) {
        invalidateRendererCaches();
      }
      return;
    }

    if (renderers.put(renderer.getId(), renderer) != renderer) {
      invalidateRendererCaches();
    }
  }

  public void unregisterRenderer(String rendererId) {
    if (renderers == null || rendererId == null || rendererId.isBlank()) {
      return;
    }

    boolean removed = renderers.remove(rendererId) != null;
    removed |= renderers.remove(normalizeRendererId(rendererId)) != null;
    if (removed) {
      invalidateRendererCaches();
    }
  }

  private void invalidateRendererCaches() {
    if (resolvedRenderers != null) {
      resolvedRenderers.clear();
    }
  }

  private RendererMetadata metadataFor(String rendererId) {
    String id = Objects.toString(rendererId, FeatureUnknown.ID);
    if (rendererMetadata == null) {
      return buildRendererMetadata(id);
    }

    return rendererMetadata.computeIfAbsent(id, this::buildRendererMetadata);
  }

  private RendererMetadata buildRendererMetadata(String rendererId) {
    String normalized = normalizeRendererId(rendererId);
    return new RendererMetadata(
        normalized,
        "Renderer: " + rendererDisplayName(rendererId),
        "Scope: " + rendererScope(normalized),
        "ID: " + rendererId
    );
  }

  private boolean applyRendererMetadata(MapMeta meta, ReactRenderer renderer) {
    if (meta == null || renderer == null) {
      return false;
    }

    String storedId = getStoredRendererId(meta);
    String resolvedId = Objects.toString(renderer.getId(), FeatureUnknown.ID);
    String rendererId = resolvedId;
    if (isUnknownRendererId(resolvedId) && isSpecificRendererId(storedId)) {
      rendererId = storedId;
    }
    RendererMetadata metadata = metadataFor(rendererId);
    String previousToken = meta.getPersistentDataContainer().get(nsMapToken, PersistentDataType.STRING);
    String mapToken = getOrCreateMapToken(meta);
    List<String> lore = List.of(
        metadata.rendererLine(),
        metadata.scopeLine(),
        metadata.idLine(),
        "Tag: " + mapToken
    );

    boolean changed = false;
    if (meta.getDisplayName() != null) {
      // Avoid item-frame hover popups obscuring nearby maps by not naming the item.
      meta.setDisplayName(null);
      changed = true;
    }

    if (!Objects.equals(meta.getLore(), lore)) {
      meta.setLore(lore);
      changed = true;
    }

    if (!Objects.equals(previousToken, mapToken)) {
      changed = true;
    }

    byte flag = meta.getPersistentDataContainer().getOrDefault(nsReact, PersistentDataType.BYTE, (byte) 0);
    if (flag != 1) {
      meta.getPersistentDataContainer().set(nsReact, PersistentDataType.BYTE, (byte) 1);
      changed = true;
    }

    String storedPdcId = meta.getPersistentDataContainer().get(nsRenderer, PersistentDataType.STRING);
    if (!rendererId.equalsIgnoreCase(Objects.toString(storedPdcId, ""))) {
      meta.getPersistentDataContainer().set(nsRenderer, PersistentDataType.STRING, rendererId);
      changed = true;
    }

    if (storedPdcId == null || storedPdcId.isBlank()) {
      changed = true;
    }

    return changed;
  }

  private boolean repairMapItem(ItemStack item, World worldHint, boolean forceRendererUpdate) {
    MapMeta meta = reactMapMeta(item);
    if (meta == null) {
      return false;
    }

    boolean rekeyed = rekeyWallAssignedInventoryMap(meta, worldHint);
    if (!repairMapMeta(meta, worldHint, forceRendererUpdate) && !rekeyed) {
      return false;
    }

    item.setItemMeta(meta);
    return true;
  }

  // An inventory copy of a map id that is tiling a wall would show that wall's tile
  // fragment in hand; re-key the copy to its own fresh id so held maps always render
  // as a full standalone dashboard. Frame items never pass through this path.
  private boolean rekeyWallAssignedInventoryMap(MapMeta meta, World worldHint) {
    if (!megamapEnabled || !megamapSplitDuplicates) {
      return false;
    }

    MegamapGrid.MegamapSolution solution = megamapSolution;
    if (solution == null || solution.tiles().isEmpty()) {
      return false;
    }

    MapView view = meta.getMapView();
    Integer mapId = mapIdOf(view);
    if (mapId == null || solution.tiles().get(mapId) == null) {
      return false;
    }

    String storedRendererId = getStoredRendererId(meta);
    ReactRenderer renderer = resolveRenderer(storedRendererId);
    if (renderer == null) {
      return false;
    }

    // Never re-key behind an unresolved renderer; that would swap the dashboard.
    if (isSpecificRendererId(storedRendererId) && isUnknownRendererId(renderer.getId())) {
      return false;
    }

    World world = worldHint;
    if (world == null && view != null && view.getWorld() != null) {
      world = view.getWorld();
    }
    if (world == null && !Bukkit.getWorlds().isEmpty()) {
      world = Bukkit.getWorlds().get(0);
    }
    if (world == null) {
      return false;
    }

    MapView fresh = createView(world, renderer);
    meta.setMapView(fresh);
    applyRendererMetadata(meta, renderer);
    React.verbose("Megamap re-keyed inventory copy of wall map " + mapId + " -> " + mapIdOf(fresh));
    return true;
  }

  private MapMeta reactMapMeta(ItemStack item) {
    if (item == null || item.getType() != Material.FILLED_MAP) {
      return null;
    }

    if (!(item.getItemMeta() instanceof MapMeta meta)) {
      return null;
    }

    return isReactMapMeta(meta) ? meta : null;
  }

  private boolean isReactMapMeta(MapMeta meta) {
    if (meta.getPersistentDataContainer().getOrDefault(nsReact, PersistentDataType.BYTE, (byte) 0) == 1) {
      return true;
    }

    String rendererId = meta.getPersistentDataContainer().get(nsRenderer, PersistentDataType.STRING);
    if (isSpecificRendererId(rendererId)) {
      return true;
    }

    return isSpecificRendererId(parseRendererIdFromLore(meta.getLore()));
  }

  private boolean repairMapMeta(MapMeta meta, World worldHint, boolean forceRendererUpdate) {
    boolean changed = false;
    MapView view = meta.getMapView();
    String storedRendererId = getStoredRendererId(meta);
    if (isUnknownRendererId(storedRendererId) && view != null) {
      String liveRendererId = rendererIdFromView(view);
      if (isSpecificRendererId(liveRendererId)) {
        meta.getPersistentDataContainer().set(nsRenderer, PersistentDataType.STRING, liveRendererId);
        storedRendererId = liveRendererId;
        changed = true;
      }
    }

    ReactRenderer renderer = resolveRenderer(storedRendererId);
    if (renderer == null && renderers != null) {
      renderer = renderers.get(FeatureUnknown.ID);
    }
    if (renderer == null) {
      return false;
    }

    World world = worldHint;
    if (world == null && view != null && view.getWorld() != null) {
      world = view.getWorld();
    }
    if (world == null && !Bukkit.getWorlds().isEmpty()) {
      world = Bukkit.getWorlds().get(0);
    }

    boolean mapViewChanged = view == null || view.getWorld() == null || (world != null && !view.getWorld().equals(world));
    if (mapViewChanged) {
      if (world != null) {
        meta.setMapView(createView(world, renderer));
      }
    } else {
      ensureMapRenderer(view, renderer, forceRendererUpdate);
    }

    boolean metadataChanged = applyRendererMetadata(meta, renderer);
    return mapViewChanged || metadataChanged || changed;
  }

  private void ensureMapRenderer(MapView view, ReactRenderer renderer, boolean forceRendererUpdate) {
    if (view == null || renderer == null) {
      return;
    }

    if (forceRendererUpdate || !hasExpectedMapRenderer(view, renderer)) {
      updateMapView(view, renderer);
    }
  }

  private boolean hasExpectedMapRenderer(MapView view, ReactRenderer renderer) {
    if (view == null || renderer == null) {
      return false;
    }

    List<MapRenderer> mapRenderers = view.getRenderers();
    if (mapRenderers.size() != 1) {
      return false;
    }

    MapRenderer current = mapRenderers.get(0);
    if (!(current instanceof MapRendererPipe pipe)) {
      return false;
    }

    return pipe.getNormalizedRendererId().equals(metadataFor(renderer.getId()).normalizedId());
  }

  private String rendererIdFromView(MapView view) {
    if (view == null) {
      return FeatureUnknown.ID;
    }

    for (MapRenderer mapRenderer : view.getRenderers()) {
      if (!(mapRenderer instanceof MapRendererPipe pipe)) {
        continue;
      }

      String id = pipe.getRendererId();
      if (isSpecificRendererId(id)) {
        return id;
      }
    }

    return FeatureUnknown.ID;
  }

  private void refreshLoadedItemFrames() {
    Supplier<FrameMapViewerSnapshot> viewerSnapshot = memoizedViewerSnapshot();
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        refreshChunkItemFrames(chunk, true, viewerSnapshot);
      }
    }
  }

  private void refreshChunkItemFrames(Chunk chunk, boolean forceRendererUpdate, Supplier<FrameMapViewerSnapshot> viewerSnapshot) {
    if (chunk == null) {
      return;
    }

    Runnable work = () -> {
      for (Entity entity : chunk.getEntities()) {
        if (entity instanceof ItemFrame frame) {
          refreshItemFrame(frame, forceRendererUpdate, viewerSnapshot);
        }
      }
    };

    if (!J.isFoliaThreading()) {
      work.run();
      return;
    }

    World world = chunk.getWorld();
    Location anchor = new Location(world, (chunk.getX() << 4) + 8.0D, 64.0D, (chunk.getZ() << 4) + 8.0D);
    if (J.isOwnedByCurrentRegion(anchor)) {
      work.run();
      return;
    }

    J.runChunk(world, chunk.getX(), chunk.getZ(), work);
  }

  private void runFrameRegion(Entity anchor, Runnable work) {
    if (!J.isFoliaThreading() || J.isOwnedByCurrentRegion(anchor)) {
      work.run();
      return;
    }

    J.runEntity(anchor, work);
  }

  private void refreshItemFrame(ItemFrame frame, boolean forceRendererUpdate, Supplier<FrameMapViewerSnapshot> viewerSnapshot) {
    if (frame == null) {
      return;
    }

    ItemStack item = frame.getItem();
    MapMeta meta = reactMapMeta(item);
    if (meta == null) {
      // The frame no longer holds a React map; drop stale tracking so the wall re-solves.
      untrackFrameMap(frame.getUniqueId());
      return;
    }

    if (repairMapMeta(meta, frame.getWorld(), forceRendererUpdate)) {
      item.setItemMeta(meta);
      setFrameItemQuietly(frame, item);
    }

    MapView view = meta.getMapView();
    if (view == null) {
      return;
    }

    trackFrameMap(frame, view);
    pushFrameMapToNearbyPlayers(frame, view, viewerSnapshot);
  }

  private void trackFrameMap(ItemFrame frame, MapView view) {
    if (frame == null || view == null || frame.getWorld() == null || activeFrameMaps == null) {
      return;
    }

    Integer mapId = mapIdOf(view);
    if (mapId == null) {
      return;
    }

    long now = System.currentTimeMillis();
    UUID frameId = frame.getUniqueId();
    UUID worldId = frame.getWorld().getUID();
    Location location = frame.getLocation();
    if (frameMapLastSeenByMapId != null) {
      frameMapLastSeenByMapId.put(mapId, now);
    }

    ActiveFrameMap tracked = activeFrameMaps.compute(frameId, (id, existing) -> {
      if (existing == null || !existing.worldId.equals(worldId)) {
        return new ActiveFrameMap(frameId, worldId, mapId, location, now);
      }

      existing.mapId = mapId;
      existing.location = location;
      existing.lastSeenMs = now;
      return existing;
    });
    indexFrameMapId(mapId, frameId);
    if (tracked.gridMapId < 0) {
      invalidateMegamapGrid();
    }
    captureFrameGridState(tracked, frame, view);
  }

  private void indexFrameMapId(int mapId, UUID frameId) {
    if (frameIdsByMapId == null || frameId == null || mapId < 0) {
      return;
    }

    // A map id can transiently point at several frames while a duplicate is being
    // split; keeping the latest is enough for anchor lookups.
    frameIdsByMapId.put(mapId, frameId);
  }

  private void captureFrameGridState(ActiveFrameMap tracked, ItemFrame frame, MapView view) {
    if (tracked == null || frame == null || tracked.location == null) {
      return;
    }

    BlockFace facing = frame.getFacing();
    boolean rotationAligned = frame.getRotation() == Rotation.NONE;
    int blockX = tracked.location.getBlockX();
    int blockY = tracked.location.getBlockY();
    int blockZ = tracked.location.getBlockZ();
    String rendererId = rendererIdFromView(view);
    if (tracked.gridMapId == tracked.mapId
        && tracked.facing == facing
        && tracked.rotationAligned == rotationAligned
        && tracked.blockX == blockX
        && tracked.blockY == blockY
        && tracked.blockZ == blockZ
        && Objects.equals(tracked.rendererId, rendererId)) {
      return;
    }

    tracked.gridMapId = tracked.mapId;
    tracked.facing = facing;
    tracked.rotationAligned = rotationAligned;
    tracked.blockX = blockX;
    tracked.blockY = blockY;
    tracked.blockZ = blockZ;
    tracked.rendererId = rendererId;
    invalidateMegamapGrid();
  }

  private void invalidateMegamapGrid() {
    if (megamapStateVersion != null) {
      megamapStateVersion.incrementAndGet();
    }
  }

  private void untrackFrameMap(UUID frameId) {
    if (activeFrameMaps == null || frameId == null) {
      return;
    }

    ActiveFrameMap removed = activeFrameMaps.remove(frameId);
    if (removed == null) {
      return;
    }

    if (frameIdsByMapId != null) {
      frameIdsByMapId.remove(removed.mapId, frameId);
    }

    // A split dispatched to a region that never ran (entity retired, chunk unloaded)
    // must not pin this frame id out of future splits after the frame reloads.
    if (megamapSplitsInFlight != null) {
      megamapSplitsInFlight.remove(frameId);
    }

    invalidateMegamapGrid();
  }

  private boolean rebuildMegamapTiles() {
    if (megamapStateVersion == null) {
      return false;
    }

    int version = megamapStateVersion.get();
    if (version == megamapSolvedVersion && megamapEnabled == megamapSolvedEnabled) {
      return false;
    }

    megamapSolvedVersion = version;
    megamapSolvedEnabled = megamapEnabled;
    if (!megamapEnabled || activeFrameMaps == null || activeFrameMaps.isEmpty()) {
      megamapSolution = MegamapGrid.MegamapSolution.EMPTY;
      return true;
    }

    List<MegamapGrid.FrameCell> cells = new ArrayList<>(activeFrameMaps.size());
    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      if (tracked == null || tracked.facing == null || tracked.rendererId == null) {
        continue;
      }

      cells.add(new MegamapGrid.FrameCell(
          tracked.mapId,
          tracked.worldId,
          tracked.facing,
          tracked.blockX,
          tracked.blockY,
          tracked.blockZ,
          tracked.rendererId,
          tracked.rotationAligned
      ));
    }

    megamapSolution = MegamapGrid.analyze(cells);
    return true;
  }

  private void reconcileMegamapDefects() {
    MegamapGrid.MegamapSolution solution = megamapSolution;
    if (!megamapEnabled
        || solution == null
        || solution.defects().isEmpty()
        || activeFrameMaps == null
        || activeFrameMaps.isEmpty()) {
      return;
    }

    splitDuplicateFrameMaps(solution);
    resetRotatedFrameMaps(solution);
  }

  private void splitDuplicateFrameMaps(MegamapGrid.MegamapSolution solution) {
    if (!megamapSplitDuplicates) {
      return;
    }

    List<MegamapDuplicateSplitter.DuplicateFrame> duplicates = null;
    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      if (tracked == null || tracked.rendererId == null) {
        continue;
      }

      MegamapGrid.MegamapDefect defect = solution.defects().get(tracked.mapId);
      if (defect == null || defect.reason() != MegamapGrid.DefectReason.DUPLICATE_MAP_ID) {
        continue;
      }

      if (duplicates == null) {
        duplicates = new ArrayList<>();
      }

      duplicates.add(new MegamapDuplicateSplitter.DuplicateFrame(
          tracked.frameId,
          tracked.mapId,
          tracked.rendererId,
          tracked.firstSeenMs
      ));
    }

    if (duplicates == null) {
      return;
    }

    int remaining = megamapSplitsPerPass;
    for (UUID frameId : MegamapDuplicateSplitter.plan(duplicates)) {
      if (remaining <= 0) {
        return;
      }

      if (splitDuplicateFrameMap(frameId)) {
        remaining--;
      }
    }
  }

  private boolean splitDuplicateFrameMap(UUID frameId) {
    ActiveFrameMap tracked = activeFrameMaps.get(frameId);
    if (tracked == null || tracked.rendererId == null || megamapSplitsInFlight == null) {
      return false;
    }

    Entity entity = resolveFrameEntity(frameId);
    if (entity == null) {
      return false;
    }

    ReactRenderer renderer = resolveRenderer(tracked.rendererId);
    World world = Bukkit.getWorld(tracked.worldId);
    if (renderer == null || world == null) {
      return false;
    }

    // Never mint a fresh view behind an unresolved renderer; that would swap the
    // dashboard out from under the player instead of just re-keying the map.
    if (isSpecificRendererId(tracked.rendererId) && isUnknownRendererId(renderer.getId())) {
      return false;
    }

    if (!megamapSplitsInFlight.add(frameId)) {
      return false;
    }

    // Bukkit.createMap allocates a global map id, so mint the view here on the
    // global/primary maintenance thread and dispatch only the frame item write to the
    // frame's owning region.
    int duplicateMapId = tracked.mapId;
    MapView view = createView(world, renderer);
    runFrameRegion(entity, () -> {
      try {
        applyDuplicateSplit(tracked, entity, duplicateMapId, renderer, view);
      } finally {
        megamapSplitsInFlight.remove(frameId);
      }
    });

    return true;
  }

  private void applyDuplicateSplit(
      ActiveFrameMap tracked,
      Entity entity,
      int duplicateMapId,
      ReactRenderer renderer,
      MapView view
  ) {
    if (!(entity instanceof ItemFrame frame) || !frame.isValid()) {
      return;
    }

    ItemStack item = frame.getItem();
    MapMeta meta = reactMapMeta(item);
    if (meta == null) {
      return;
    }

    Integer currentMapId = mapIdOf(meta.getMapView());
    if (currentMapId == null || currentMapId != duplicateMapId) {
      return;
    }

    Integer freshMapId = mapIdOf(view);
    if (freshMapId == null) {
      return;
    }

    meta.setMapView(view);
    applyRendererMetadata(meta, renderer);
    item.setItemMeta(meta);
    setFrameItemQuietly(frame, item);

    long now = System.currentTimeMillis();
    tracked.mapId = freshMapId;
    tracked.view = view;
    tracked.location = frame.getLocation();
    tracked.lastSeenMs = now;
    tracked.lastValidatedMs = now;
    if (frameIdsByMapId != null) {
      frameIdsByMapId.remove(duplicateMapId, tracked.frameId);
    }
    indexFrameMapId(freshMapId, tracked.frameId);
    captureFrameGridState(tracked, frame, view);
    invalidateMegamapGrid();
    React.verbose("Megamap split duplicate map " + duplicateMapId + " -> " + freshMapId + " frame=" + tracked.frameId);
  }

  private void resetRotatedFrameMaps(MegamapGrid.MegamapSolution solution) {
    int remaining = megamapRotationResetsPerPass;
    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      if (remaining <= 0) {
        return;
      }

      if (tracked == null || tracked.rotationAligned) {
        continue;
      }

      MegamapGrid.MegamapDefect defect = solution.defects().get(tracked.mapId);
      if (defect == null || defect.reason() != MegamapGrid.DefectReason.ROTATED) {
        continue;
      }

      Entity entity = resolveFrameEntity(tracked.frameId);
      if (entity == null) {
        continue;
      }

      remaining--;
      runFrameRegion(entity, () -> applyRotationReset(tracked, entity));
    }
  }

  private void applyRotationReset(ActiveFrameMap tracked, Entity entity) {
    if (!(entity instanceof ItemFrame frame) || !frame.isValid()) {
      return;
    }

    if (frame.getRotation() != Rotation.NONE) {
      frame.setRotation(Rotation.NONE);
      React.verbose("Megamap rotation reset map=" + tracked.mapId + " frame=" + tracked.frameId);
    }

    if (!tracked.rotationAligned) {
      tracked.rotationAligned = true;
      invalidateMegamapGrid();
    }
  }

  private Entity resolveFrameEntity(UUID frameId) {
    try {
      return Bukkit.getEntity(frameId);
    } catch (Throwable ex) {
      React.verbose("Failed to resolve tracked map frame entity " + frameId + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      return null;
    }
  }

  public MegamapGrid.MegamapTile megamapTileFor(MapView view) {
    if (!megamapEnabled) {
      return null;
    }

    MegamapGrid.MegamapSolution solution = megamapSolution;
    if (solution == null || solution.tiles().isEmpty()) {
      return null;
    }

    Integer mapId = mapIdOf(view);
    return mapId == null ? null : solution.tiles().get(mapId);
  }

  public MegamapGrid.MegamapDefect megamapDefectFor(MapView view) {
    if (!megamapEnabled) {
      return null;
    }

    MegamapGrid.MegamapSolution solution = megamapSolution;
    if (solution == null || solution.defects().isEmpty()) {
      return null;
    }

    Integer mapId = mapIdOf(view);
    return mapId == null ? null : solution.defects().get(mapId);
  }

  public Map<String, MegamapStatus> megamapStatusByRenderer() {
    MegamapGrid.MegamapSolution solution = megamapSolution;
    if (!megamapEnabled || solution == null || solution.isEmpty() || activeFrameMaps == null) {
      return Map.of();
    }

    Map<String, MegamapStatus> status = new LinkedHashMap<>();
    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      if (tracked == null || tracked.rendererId == null) {
        continue;
      }

      String rendererId = normalizeRendererId(tracked.rendererId);
      MegamapGrid.MegamapTile tile = solution.tiles().get(tracked.mapId);
      MegamapGrid.MegamapDefect defect = solution.defects().get(tracked.mapId);
      if (tile == null && defect == null) {
        continue;
      }

      status.merge(
          rendererId,
          new MegamapStatus(
              tile == null ? 0 : tile.gridWidth(),
              tile == null ? 0 : tile.gridHeight(),
              1,
              defect == null ? null : defect.reason()
          ),
          MegamapStatus::mergeWith
      );
    }

    return status;
  }

  private void pushTrackedFrameMaps(Supplier<FrameMapViewerSnapshot> viewerSnapshot) {
    if (activeFrameMaps == null || activeFrameMaps.isEmpty()) {
      pruneFramePushState();
      return;
    }

    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      if (tracked == null || tracked.worldId == null) {
        if (tracked != null) {
          untrackFrameMap(tracked.frameId);
        }
        continue;
      }

      World world = Bukkit.getWorld(tracked.worldId);
      if (world == null) {
        untrackFrameMap(tracked.frameId);
        continue;
      }

      Entity entity = resolveFrameEntity(tracked.frameId);
      if (entity == null) {
        untrackFrameMap(tracked.frameId);
        continue;
      }

      runFrameRegion(entity, () -> validateAndPushTrackedFrame(tracked, entity, viewerSnapshot));
    }

    pruneFramePushState();
  }

  private void validateAndPushTrackedFrame(ActiveFrameMap tracked, Entity entity, Supplier<FrameMapViewerSnapshot> viewerSnapshot) {
    long now = System.currentTimeMillis();
    if (!(entity instanceof ItemFrame frame)
        || !frame.isValid()
        || frame.getWorld() == null
        || !tracked.worldId.equals(frame.getWorld().getUID())) {
      untrackFrameMap(tracked.frameId);
      return;
    }

    // Reading the frame item clones the stack and its meta; only revalidate on a
    // cadence and push through the cached view between validations.
    if (tracked.view != null && now - tracked.lastValidatedMs < Math.max(0L, frameMapValidateIntervalMs)) {
      tracked.lastSeenMs = now;
      if (frameMapLastSeenByMapId != null) {
        frameMapLastSeenByMapId.put(tracked.mapId, now);
      }
      pushMapToNearbyPlayers(frame, tracked.view, viewerSnapshot);
      return;
    }

    MapMeta meta = reactMapMeta(frame.getItem());
    if (meta == null || meta.getMapView() == null) {
      untrackFrameMap(tracked.frameId);
      return;
    }

    MapView view = meta.getMapView();
    Integer mapId = mapIdOf(view);
    if (mapId == null) {
      untrackFrameMap(tracked.frameId);
      return;
    }

    int previousMapId = tracked.mapId;
    tracked.mapId = mapId;
    tracked.location = frame.getLocation();
    tracked.lastSeenMs = now;
    tracked.lastValidatedMs = now;
    tracked.view = view;
    if (previousMapId != mapId && frameIdsByMapId != null) {
      frameIdsByMapId.remove(previousMapId, tracked.frameId);
    }
    indexFrameMapId(mapId, tracked.frameId);
    captureFrameGridState(tracked, frame, view);
    if (frameMapLastSeenByMapId != null) {
      frameMapLastSeenByMapId.put(mapId, now);
    }
    pushMapToNearbyPlayers(frame, view, viewerSnapshot);
  }

  private void pruneFramePushState() {
    long now = System.currentTimeMillis();
    long retentionMs = effectiveFrameMapPushStateRetentionMs();
    if (frameMapPushMsByViewerKey != null && !frameMapPushMsByViewerKey.isEmpty()) {
      frameMapPushMsByViewerKey.entrySet().removeIf(entry -> now - entry.getValue() > retentionMs);
    }
    if (frameMapLastSeenByMapId != null && !frameMapLastSeenByMapId.isEmpty()) {
      frameMapLastSeenByMapId.entrySet().removeIf(entry -> now - entry.getValue() > retentionMs);
    }
  }

  private void clearRenderers(MapView view) {
    if (view == null) {
      return;
    }

    for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
      try {
        view.removeRenderer(renderer);
      } catch (Throwable e) {
        Integer mapId = mapIdOf(view);
        String mapText = mapId == null ? "unknown" : String.valueOf(mapId);
        String rendererClass = renderer == null ? "unknown" : renderer.getClass().getSimpleName();
        React.warn(
            "Map renderer cleanup failed: mapId=" + mapText
                + " renderer=" + rendererClass
                + " cause=" + summarizeThrowable(e)
                + " action=kept-existing-renderer"
        );
      }
    }
  }

  private void repairOneLoadedChunkItemFrames(Supplier<FrameMapViewerSnapshot> viewerSnapshot) {
    long now = System.currentTimeMillis();
    if (now - lastItemFrameRepairMs < effectiveItemFrameRepairCadenceMs()) {
      return;
    }
    lastItemFrameRepairMs = now;

    List<World> worlds = Bukkit.getWorlds();
    if (worlds.isEmpty()) {
      itemFrameWorldCursor = 0;
      itemFrameChunkCursor = 0;
      return;
    }

    int remaining = effectiveItemFrameChunkBatchSize();
    boolean forceRendererUpdate = inStartupBoost();
    int worldCount = worlds.size();
    int worldsVisited = 0;

    // getLoadedChunks() copies the full loaded-chunk array, so fetch it once per
    // world per pass and serve the whole batch from it instead of per chunk.
    while (remaining > 0 && worldsVisited < worldCount) {
      if (itemFrameWorldCursor >= worldCount) {
        itemFrameWorldCursor = 0;
        itemFrameChunkCursor = 0;
      }

      World world = worlds.get(itemFrameWorldCursor);
      Chunk[] loadedChunks = world.getLoadedChunks();
      if (itemFrameChunkCursor >= loadedChunks.length) {
        itemFrameWorldCursor = (itemFrameWorldCursor + 1) % worldCount;
        itemFrameChunkCursor = 0;
        worldsVisited++;
        continue;
      }

      int end = Math.min(loadedChunks.length, itemFrameChunkCursor + remaining);
      for (int i = itemFrameChunkCursor; i < end; i++) {
        refreshChunkItemFrames(loadedChunks[i], forceRendererUpdate, viewerSnapshot);
      }

      remaining -= end - itemFrameChunkCursor;
      itemFrameChunkCursor = end;
      if (itemFrameChunkCursor >= loadedChunks.length) {
        itemFrameWorldCursor = (itemFrameWorldCursor + 1) % worldCount;
        itemFrameChunkCursor = 0;
        worldsVisited++;
      }
    }
  }

  private void pushFrameMapToNearbyPlayers(ItemFrame frame, MapView view, Supplier<FrameMapViewerSnapshot> viewerSnapshot) {
    if (frame == null || frame.getWorld() == null || view == null) {
      return;
    }

    pushMapToNearbyPlayers(frame, view, viewerSnapshot);
  }

  private void pushMapToNearbyPlayers(ItemFrame frame, MapView view, Supplier<FrameMapViewerSnapshot> viewerSnapshot) {
    if (frame == null || frame.getWorld() == null || view == null || frameMapPushMsByViewerKey == null) {
      return;
    }

    Integer mapId = mapIdOf(view);
    if (mapId == null) {
      return;
    }

    // On regionized servers the global viewer snapshot would read player state across
    // region threads; serve viewers region-locally from the frame's owning thread instead.
    if (J.isFoliaThreading()) {
      pushMapToNearbyPlayersRegionLocal(frame, view, mapId);
      return;
    }

    FrameMapViewerSnapshot activeViewerSnapshot = viewerSnapshot.get();
    if (activeViewerSnapshot == null || activeViewerSnapshot.viewersByWorldChunk.isEmpty()) {
      return;
    }

    World world = frame.getWorld();
    UUID worldId = world.getUID();
    Location source = frame.getLocation();
    double sourceX = source.getX();
    double sourceY = source.getY();
    double sourceZ = source.getZ();
    long now = System.currentTimeMillis();
    double radiusSq = effectiveFrameMapPushRadiusSq();
    long activeInterval = effectiveFrameMapPushIntervalMs();
    long idleInterval = effectiveFrameMapIdlePushIntervalMs();
    boolean requireLineOfSight = frameMapRequireLineOfSight;
    double lookDotThreshold = effectiveFrameMapLookDotThreshold();
    Map<UUID, FrameMapViewer> candidates = collectFrameMapCandidates(activeViewerSnapshot, worldId, sourceX, sourceZ, mapId);
    if (candidates.isEmpty()) {
      return;
    }

    for (FrameMapViewer viewer : candidates.values()) {
      if (viewer == null) {
        continue;
      }

      Player player = Bukkit.getPlayer(viewer.playerId);
      if (!isSnapshotPlayerValid(player, viewer)) {
        continue;
      }

      boolean holding = viewer.isHoldingMap(mapId);
      boolean holdingBypassesRange = holding && frameMapPushOutsideRangeWhenHolding;
      boolean sameWorld = worldId.equals(viewer.worldId);
      if (!holdingBypassesRange && !sameWorld) {
        continue;
      }

      boolean withinRadius = false;
      if (sameWorld) {
        double dx = viewer.x - sourceX;
        double dy = viewer.y - sourceY;
        double dz = viewer.z - sourceZ;
        withinRadius = ((dx * dx) + (dy * dy) + (dz * dz)) <= radiusSq;
      }

      if (!holdingBypassesRange && !withinRadius) {
        continue;
      }

      boolean activelyWatching = holding || isLikelyLookingAtFrame(viewer, sourceX, sourceY, sourceZ, lookDotThreshold);
      long requiredInterval = activelyWatching ? activeInterval : idleInterval;
      FramePushKey pushKey = new FramePushKey(mapId, viewer.playerId);
      long seededLastPush = now - requiredInterval + initialPushOffsetMs(pushKey, requiredInterval);
      long lastPush = frameMapPushMsByViewerKey.getOrDefault(pushKey, seededLastPush);
      if (now - lastPush < requiredInterval) {
        continue;
      }

      if (!holdingBypassesRange && requireLineOfSight && !player.hasLineOfSight(frame)) {
        continue;
      }

      try {
        player.sendMap(view);
        frameMapPushMsByViewerKey.put(pushKey, now);
      } catch (Throwable ex) {
        React.verbose("Failed to push map " + mapId + " to player " + player.getName() + ": "
            + ex.getClass().getSimpleName()
            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      }
    }
  }

  private void pushMapToNearbyPlayersRegionLocal(ItemFrame frame, MapView view, int mapId) {
    Location source = frame.getLocation();
    double radius = Math.max(1D, frameMapPushRadiusBlocks);
    double radiusSq = radius * radius;
    long now = System.currentTimeMillis();
    long activeInterval = effectiveFrameMapPushIntervalMs();
    long idleInterval = effectiveFrameMapIdlePushIntervalMs();
    double lookDotThreshold = effectiveFrameMapLookDotThreshold();
    for (Entity nearby : frame.getWorld().getNearbyEntities(source, radius, radius, radius)) {
      if (!(nearby instanceof Player player) || !player.isOnline()) {
        continue;
      }

      if (player.getLocation().distanceSquared(source) > radiusSq) {
        continue;
      }

      boolean holding = isHoldingMap(player, mapId);
      boolean activelyWatching = holding || isLikelyLookingAtFrame(player, source, lookDotThreshold);
      long requiredInterval = activelyWatching ? activeInterval : idleInterval;
      FramePushKey pushKey = new FramePushKey(mapId, player.getUniqueId());
      long seededLastPush = now - requiredInterval + initialPushOffsetMs(pushKey, requiredInterval);
      long lastPush = frameMapPushMsByViewerKey.getOrDefault(pushKey, seededLastPush);
      if (now - lastPush < requiredInterval) {
        continue;
      }

      boolean holdingBypassesRange = holding && frameMapPushOutsideRangeWhenHolding;
      if (!holdingBypassesRange && frameMapRequireLineOfSight && !player.hasLineOfSight(frame)) {
        continue;
      }

      try {
        player.sendMap(view);
        frameMapPushMsByViewerKey.put(pushKey, now);
      } catch (Throwable ex) {
        React.verbose("Failed to push map " + mapId + " to player " + player.getName() + ": "
            + ex.getClass().getSimpleName()
            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      }
    }
  }

  private boolean isLikelyLookingAtFrame(Player player, Location source, double lookDotThreshold) {
    Location eye = player.getEyeLocation();
    double toFrameX = source.getX() - eye.getX();
    double toFrameY = source.getY() - eye.getY();
    double toFrameZ = source.getZ() - eye.getZ();
    double toFrameLengthSquared = (toFrameX * toFrameX) + (toFrameY * toFrameY) + (toFrameZ * toFrameZ);
    if (toFrameLengthSquared <= 1.0E-6D) {
      return true;
    }

    org.bukkit.util.Vector direction = eye.getDirection();
    double directionLengthSquared = direction.lengthSquared();
    if (directionLengthSquared <= 1.0E-6D) {
      return false;
    }

    double inverseToFrameLength = 1.0D / Math.sqrt(toFrameLengthSquared);
    double inverseDirectionLength = 1.0D / Math.sqrt(directionLengthSquared);
    double dot = ((direction.getX() * inverseDirectionLength) * (toFrameX * inverseToFrameLength))
        + ((direction.getY() * inverseDirectionLength) * (toFrameY * inverseToFrameLength))
        + ((direction.getZ() * inverseDirectionLength) * (toFrameZ * inverseToFrameLength));
    return dot >= lookDotThreshold;
  }

  private Map<UUID, FrameMapViewer> collectFrameMapCandidates(
      FrameMapViewerSnapshot viewerSnapshot,
      UUID worldId,
      double sourceX,
      double sourceZ,
      int mapId
  ) {
    Map<UUID, FrameMapViewer> candidates = new HashMap<>();
    Map<Long, List<FrameMapViewer>> worldBuckets = viewerSnapshot.viewersByWorldChunk.get(worldId);
    if (worldBuckets != null && !worldBuckets.isEmpty()) {
      double radiusBlocks = Math.max(1D, frameMapPushRadiusBlocks);
      int minChunkX = chunkCoordinate(sourceX - radiusBlocks);
      int maxChunkX = chunkCoordinate(sourceX + radiusBlocks);
      int minChunkZ = chunkCoordinate(sourceZ - radiusBlocks);
      int maxChunkZ = chunkCoordinate(sourceZ + radiusBlocks);

      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
          List<FrameMapViewer> viewers = worldBuckets.get(chunkKey(chunkX, chunkZ));
          if (viewers == null || viewers.isEmpty()) {
            continue;
          }

          for (FrameMapViewer viewer : viewers) {
            candidates.put(viewer.playerId, viewer);
          }
        }
      }
    }

    if (frameMapPushOutsideRangeWhenHolding) {
      List<FrameMapViewer> holders = viewerSnapshot.viewersHoldingMap.get(mapId);
      if (holders != null && !holders.isEmpty()) {
        for (FrameMapViewer viewer : holders) {
          candidates.put(viewer.playerId, viewer);
        }
      }
    }

    return candidates;
  }

  private Integer mapIdOf(MapView view) {
    if (view == null) {
      return null;
    }

    try {
      return view.getId();
    } catch (Throwable ex) {
      React.verbose("Failed to read map view id: " + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      return null;
    }
  }

  private Integer mapIdOf(ItemStack item) {
    if (item == null || item.getType() != Material.FILLED_MAP) {
      return null;
    }

    MapMeta meta = (MapMeta) item.getItemMeta();
    if (meta == null || meta.getMapView() == null) {
      return null;
    }

    return mapIdOf(meta.getMapView());
  }

  private FrameMapViewerSnapshot buildFrameMapViewerSnapshot() {
    Map<UUID, Map<Long, List<FrameMapViewer>>> viewersByWorldChunk = new HashMap<>();
    Map<Integer, List<FrameMapViewer>> viewersHoldingMap = new HashMap<>();

    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player == null || player.getWorld() == null) {
        continue;
      }

      Location location = player.getLocation();
      if (location.getWorld() == null) {
        continue;
      }

      Location eyeLocation = player.getEyeLocation();
      org.bukkit.util.Vector eyeDirection = eyeLocation.getDirection();
      double directionX = eyeDirection.getX();
      double directionY = eyeDirection.getY();
      double directionZ = eyeDirection.getZ();
      double directionLengthSquared = (directionX * directionX) + (directionY * directionY) + (directionZ * directionZ);
      boolean hasDirection = directionLengthSquared > 1.0E-6D;
      if (hasDirection) {
        double inverseDirectionLength = 1.0D / Math.sqrt(directionLengthSquared);
        directionX *= inverseDirectionLength;
        directionY *= inverseDirectionLength;
        directionZ *= inverseDirectionLength;
      }

      UUID worldId = location.getWorld().getUID();
      int chunkX = chunkCoordinate(location.getX());
      int chunkZ = chunkCoordinate(location.getZ());
      int mainHandMapId = mapIdOrNegative(player.getInventory().getItemInMainHand());
      int offHandMapId = mapIdOrNegative(player.getInventory().getItemInOffHand());
      FrameMapViewer viewer = new FrameMapViewer(
          player.getUniqueId(),
          worldId,
          location.getX(),
          location.getY(),
          location.getZ(),
          eyeLocation.getX(),
          eyeLocation.getY(),
          eyeLocation.getZ(),
          directionX,
          directionY,
          directionZ,
          hasDirection,
          mainHandMapId,
          offHandMapId
      );

      Map<Long, List<FrameMapViewer>> worldBuckets = viewersByWorldChunk.computeIfAbsent(worldId, ignored -> new HashMap<>());
      List<FrameMapViewer> chunkViewers = worldBuckets.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>());
      chunkViewers.add(viewer);

      if (mainHandMapId >= 0) {
        List<FrameMapViewer> holders = viewersHoldingMap.computeIfAbsent(mainHandMapId, ignored -> new ArrayList<>());
        holders.add(viewer);
      }

      if (offHandMapId >= 0 && offHandMapId != mainHandMapId) {
        List<FrameMapViewer> holders = viewersHoldingMap.computeIfAbsent(offHandMapId, ignored -> new ArrayList<>());
        holders.add(viewer);
      }
    }

    return new FrameMapViewerSnapshot(viewersByWorldChunk, viewersHoldingMap);
  }

  private boolean isSnapshotPlayerValid(Player player, FrameMapViewer viewer) {
    if (player == null || !player.isOnline() || player.getWorld() == null || viewer == null) {
      return false;
    }

    return player.getWorld().getUID().equals(viewer.worldId);
  }

  private int mapIdOrNegative(ItemStack item) {
    Integer mapId = mapIdOf(item);
    return mapId == null ? -1 : mapId;
  }

  private boolean isHoldingMap(Player player, int mapId) {
    if (player == null) {
      return false;
    }

    Integer main = mapIdOf(player.getInventory().getItemInMainHand());
    if (main != null && main == mapId) {
      return true;
    }

    Integer off = mapIdOf(player.getInventory().getItemInOffHand());
    return off != null && off == mapId;
  }

  private boolean isLikelyLookingAtFrame(
      FrameMapViewer viewer,
      double sourceX,
      double sourceY,
      double sourceZ,
      double lookDotThreshold
  ) {
    if (viewer == null) {
      return false;
    }

    double toFrameX = sourceX - viewer.eyeX;
    double toFrameY = sourceY - viewer.eyeY;
    double toFrameZ = sourceZ - viewer.eyeZ;
    double toFrameLengthSquared = (toFrameX * toFrameX) + (toFrameY * toFrameY) + (toFrameZ * toFrameZ);
    if (toFrameLengthSquared <= 1.0E-6D) {
      return true;
    }

    if (!viewer.hasDirection) {
      return false;
    }

    double inverseToFrameLength = 1.0D / Math.sqrt(toFrameLengthSquared);
    double normalizedToFrameX = toFrameX * inverseToFrameLength;
    double normalizedToFrameY = toFrameY * inverseToFrameLength;
    double normalizedToFrameZ = toFrameZ * inverseToFrameLength;
    double dot = (viewer.directionX * normalizedToFrameX)
        + (viewer.directionY * normalizedToFrameY)
        + (viewer.directionZ * normalizedToFrameZ);
    return dot >= lookDotThreshold;
  }

  private int chunkCoordinate(double blockCoordinate) {
    return (int) Math.floor(blockCoordinate / 16.0D);
  }

  private long chunkKey(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
  }

  private long initialPushOffsetMs(FramePushKey pushKey, long intervalMs) {
    if (pushKey == null || intervalMs <= 1L) {
      return 0L;
    }

    long hash = Integer.toUnsignedLong(pushKey.hashCode());
    return hash % intervalMs;
  }

  private boolean isKnownFrameMap(int mapId, long now) {
    if (frameMapLastSeenByMapId == null) {
      return false;
    }

    Long seenAt = frameMapLastSeenByMapId.get(mapId);
    if (seenAt == null) {
      return false;
    }

    return now - seenAt <= effectiveFrameMapPushStateRetentionMs();
  }

  private String summarizeThrowable(Throwable throwable) {
    if (throwable == null) {
      return "unknown";
    }

    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }

    String message = root.getMessage();
    if (message == null || message.isBlank()) {
      return root.getClass().getSimpleName();
    }

    return root.getClass().getSimpleName() + ": " + message;
  }

  private void setFrameItemQuietly(ItemFrame frame, ItemStack item) {
    if (frame == null) {
      return;
    }

    if (!itemFrameSetItemSilentMethodResolved) {
      itemFrameSetItemSilentMethodResolved = true;
      try {
        itemFrameSetItemSilentMethod = ItemFrame.class.getMethod("setItem", ItemStack.class, boolean.class);
      } catch (Throwable ex) {
        React.verbose("Silent ItemFrame#setItem(ItemStack, boolean) not available: "
            + ex.getClass().getSimpleName()
            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        itemFrameSetItemSilentMethod = null;
      }
    }

    if (itemFrameSetItemSilentMethod != null) {
      try {
        itemFrameSetItemSilentMethod.invoke(frame, item, false);
        return;
      } catch (Throwable ex) {
        React.verbose("Failed invoking silent ItemFrame setter, falling back: "
            + ex.getClass().getSimpleName()
            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      }
    }

    frame.setItem(item);
  }

  private void repairOneOnlinePlayerInventory() {
    long now = System.currentTimeMillis();
    if (now - lastInventoryRepairMs < effectiveInventoryRepairCadenceMs()) {
      return;
    }
    lastInventoryRepairMs = now;

    List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
    if (onlinePlayers.isEmpty()) {
      inventoryRepairCursor = 0;
      return;
    }

    if (inventoryRepairCursor >= onlinePlayers.size()) {
      inventoryRepairCursor = 0;
    }

    int passes = Math.min(effectiveInventoryBatchSize(), onlinePlayers.size());
    boolean forceRendererUpdate = inStartupBoost();
    for (int i = 0; i < passes; i++) {
      if (inventoryRepairCursor >= onlinePlayers.size()) {
        inventoryRepairCursor = 0;
      }

      Player player = onlinePlayers.get(inventoryRepairCursor++);
      if (!J.isFoliaThreading()) {
        updateMapViews(player, forceRendererUpdate);
        continue;
      }

      J.runEntity(player, () -> updateMapViews(player, forceRendererUpdate));
    }
  }

  private ReactRenderer resolveRenderer(String rendererId) {
    if (renderers == null) {
      return null;
    }

    if (resolvedRenderers == null) {
      return resolveRendererUncached(rendererId);
    }

    ReactRenderer cached = resolvedRenderers.get(Objects.toString(rendererId, ""));
    if (cached != null) {
      return cached;
    }

    ReactRenderer resolved = resolveRendererUncached(rendererId);
    if (resolved != null) {
      resolvedRenderers.put(Objects.toString(rendererId, ""), resolved);
    }

    return resolved;
  }

  private ReactRenderer resolveRendererUncached(String rendererId) {
    ReactRenderer unknown = renderers.get(FeatureUnknown.ID);
    String requested = normalizeRendererId(rendererId);
    if (requested.isBlank()) {
      return unknown;
    }

    ReactRenderer exact = renderers.get(rendererId);
    if (exact != null) {
      return exact;
    }

    ReactRenderer normalized = renderers.get(requested);
    if (normalized != null) {
      return normalized;
    }

    String requestedAlias = stripFeaturePrefix(requested);
    if (!requestedAlias.equals(requested)) {
      ReactRenderer alias = renderers.get(requestedAlias);
      if (alias != null) {
        return alias;
      }
    }

    String requestedCanonical = canonicalRendererId(requested);
    String requestedAliasCanonical = canonicalRendererId(requestedAlias);

    for (ReactRenderer candidate : renderers.values()) {
      if (candidate == null || candidate.getId() == null) {
        continue;
      }

      String candidateNormalized = normalizeRendererId(candidate.getId());
      if (candidateNormalized.equals(requested)
          || candidateNormalized.equals(requestedAlias)
          || canonicalRendererId(candidateNormalized).equals(requestedCanonical)
          || canonicalRendererId(candidateNormalized).equals(requestedAliasCanonical)) {
        return candidate;
      }
    }

    ReactRenderer dynamic = resolveDynamicRenderer(requested, requestedAlias, requestedCanonical, requestedAliasCanonical);
    if (dynamic != null) {
      registerRenderer(dynamic);
      return dynamic;
    }

    return unknown;
  }

  private ReactRenderer resolveDynamicRenderer(
      String requested,
      String requestedAlias,
      String requestedCanonical,
      String requestedAliasCanonical
  ) {
    if (requested.isBlank()) {
      return null;
    }

    ReactRenderer metrics = integrationMetricsRenderer(requested, requestedAlias);
    if (matchesRendererId(metrics, requested, requestedAlias, requestedCanonical, requestedAliasCanonical)) {
      return metrics;
    }

    SampleController sampleController = React.controller(SampleController.class);
    if (sampleController != null && sampleController.getSamplers() != null) {
      for (Sampler sampler : sampleController.getSamplers().all()) {
        if (matchesRendererId(sampler, requested, requestedAlias, requestedCanonical, requestedAliasCanonical)) {
          return sampler;
        }
      }
    }

    FeatureController featureController = React.controller(FeatureController.class);
    if (featureController != null && featureController.getFeatures() != null) {
      for (Feature feature : featureController.getFeatures().all()) {
        if (!(feature instanceof ReactRenderer reactRenderer)) {
          continue;
        }

        if (matchesRendererId(reactRenderer, requested, requestedAlias, requestedCanonical, requestedAliasCanonical)) {
          return reactRenderer;
        }
      }
    }

    return null;
  }

  private ReactRenderer integrationMetricsRenderer(String requested, String requestedAlias) {
    if (irisMetricsRenderers != null) {
      for (ReactRenderer renderer : irisMetricsRenderers) {
        if (renderer != null
            && (requested.equals(renderer.getId()) || requestedAlias.equals(renderer.getId()))) {
          return renderer;
        }
      }
    }

    if (requested.equals(RendererAdaptMetrics.ID) || requestedAlias.equals(RendererAdaptMetrics.ID)) {
      return adaptMetricsRenderer;
    }

    if (requested.equals(RendererWormholesMetrics.ID) || requestedAlias.equals(RendererWormholesMetrics.ID)) {
      return wormholesMetricsRenderer;
    }

    if (requested.equals(RendererHolouiMetrics.ID) || requestedAlias.equals(RendererHolouiMetrics.ID)) {
      return holouiMetricsRenderer;
    }

    if (requested.equals(RendererHiddenoreMetrics.ID) || requestedAlias.equals(RendererHiddenoreMetrics.ID)) {
      return hiddenoreMetricsRenderer;
    }

    if (requested.equals(RendererBiletoolsMetrics.ID) || requestedAlias.equals(RendererBiletoolsMetrics.ID)) {
      return biletoolsMetricsRenderer;
    }

    return null;
  }

  private boolean matchesRendererId(
      ReactRenderer renderer,
      String requested,
      String requestedAlias,
      String requestedCanonical,
      String requestedAliasCanonical
  ) {
    if (renderer == null || renderer.getId() == null) {
      return false;
    }

    String candidateNormalized = normalizeRendererId(renderer.getId());
    return candidateNormalized.equals(requested)
        || candidateNormalized.equals(requestedAlias)
        || canonicalRendererId(candidateNormalized).equals(requestedCanonical)
        || canonicalRendererId(candidateNormalized).equals(requestedAliasCanonical);
  }

  private String getStoredRendererId(MapMeta meta) {
    if (meta == null) {
      return FeatureUnknown.ID;
    }

    String pdcId = meta.getPersistentDataContainer().get(nsRenderer, PersistentDataType.STRING);
    if (isSpecificRendererId(pdcId)) {
      return pdcId;
    }

    String loreId = parseRendererIdFromLore(meta.getLore());
    if (isSpecificRendererId(loreId)) {
      return loreId;
    }

    if (isUnknownRendererId(pdcId)) {
      return FeatureUnknown.ID;
    }

    return pdcId;
  }

  private boolean isSpecificRendererId(String rendererId) {
    String normalized = normalizeRendererId(rendererId);
    return !normalized.isBlank() && !isUnknownRendererId(rendererId);
  }

  private boolean isUnknownRendererId(String rendererId) {
    String normalized = normalizeRendererId(rendererId);
    return normalized.isBlank() || normalized.equals(normalizeRendererId(FeatureUnknown.ID));
  }

  private String getOrCreateMapToken(MapMeta meta) {
    String token = meta.getPersistentDataContainer().get(nsMapToken, PersistentDataType.STRING);
    if (token == null || token.isBlank()) {
      token = parseMapTokenFromLore(meta.getLore());
    }
    if (token == null || token.isBlank()) {
      token = UUID.randomUUID().toString();
    }
    meta.getPersistentDataContainer().set(nsMapToken, PersistentDataType.STRING, token);
    return token;
  }

  private String stripFeaturePrefix(String rendererId) {
    String normalized = normalizeRendererId(rendererId);
    if (normalized.startsWith("feature-") && normalized.length() > "feature-".length()) {
      return normalized.substring("feature-".length());
    }
    return normalized;
  }

  private String parseRendererIdFromLore(List<String> lore) {
    if (lore == null) {
      return FeatureUnknown.ID;
    }

    for (String line : lore) {
      if (line == null || line.isBlank()) {
        continue;
      }

      String stripped = ChatColor.stripColor(line);
      if (stripped == null || stripped.isBlank()) {
        continue;
      }

      String trimmed = stripped.trim();
      if (!trimmed.regionMatches(true, 0, "ID:", 0, 3)) {
        continue;
      }

      String parsed = trimmed.substring(3).trim();
      if (isSpecificRendererId(parsed)) {
        return parsed;
      }
    }

    return FeatureUnknown.ID;
  }

  private String parseMapTokenFromLore(List<String> lore) {
    if (lore == null) {
      return null;
    }

    for (String line : lore) {
      if (line == null || line.isBlank()) {
        continue;
      }

      String stripped = ChatColor.stripColor(line);
      if (stripped == null || stripped.isBlank()) {
        continue;
      }

      String trimmed = stripped.trim();
      if (!trimmed.regionMatches(true, 0, "Tag:", 0, 4)) {
        continue;
      }

      String parsed = trimmed.substring(4).trim();
      if (!parsed.isBlank()) {
        return parsed;
      }
    }

    return null;
  }

  private Location findFrameAnchor(MapView view) {
    if (view == null || activeFrameMaps == null || activeFrameMaps.isEmpty()) {
      return null;
    }

    Integer mapId = mapIdOf(view);
    if (mapId == null) {
      return null;
    }

    // This sits on the per-viewer render path, so hit the mapId index first and only
    // fall back to the linear scan when the index misses.
    World viewWorld = view.getWorld();
    if (frameIdsByMapId != null) {
      UUID frameId = frameIdsByMapId.get(mapId);
      if (frameId != null) {
        Location anchor = frameAnchorOf(activeFrameMaps.get(frameId), mapId, viewWorld);
        if (anchor != null) {
          return anchor;
        }
      }
    }

    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      Location anchor = frameAnchorOf(tracked, mapId, viewWorld);
      if (anchor != null) {
        return anchor;
      }
    }

    return null;
  }

  private Location frameAnchorOf(ActiveFrameMap tracked, int mapId, World viewWorld) {
    if (tracked == null || tracked.mapId != mapId) {
      return null;
    }

    Location location = tracked.location;
    if (location == null || location.getWorld() == null) {
      return null;
    }

    if (viewWorld != null && !viewWorld.equals(location.getWorld())) {
      return null;
    }

    return location.clone();
  }

  private String canonicalRendererId(String rendererId) {
    return normalizeRendererId(rendererId).replaceAll("[^a-z0-9]", "");
  }

  private String rendererScope(String normalizedRendererId) {
    if (normalizedRendererId.startsWith("iris-")) {
      return "Iris";
    }
    if (normalizedRendererId.startsWith("adapt-")) {
      return "Adapt";
    }
    if (normalizedRendererId.startsWith("wormholes-")) {
      return "Wormholes";
    }
    if (normalizedRendererId.startsWith("holoui-")) {
      return "HoloUi";
    }
    if (normalizedRendererId.startsWith("hiddenore-")) {
      return "HiddenOre";
    }
    if (normalizedRendererId.startsWith("biletools-")) {
      return "BileTools";
    }
    if (normalizedRendererId.startsWith("react-")) {
      return "React";
    }
    if (normalizedRendererId.startsWith("plugin-")) {
      return "Plugins";
    }
    return "Core";
  }

  private String rendererDisplayName(String rendererId) {
    if (rendererId == null || rendererId.isBlank()) {
      return "Unknown";
    }

    String spaced = rendererId
        .replace('_', ' ')
        .replace('-', ' ')
        .replaceAll("([a-z])([A-Z])", "$1 $2")
        .trim();
    if (spaced.isBlank()) {
      return rendererId;
    }

    return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }

  private String normalizeRendererId(String rendererId) {
    if (rendererId == null) {
      return "";
    }

    int length = rendererId.length();
    if (length == 0) {
      return rendererId;
    }

    if (rendererId.charAt(0) > ' ' && rendererId.charAt(length - 1) > ' ') {
      boolean normalized = true;
      for (int i = 0; i < length; i++) {
        char character = rendererId.charAt(i);
        if (character > 127 || (character >= 'A' && character <= 'Z')) {
          normalized = false;
          break;
        }
      }

      if (normalized) {
        return rendererId;
      }
    }

    return rendererId.toLowerCase(Locale.ROOT).trim();
  }

  private long effectiveFrameMapPushIntervalMs() {
    long baseInterval = Math.max(50L, frameMapPushIntervalMs);
    if (!inStartupBoost()) {
      return baseInterval;
    }

    long startupInterval = Math.max(25L, startupFrameMapPushIntervalMs);
    return Math.min(baseInterval, startupInterval);
  }

  private long effectiveFrameMapPushStateRetentionMs() {
    long minRetention = effectiveFrameMapIdlePushIntervalMs() * 2L;
    return Math.max(minRetention, frameMapPushStateRetentionMs);
  }

  private long effectiveFrameMapIdlePushIntervalMs() {
    long idleInterval = Math.max(1_000L, frameMapIdlePushIntervalMs);
    return Math.max(idleInterval, effectiveFrameMapPushIntervalMs());
  }

  private long effectiveInventoryRepairCadenceMs() {
    long baseCadence = Math.max(50L, inventoryRepairCadenceMs);
    if (!inStartupBoost()) {
      return baseCadence;
    }

    return Math.min(baseCadence, 100L);
  }

  private long effectiveItemFrameRepairCadenceMs() {
    long baseCadence = Math.max(50L, itemFrameRepairCadenceMs);
    if (!inStartupBoost()) {
      return baseCadence;
    }

    return Math.min(baseCadence, 100L);
  }

  private int effectiveInventoryBatchSize() {
    int configured = Math.max(1, inventoryRepairBatchSize);
    if (!inStartupBoost()) {
      return configured;
    }

    return Math.max(configured, Math.max(1, startupBoostInventoryBatchSize));
  }

  private int effectiveItemFrameChunkBatchSize() {
    int configured = Math.max(1, itemFrameChunkBatchSize);
    if (!inStartupBoost()) {
      return configured;
    }

    return Math.max(configured, Math.max(1, startupBoostItemFrameChunkBatchSize));
  }

  private boolean inStartupBoost() {
    return System.currentTimeMillis() < startupBoostUntilMs;
  }

  private void applyMaintenanceTickInterval() {
    setTinterval(Math.max(50L, maintenanceTickIntervalMs));
  }

  private double effectiveFrameMapPushRadiusSq() {
    double radius = Math.max(1D, frameMapPushRadiusBlocks);
    return radius * radius;
  }

  private double effectiveFrameMapLookDotThreshold() {
    return Math.max(0D, Math.min(0.999D, frameMapLookDotThreshold));
  }

  private static final class FrameMapViewerSnapshot {
    private final Map<UUID, Map<Long, List<FrameMapViewer>>> viewersByWorldChunk;
    private final Map<Integer, List<FrameMapViewer>> viewersHoldingMap;

    private FrameMapViewerSnapshot(
        Map<UUID, Map<Long, List<FrameMapViewer>>> viewersByWorldChunk,
        Map<Integer, List<FrameMapViewer>> viewersHoldingMap
    ) {
      this.viewersByWorldChunk = viewersByWorldChunk;
      this.viewersHoldingMap = viewersHoldingMap;
    }
  }

  private static final class FrameMapViewer {
    private final UUID playerId;
    private final UUID worldId;
    private final double x;
    private final double y;
    private final double z;
    private final double eyeX;
    private final double eyeY;
    private final double eyeZ;
    private final double directionX;
    private final double directionY;
    private final double directionZ;
    private final boolean hasDirection;
    private final int mainHandMapId;
    private final int offHandMapId;

    private FrameMapViewer(
        UUID playerId,
        UUID worldId,
        double x,
        double y,
        double z,
        double eyeX,
        double eyeY,
        double eyeZ,
        double directionX,
        double directionY,
        double directionZ,
        boolean hasDirection,
        int mainHandMapId,
        int offHandMapId
    ) {
      this.playerId = playerId;
      this.worldId = worldId;
      this.x = x;
      this.y = y;
      this.z = z;
      this.eyeX = eyeX;
      this.eyeY = eyeY;
      this.eyeZ = eyeZ;
      this.directionX = directionX;
      this.directionY = directionY;
      this.directionZ = directionZ;
      this.hasDirection = hasDirection;
      this.mainHandMapId = mainHandMapId;
      this.offHandMapId = offHandMapId;
    }

    private boolean isHoldingMap(int mapId) {
      return mainHandMapId == mapId || offHandMapId == mapId;
    }
  }

  private record RendererMetadata(String normalizedId, String rendererLine, String scopeLine, String idLine) {
  }

  public record MegamapStatus(int gridWidth, int gridHeight, int frames, MegamapGrid.DefectReason defect) {
    private MegamapStatus mergeWith(MegamapStatus other) {
      if (other == null) {
        return this;
      }

      boolean preferOther = (other.gridWidth * other.gridHeight) > (gridWidth * gridHeight);
      return new MegamapStatus(
          preferOther ? other.gridWidth : gridWidth,
          preferOther ? other.gridHeight : gridHeight,
          frames + other.frames,
          defect == null ? other.defect : defect
      );
    }
  }

  // Mutated from region threads and read from the maintenance thread, so every mutable
  // field is volatile.
  private static final class ActiveFrameMap {
    private final UUID frameId;
    private final UUID worldId;
    private final long firstSeenMs;
    private volatile int mapId;
    private volatile int gridMapId = -1;
    private volatile Location location;
    private volatile long lastSeenMs;
    private volatile long lastValidatedMs;
    private volatile MapView view;
    private volatile BlockFace facing;
    private volatile boolean rotationAligned;
    private volatile int blockX;
    private volatile int blockY;
    private volatile int blockZ;
    private volatile String rendererId;

    private ActiveFrameMap(UUID frameId, UUID worldId, int mapId, Location location, long lastSeenMs) {
      this.frameId = frameId;
      this.worldId = worldId;
      this.mapId = mapId;
      this.location = location;
      this.lastSeenMs = lastSeenMs;
      this.firstSeenMs = lastSeenMs;
    }
  }

  private record FramePushKey(int mapId, UUID playerId) {
  }
}
