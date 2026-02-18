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
import art.arcane.react.api.rendering.*;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.feature.FeatureUnknown;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.react.util.config.ConfigDescription;
import art.arcane.react.util.config.ConfigDoc;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.scheduling.J;
import art.arcane.react.util.scheduling.TickedObject;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.util.io.JarScanner;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = true)
@Data
@ConfigDescription("Manages React map item metadata, renderer repair, and live map packet delivery for inventories and item frames.")
public class MapController extends TickedObject implements IController, Listener {
  private static final NamespacedKey nsReact = new NamespacedKey(React.instance, "react");
  private static final NamespacedKey nsRenderer = new NamespacedKey(React.instance, "react-renderer");
  private static final NamespacedKey nsMapToken = new NamespacedKey(React.instance, "react-map-token");
  private static final Set<String> disabledRendererIds = Set.of(
      "iris-generation-pressure-overlay",
      "iris-biome-chunk-share-pie-map",
      "iris-biome-cache-hit-rate"
  );
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
  private transient Map<String, ReactRenderer> renderers;
  private transient Map<String, Long> frameMapPushMsByViewerKey;
  private transient Map<Integer, Long> frameMapLastSeenByMapId;
  private transient Map<UUID, ActiveFrameMap> activeFrameMaps;
  private transient AtomicBoolean maintenanceTickQueued;
  private transient Method itemFrameSetItemSilentMethod;
  private transient boolean itemFrameSetItemSilentMethodResolved;
  private transient long lastInventoryRepairMs;
  private transient long lastItemFrameRepairMs;
  private transient int inventoryRepairCursor;
  private transient int itemFrameWorldCursor;
  private transient int itemFrameChunkCursor;
  private transient ReactRenderer irisMetricsRenderer;
  private transient ReactRenderer adaptMetricsRenderer;
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
    if (item == null || !item.getType().equals(Material.FILLED_MAP)) {
      return false;
    }

    MapMeta meta = (MapMeta) item.getItemMeta();
    if (meta == null) {
      return false;
    }

    if (meta.getPersistentDataContainer().getOrDefault(nsReact, PersistentDataType.BYTE, (byte) 0) == 1) {
      return true;
    }

    String rendererId = meta.getPersistentDataContainer().get(nsRenderer, PersistentDataType.STRING);
    if (isSpecificRendererId(rendererId)) {
      return true;
    }

    return isSpecificRendererId(parseRendererIdFromLore(meta.getLore()));
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
  public void on(ChunkLoadEvent e) {
    refreshChunkItemFrames(e.getChunk(), true);
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
    frameMapPushMsByViewerKey = new ConcurrentHashMap<>();
    frameMapLastSeenByMapId = new ConcurrentHashMap<>();
    activeFrameMaps = new ConcurrentHashMap<>();
    maintenanceTickQueued = new AtomicBoolean(false);
    itemFrameSetItemSilentMethod = null;
    itemFrameSetItemSilentMethodResolved = false;
    lastInventoryRepairMs = 0L;
    lastItemFrameRepairMs = 0L;
    inventoryRepairCursor = 0;
    itemFrameWorldCursor = 0;
    itemFrameChunkCursor = 0;
    renderers.put(FeatureUnknown.ID, new RendererUnknown());
    irisMetricsRenderer = new RendererIrisMetrics();
    adaptMetricsRenderer = new RendererAdaptMetrics();
    reactMetricsRenderer = new RendererReactMetrics();
    startupBoostUntilMs = System.currentTimeMillis() + Math.max(0L, startupBoostDurationMs);
    applyMaintenanceTickInterval();
  }

  private void scanForRenderers(String pkg) {
    String p = React.instance.jar().getAbsolutePath();
    p = p.replaceAll("\\Q.jar.jar\\E", ".jar");
    JarScanner j = new JarScanner(new File(p), pkg);
    try {
      j.scan();
      j.getClasses().stream()
          .filter(i -> i.isAssignableFrom(ReactRenderer.class) || ReactRenderer.class.isAssignableFrom(i))
          .map((i) -> {
            try {
              return (ReactRenderer) i.getConstructor().newInstance();
            } catch (Throwable e) {
              e.printStackTrace();
            }

            return null;
          })
          .forEach((i) -> {
            if (i != null) {
              renderers.put(i.getId(), i);
            }
          });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
  }

  @Override
  public void postStart() {
    startupBoostUntilMs = System.currentTimeMillis() + Math.max(0L, startupBoostDurationMs);
    for (Sampler i : React.controller(SampleController.class).getSamplers().all()) {
      registerRenderer(i);
    }

    for (Feature i : React.controller(FeatureController.class).getFeatures().all()) {
      if (i instanceof ReactRenderer f) {
        registerRenderer(f);
      }
    }

    syncIntegrationRenderers();

    for (Player i : Bukkit.getOnlinePlayers()) {
      join(i);
    }

    refreshLoadedItemFrames();
  }

  @Override
  public void onTick() {
    applyMaintenanceTickInterval();
    Runnable maintenanceTick = () -> {
      try {
        syncIntegrationRenderers();
        repairOneOnlinePlayerInventory();
        pushTrackedFrameMaps();
        repairOneLoadedChunkItemFrames();
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
    syncIntegrationRenderer("iris", irisMetricsRenderer);
    syncIntegrationRenderer("adapt", adaptMetricsRenderer);
    syncIntegrationRenderer("react", reactMetricsRenderer);
    syncIntegrationCapabilityRenderers("iris");
    syncIntegrationCapabilityRenderers("adapt");
  }

  private void syncIntegrationRenderer(String capability, ReactRenderer renderer) {
    if (renderers == null || renderer == null) {
      return;
    }

    // Keep integration dashboards resolvable even when capability is temporarily missing
    // during reload/handshake windows. The renderer itself handles offline status safely.
    registerRenderer(renderer);
  }

  private void syncIntegrationCapabilityRenderers(String capability) {
    if (renderers == null || capability == null || capability.isBlank()) {
      return;
    }

    String prefix = capability.toLowerCase(Locale.ROOT).trim() + "-";
    boolean available = IntegrationCapabilitySupport.hasCapability(
        React.controller(IntegrationController.class),
        capability
    );

    if (!available) {
      // Do not remove these renderer ids while integration is negotiating after reload.
      return;
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

  private void registerRenderer(ReactRenderer renderer) {
    if (renderers == null || renderer == null || renderer.getId() == null) {
      return;
    }

    String normalized = normalizeRendererId(renderer.getId());
    if (disabledRendererIds.contains(normalized)) {
      renderers.remove(renderer.getId());
      return;
    }

    if ("iris-chunk-stream-ms".equals(normalized) && !isIrisPregenActive()) {
      renderers.remove(renderer.getId());
      return;
    }

    renderers.put(renderer.getId(), renderer);
  }

  private boolean isIrisPregenActive() {
    IntegrationController controller = React.controller(IntegrationController.class);
    if (controller == null || controller.getRemoteSamplerBridge() == null) {
      return false;
    }

    var bridge = controller.getRemoteSamplerBridge();
    if (!bridge.isAvailable("iris", IntegrationMetricSchema.IRIS_PREGEN_QUEUE)) {
      return false;
    }

    return bridge.valueOr("iris", IntegrationMetricSchema.IRIS_PREGEN_QUEUE, 0D) > 0D;
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
    String normalized = normalizeRendererId(rendererId);
    String scope = rendererScope(normalized);
    String rendererName = rendererDisplayName(rendererId);
    String previousToken = meta.getPersistentDataContainer().get(nsMapToken, PersistentDataType.STRING);
    String mapToken = getOrCreateMapToken(meta);
    List<String> lore = List.of(
        "Renderer: " + rendererName,
        "Scope: " + scope,
        "ID: " + rendererId,
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
    if (!isReactMap(item)) {
      return false;
    }

    MapMeta meta = (MapMeta) item.getItemMeta();
    if (meta == null) {
      return false;
    }

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
    if (mapViewChanged || metadataChanged || changed) {
      item.setItemMeta(meta);
      return true;
    }

    return false;
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

    String currentId = normalizeRendererId(pipe.getRendererId());
    String expectedId = normalizeRendererId(renderer.getId());
    return currentId.equals(expectedId);
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
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        refreshChunkItemFrames(chunk, true);
      }
    }
  }

  private void refreshChunkItemFrames(Chunk chunk, boolean forceRendererUpdate) {
    if (chunk == null) {
      return;
    }

    for (Entity entity : chunk.getEntities()) {
      if (entity instanceof ItemFrame frame) {
        refreshItemFrame(frame, forceRendererUpdate);
      }
    }
  }

  private void refreshItemFrame(ItemFrame frame, boolean forceRendererUpdate) {
    if (frame == null) {
      return;
    }

    ItemStack item = frame.getItem();
    if (!isReactMap(item)) {
      return;
    }

    ItemStack updated = item.clone();
    boolean metadataChanged = repairMapItem(updated, frame.getWorld(), forceRendererUpdate);
    if (metadataChanged) {
      setFrameItemQuietly(frame, updated);
    }

    ItemStack effectiveItem = metadataChanged ? updated : item;
    MapMeta effectiveMeta = (MapMeta) effectiveItem.getItemMeta();
    if (effectiveMeta == null || effectiveMeta.getMapView() == null) {
      return;
    }

    MapView view = effectiveMeta.getMapView();
    trackFrameMap(frame, view);
    pushFrameMapToNearbyPlayers(frame, view);
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

    activeFrameMaps.compute(frameId, (id, existing) -> {
      if (existing == null || !existing.worldId.equals(worldId)) {
        return new ActiveFrameMap(frameId, worldId, mapId, location, now);
      }

      existing.mapId = mapId;
      existing.location = location;
      existing.lastSeenMs = now;
      return existing;
    });
  }

  private void pushTrackedFrameMaps() {
    if (activeFrameMaps == null || activeFrameMaps.isEmpty()) {
      pruneFramePushState();
      return;
    }

    List<UUID> stale = new ArrayList<>();
    long now = System.currentTimeMillis();

    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      if (tracked == null || tracked.worldId == null) {
        if (tracked != null) {
          stale.add(tracked.frameId);
        }
        continue;
      }

      World world = Bukkit.getWorld(tracked.worldId);
      if (world == null) {
        stale.add(tracked.frameId);
        continue;
      }

      Entity entity;
      try {
        entity = Bukkit.getEntity(tracked.frameId);
      } catch (Throwable ex) {
        React.verbose("Failed to resolve tracked map frame entity " + tracked.frameId + ": "
            + ex.getClass().getSimpleName()
            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        entity = null;
      }

      if (!(entity instanceof ItemFrame frame)
          || !frame.isValid()
          || frame.getWorld() == null
          || !tracked.worldId.equals(frame.getWorld().getUID())) {
        stale.add(tracked.frameId);
        continue;
      }

      ItemStack frameItem = frame.getItem();
      if (!isReactMap(frameItem)) {
        stale.add(tracked.frameId);
        continue;
      }

      MapMeta meta = (MapMeta) frameItem.getItemMeta();
      if (meta == null || meta.getMapView() == null) {
        stale.add(tracked.frameId);
        continue;
      }

      MapView view = meta.getMapView();
      Integer mapId = mapIdOf(view);
      if (mapId == null) {
        stale.add(tracked.frameId);
        continue;
      }

      tracked.mapId = mapId;
      tracked.location = frame.getLocation();
      tracked.lastSeenMs = now;
      pushMapToNearbyPlayers(frame, view);
    }

    for (UUID frameId : stale) {
      activeFrameMaps.remove(frameId);
    }

    pruneFramePushState();
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

  private void repairOneLoadedChunkItemFrames() {
    long now = System.currentTimeMillis();
    if (now - lastItemFrameRepairMs < effectiveItemFrameRepairCadenceMs()) {
      return;
    }
    lastItemFrameRepairMs = now;

    int batch = effectiveItemFrameChunkBatchSize();
    boolean forceRendererUpdate = inStartupBoost();
    for (int i = 0; i < batch; i++) {
      Chunk chunk = nextLoadedChunkForRepair();
      if (chunk == null) {
        return;
      }

      refreshChunkItemFrames(chunk, forceRendererUpdate);
    }
  }

  private Chunk nextLoadedChunkForRepair() {
    List<World> worlds = Bukkit.getWorlds();
    if (worlds.isEmpty()) {
      itemFrameWorldCursor = 0;
      itemFrameChunkCursor = 0;
      return null;
    }

    int worldCount = worlds.size();
    for (int i = 0; i < worldCount; i++) {
      if (itemFrameWorldCursor >= worldCount) {
        itemFrameWorldCursor = 0;
        itemFrameChunkCursor = 0;
      }

      World world = worlds.get(itemFrameWorldCursor);
      Chunk[] loadedChunks = world.getLoadedChunks();
      if (loadedChunks.length == 0) {
        itemFrameWorldCursor = (itemFrameWorldCursor + 1) % worldCount;
        itemFrameChunkCursor = 0;
        continue;
      }

      if (itemFrameChunkCursor >= loadedChunks.length) {
        itemFrameChunkCursor = 0;
        itemFrameWorldCursor = (itemFrameWorldCursor + 1) % worldCount;
        continue;
      }

      Chunk next = loadedChunks[itemFrameChunkCursor++];
      if (itemFrameChunkCursor >= loadedChunks.length) {
        itemFrameChunkCursor = 0;
        itemFrameWorldCursor = (itemFrameWorldCursor + 1) % worldCount;
      }

      return next;
    }

    return null;
  }

  private void pushFrameMapToNearbyPlayers(ItemFrame frame, MapView view) {
    if (frame == null || frame.getWorld() == null || view == null) {
      return;
    }

    pushMapToNearbyPlayers(frame, view);
  }

  private void pushMapToNearbyPlayers(ItemFrame frame, MapView view) {
    if (frame == null || frame.getWorld() == null || view == null || frameMapPushMsByViewerKey == null) {
      return;
    }

    World world = frame.getWorld();
    Location source = frame.getLocation();
    Integer mapId = mapIdOf(view);
    if (mapId == null) {
      return;
    }

    long now = System.currentTimeMillis();
    double radiusSq = effectiveFrameMapPushRadiusSq();
    long activeInterval = effectiveFrameMapPushIntervalMs();
    long idleInterval = effectiveFrameMapIdlePushIntervalMs();
    boolean requireLineOfSight = frameMapRequireLineOfSight;

    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player == null || player.getWorld() == null) {
        continue;
      }

      boolean holding = isHoldingMap(player, mapId);
      boolean holdingBypassesRange = holding && frameMapPushOutsideRangeWhenHolding;
      boolean sameWorld = player.getWorld().equals(world);
      if (!holdingBypassesRange && !sameWorld) {
        continue;
      }

      boolean withinRadius = false;
      if (sameWorld) {
        double distanceSq = player.getLocation().distanceSquared(source);
        withinRadius = distanceSq <= radiusSq;
      }

      if (!holdingBypassesRange && !withinRadius) {
        continue;
      }

      boolean activelyWatching = holding || isLikelyLookingAtFrame(player, source);
      long requiredInterval = activelyWatching ? activeInterval : idleInterval;
      String pushKey = framePushKey(mapId, player.getUniqueId());
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

  private boolean isLikelyLookingAtFrame(Player player, Location source) {
    if (player == null || source == null) {
      return false;
    }

    var eye = player.getEyeLocation();
    var toFrame = source.toVector().subtract(eye.toVector());
    if (toFrame.lengthSquared() <= 1.0E-6D) {
      return true;
    }

    var direction = eye.getDirection();
    if (direction.lengthSquared() <= 1.0E-6D) {
      return false;
    }

    toFrame.normalize();
    direction.normalize();
    return direction.dot(toFrame) >= effectiveFrameMapLookDotThreshold();
  }

  private String framePushKey(int mapId, UUID playerId) {
    return mapId + ":" + playerId;
  }

  private long initialPushOffsetMs(String pushKey, long intervalMs) {
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
      updateMapViews(player, forceRendererUpdate);
    }
  }

  private ReactRenderer resolveRenderer(String rendererId) {
    if (renderers == null) {
      return null;
    }

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
    if (requested.equals(RendererIrisMetrics.ID) || requestedAlias.equals(RendererIrisMetrics.ID)) {
      return irisMetricsRenderer;
    }

    if (requested.equals(RendererAdaptMetrics.ID) || requestedAlias.equals(RendererAdaptMetrics.ID)) {
      return adaptMetricsRenderer;
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

    World viewWorld = view.getWorld();
    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      if (tracked == null || tracked.mapId != mapId || tracked.location == null || tracked.location.getWorld() == null) {
        continue;
      }

      if (viewWorld != null && !viewWorld.equals(tracked.location.getWorld())) {
        continue;
      }

      return tracked.location.clone();
    }

    return null;
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
    if (normalizedRendererId.startsWith("react-")) {
      return "React";
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
    return Objects.toString(rendererId, "").toLowerCase(Locale.ROOT).trim();
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

  private static final class ActiveFrameMap {
    private final UUID frameId;
    private final UUID worldId;
    private int mapId;
    private Location location;
    private long lastSeenMs;

    private ActiveFrameMap(UUID frameId, UUID worldId, int mapId, Location location, long lastSeenMs) {
      this.frameId = frameId;
      this.worldId = worldId;
      this.mapId = mapId;
      this.location = location;
      this.lastSeenMs = lastSeenMs;
    }
  }
}
