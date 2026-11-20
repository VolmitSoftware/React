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
import art.arcane.react.api.rendering.Graph;
import art.arcane.react.api.rendering.MapRendererRegistry;
import art.arcane.react.api.rendering.MapRuntime;
import art.arcane.react.api.rendering.MapRuntimeConfig;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.MegamapStatus;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.RendererAdaptMetrics;
import art.arcane.react.api.rendering.RendererBiletoolsMetrics;
import art.arcane.react.api.rendering.RendererGlossMetrics;
import art.arcane.react.api.rendering.RendererHiddenoreMetrics;
import art.arcane.react.api.rendering.RendererIrisMetrics;
import art.arcane.react.api.rendering.RendererIrisWorldMetrics;
import art.arcane.react.api.rendering.RendererReactMetrics;
import art.arcane.react.api.rendering.RendererWormholesMetrics;
import art.arcane.react.api.rendering.SamplerRenderer;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.platform.bukkit.BukkitMapHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@ConfigDescription("Manages React map item metadata, renderer repair, and live map packet delivery for inventories and item frames.")
public class MapController extends TickedObject implements IController, MapRuntimeConfig {
  private static final List<String> INTEGRATION_RENDERER_CAPABILITIES = List.of(
      "iris",
      "adapt",
      "wormholes",
      "gloss",
      "hiddenore",
      "biletools",
      "animus"
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
  @ConfigDoc(value = "Minimum interval between canvas redraws for held React maps in milliseconds.", impact = "Lower values rotate and refresh held maps more smoothly; higher values cut render work when many players view the same map.")
  private long heldMapRedrawIntervalMs = 150L;
  @ConfigDoc(value = "Minimum interval between canvas redraws for item-frame React maps in milliseconds.", impact = "Lower values refresh frame dashboards faster; higher values collapse redundant per-viewer redraws on busy map walls.")
  private long frameMapRedrawIntervalMs = 300L;
  @ConfigDoc(value = "Interval between full item-stack revalidations of tracked frame maps in milliseconds.", impact = "Lower values detect swapped frame items sooner; higher values avoid repeated item metadata reads every maintenance tick.")
  private long frameMapValidateIntervalMs = 2_000L;
  @ConfigDoc(value = "Maximum tracked item-frame maps considered for packet pushes per maintenance pass.", impact = "Higher values refresh very large map walls sooner; lower values cap scheduler and entity-lookup pressure.")
  private int frameMapPushBatchSize = 128;
  @ConfigDoc(value = "Combines adjacent item-frame React maps with the same renderer into one large tiled megamap display.", impact = "Disable to render every frame map as an independent 128x128 dashboard.")
  private boolean megamapEnabled = true;
  @ConfigDoc(value = "Total tile budget for one combined megamap wall.", impact = "Walls larger than the budget fall back to zoomed single-dashboard rendering.")
  private int megamapMaxTiles = 32;
  @ConfigDoc(value = "Cloned React maps placed in item frames are automatically given fresh map ids so adjacent copies can combine into a megamap wall.", impact = "Disable to keep cloned maps sharing one picture and never combining.")
  private boolean megamapSplitDuplicates = true;
  private transient MapRendererRegistry registry;
  private transient BukkitMapHost host;
  private transient MapRuntime<ItemStack> runtime;
  private transient java.util.concurrent.atomic.AtomicBoolean maintenanceTickQueued;
  private transient List<ReactRenderer> irisMetricsRenderers;
  private transient ReactRenderer adaptMetricsRenderer;
  private transient ReactRenderer wormholesMetricsRenderer;
  private transient ReactRenderer glossMetricsRenderer;
  private transient ReactRenderer hiddenoreMetricsRenderer;
  private transient ReactRenderer biletoolsMetricsRenderer;
  private transient ReactRenderer reactMetricsRenderer;
  private transient int integrationRendererFingerprint;

  public MapController() {
    super("react", "map", 250);
  }

  public ReactRenderer getRendererById(String rendererId) {
    return registry == null ? null : registry.resolve(rendererId);
  }

  public Map<String, ReactRenderer> getRenderers() {
    return registry == null ? null : registry.renderers();
  }

  public void openRenderer(Player player, ReactRenderer renderer) {
    if (renderer != null) {
      giveMap(player, renderer);
    }
  }

  public void giveMap(Player player, ReactRenderer renderer) {
    if (player != null && runtime != null) {
      runtime.giveMap(player.getUniqueId(), renderer);
    }
  }

  public void giveMapToInventory(Player player, ReactRenderer renderer) {
    if (player != null && runtime != null) {
      runtime.giveMapToInventory(player.getUniqueId(), renderer);
    }
  }

  public boolean isReactMap(ItemStack item) {
    return runtime != null && runtime.isReactMapItem(item);
  }

  public Location resolveMapAnchor(MapRenderTarget target) {
    MapRuntime.FrameAnchor anchor = runtime == null || target == null ? null : runtime.frameAnchor(target.mapId());
    if (anchor != null) {
      World world = Bukkit.getWorld(anchor.worldId());
      if (world != null) {
        return new Location(world, anchor.x(), anchor.y(), anchor.z());
      }
    }

    Player viewer = target == null || target.viewerId() == null ? null : Bukkit.getPlayer(target.viewerId());
    if (viewer != null && viewer.getWorld() != null) {
      return viewer.getLocation().clone();
    }

    World mapWorld = target == null || target.worldId() == null ? null : Bukkit.getWorld(target.worldId());
    return mapWorld == null ? null : mapWorld.getSpawnLocation().clone();
  }

  public boolean hasFrameAnchor(MapRenderTarget target) {
    return target != null && runtime != null && runtime.frameAnchor(target.mapId()) != null;
  }

  public boolean hasFrameAnchor(MapView view) {
    return view != null && runtime != null && runtime.frameAnchor(view.getId()) != null;
  }

  public void scheduleFrameRefresh(ItemFrame frame) {
    if (frame != null && runtime != null) {
      runtime.scheduleFrameRefresh(frame.getUniqueId());
    }
  }

  public ItemStack createMap(World world, ReactRenderer renderer) {
    return runtime == null || world == null ? null : runtime.createMap(world.getUID(), renderer);
  }

  public MegamapGrid.MegamapTile megamapTileFor(MapView view) {
    return view == null || runtime == null ? null : runtime.megamapTileFor(view.getId());
  }

  public MegamapGrid.MegamapDefect megamapDefectFor(MapView view) {
    return view == null || runtime == null ? null : runtime.megamapDefectFor(view.getId());
  }

  public Map<String, MegamapStatus> megamapStatusByRenderer() {
    return runtime == null ? Map.of() : runtime.megamapStatusByRenderer();
  }

  @Override
  public String getName() {
    return "Map";
  }

  @Override
  public void start() {
    if (runtime != null) {
      runtime.stop();
    }
    if (host != null) {
      host.releaseAll();
    }

    Graph.bindLatest(samplerId -> {
      HistoryController history = React.instance == null ? null : React.controller(HistoryController.class);
      MetricSnapshotValue snapshot = history == null ? null : history.latest().value(samplerId);
      return snapshot == null ? null : new Graph.Latest(snapshot.available(), snapshot.value());
    });
    registry = new MapRendererRegistry();
    registry.catalog(this::dynamicRenderers);
    host = new BukkitMapHost(React.instance);
    runtime = new MapRuntime<ItemStack>(host, this, registry);
    host.attach(runtime);
    maintenanceTickQueued = new java.util.concurrent.atomic.AtomicBoolean(false);
    irisMetricsRenderers = new ArrayList<>();
    irisMetricsRenderers.addAll(RendererIrisMetrics.dashboards());
    irisMetricsRenderers.addAll(RendererIrisWorldMetrics.dashboards());
    adaptMetricsRenderer = new RendererAdaptMetrics();
    wormholesMetricsRenderer = new RendererWormholesMetrics();
    glossMetricsRenderer = new RendererGlossMetrics();
    hiddenoreMetricsRenderer = new RendererHiddenoreMetrics();
    biletoolsMetricsRenderer = new RendererBiletoolsMetrics();
    reactMetricsRenderer = new RendererReactMetrics();
    integrationRendererFingerprint = -1;
    applyMaintenanceTickInterval();
    runtime.start();
  }

  @Override
  public void stop() {
    if (maintenanceTickQueued != null) {
      maintenanceTickQueued.set(false);
    }
    if (runtime != null) {
      runtime.stop();
    }
    if (host != null) {
      host.releaseAll();
    }
  }

  @Override
  public void postStart() {
    if (runtime == null) {
      return;
    }

    runtime.boostStartup();
    SampleController sampleController = React.controller(SampleController.class);
    if (sampleController != null && sampleController.getSamplers() != null) {
      for (Sampler sampler : sampleController.getSamplers().all()) {
        if (!isAbsentIntegrationRenderer(SamplerRenderer.of(sampler))) {
          registerRenderer(sampler);
        }
      }
    }

    FeatureController featureController = React.controller(FeatureController.class);
    if (featureController != null && featureController.getFeatures() != null) {
      for (Feature feature : featureController.getFeatures().all()) {
        if (feature instanceof ReactRenderer renderer && !isAbsentIntegrationRenderer(renderer)) {
          registerRenderer(renderer);
        }
      }
    }

    syncIntegrationRenderers();
    runtime.seedOnlineState();
  }

  @Override
  public void onTick() {
    applyMaintenanceTickInterval();
    MapRuntime<ItemStack> active = runtime;
    java.util.concurrent.atomic.AtomicBoolean queued = maintenanceTickQueued;
    if (active == null) {
      return;
    }

    Runnable maintenanceTick = () -> {
      try {
        if (active != runtime || !active.isActive()) {
          return;
        }
        syncIntegrationRenderers();
        active.maintenance();
      } finally {
        if (queued != null) {
          queued.set(false);
        }
      }
    };

    if (Bukkit.isPrimaryThread()) {
      maintenanceTick.run();
      return;
    }

    if (queued != null && queued.compareAndSet(false, true)) {
      J.sync(maintenanceTick);
    }
  }

  public void registerRenderer(ReactRenderer renderer) {
    if (registry != null) {
      registry.register(renderer);
    }
  }

  public void registerRenderer(Sampler sampler) {
    if (sampler != null) {
      registerRenderer(SamplerRenderer.of(sampler));
    }
  }

  public void unregisterRenderer(String rendererId) {
    if (registry != null) {
      registry.unregister(rendererId);
    }
  }

  private List<ReactRenderer> dynamicRenderers(String requested, String requestedAlias) {
    List<ReactRenderer> candidates = new ArrayList<>();
    ReactRenderer metrics = integrationMetricsRenderer(requested, requestedAlias);
    if (metrics != null) {
      candidates.add(metrics);
    }

    SampleController sampleController = React.controller(SampleController.class);
    if (sampleController != null && sampleController.getSamplers() != null) {
      for (Sampler sampler : sampleController.getSamplers().all()) {
        candidates.add(SamplerRenderer.of(sampler));
      }
    }

    FeatureController featureController = React.controller(FeatureController.class);
    if (featureController != null && featureController.getFeatures() != null) {
      for (Feature feature : featureController.getFeatures().all()) {
        if (feature instanceof ReactRenderer renderer) {
          candidates.add(renderer);
        }
      }
    }
    return candidates;
  }

  private void syncIntegrationRenderers() {
    if (registry == null) {
      return;
    }

    IntegrationController integration = React.controller(IntegrationController.class);
    int fingerprint = 0;
    for (int index = 0; index < INTEGRATION_RENDERER_CAPABILITIES.size(); index++) {
      if (IntegrationCapabilitySupport.isCapabilityPresent(integration, INTEGRATION_RENDERER_CAPABILITIES.get(index))) {
        fingerprint |= 1 << index;
      }
    }

    if (fingerprint == integrationRendererFingerprint) {
      return;
    }

    integrationRendererFingerprint = fingerprint;
    registerRenderer(reactMetricsRenderer);
    for (int index = 0; index < INTEGRATION_RENDERER_CAPABILITIES.size(); index++) {
      // Do not remove these renderer ids while integration is negotiating after reload.
      if ((fingerprint & (1 << index)) != 0) {
        syncIntegrationCapabilityRenderers(INTEGRATION_RENDERER_CAPABILITIES.get(index));
      }
    }
  }

  private void syncIntegrationCapabilityRenderers(String capability) {
    String prefix = capability + "-";
    for (ReactRenderer dashboard : integrationDashboardsFor(capability)) {
      registerRenderer(dashboard);
    }

    SampleController sampleController = React.controller(SampleController.class);
    if (sampleController != null && sampleController.getSamplers() != null) {
      for (Sampler sampler : sampleController.getSamplers().all()) {
        if (sampler != null && MapRendererRegistry.normalize(sampler.getId()).startsWith(prefix)) {
          registerRenderer(sampler);
        }
      }
    }

    FeatureController featureController = React.controller(FeatureController.class);
    if (featureController != null && featureController.getFeatures() != null) {
      for (Feature feature : featureController.getFeatures().all()) {
        if (feature instanceof ReactRenderer renderer && MapRendererRegistry.normalize(feature.getId()).startsWith(prefix)) {
          registerRenderer(renderer);
        }
      }
    }
  }

  private List<ReactRenderer> integrationDashboardsFor(String capability) {
    return switch (capability) {
      case "iris" -> irisMetricsRenderers == null ? List.of() : irisMetricsRenderers;
      case "adapt" -> adaptMetricsRenderer == null ? List.of() : List.of(adaptMetricsRenderer);
      case "wormholes" -> wormholesMetricsRenderer == null ? List.of() : List.of(wormholesMetricsRenderer);
      case "gloss" -> glossMetricsRenderer == null ? List.of() : List.of(glossMetricsRenderer);
      case "hiddenore" -> hiddenoreMetricsRenderer == null ? List.of() : List.of(hiddenoreMetricsRenderer);
      case "biletools" -> biletoolsMetricsRenderer == null ? List.of() : List.of(biletoolsMetricsRenderer);
      default -> List.of();
    };
  }

  private boolean isAbsentIntegrationRenderer(ReactRenderer renderer) {
    if (renderer == null || renderer.getId() == null) {
      return false;
    }

    String pluginId = IntegrationCapabilitySupport.integrationPluginFor(MapRendererRegistry.normalize(renderer.getId()));
    return pluginId != null
        && !IntegrationCapabilitySupport.isCapabilityPresent(React.controller(IntegrationController.class), pluginId);
  }

  private ReactRenderer integrationMetricsRenderer(String requested, String requestedAlias) {
    if (irisMetricsRenderers != null) {
      for (ReactRenderer renderer : irisMetricsRenderers) {
        if (renderer != null && (requested.equals(renderer.getId()) || requestedAlias.equals(renderer.getId()))) {
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
    if (requested.equals(RendererGlossMetrics.ID) || requestedAlias.equals(RendererGlossMetrics.ID)) {
      return glossMetricsRenderer;
    }
    if (requested.equals(RendererHiddenoreMetrics.ID) || requestedAlias.equals(RendererHiddenoreMetrics.ID)) {
      return hiddenoreMetricsRenderer;
    }
    if (requested.equals(RendererBiletoolsMetrics.ID) || requestedAlias.equals(RendererBiletoolsMetrics.ID)) {
      return biletoolsMetricsRenderer;
    }
    return null;
  }

  private void applyMaintenanceTickInterval() {
    setTinterval(Math.max(50L, maintenanceTickIntervalMs));
  }
}
