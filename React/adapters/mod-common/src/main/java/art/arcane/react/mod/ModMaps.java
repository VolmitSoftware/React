package art.arcane.react.mod;

import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapChunkSampler;
import art.arcane.react.api.rendering.MapHubModel;
import art.arcane.react.api.rendering.MapRendererRegistry;
import art.arcane.react.api.rendering.MapRuntime;
import art.arcane.react.api.rendering.MapRuntimeConfig;
import art.arcane.react.api.rendering.MapSettings;
import art.arcane.react.api.rendering.MegamapStatus;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.SamplerRenderer;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.platform.MapHubAction;
import art.arcane.react.platform.MapHubScreen;
import art.arcane.react.util.project.config.TomlFiles;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * React maps on a mod server: the renderer registry over the core samplers, the shared map runtime on
 * {@link ModMapHost}, and the map hub. Mirrors the Bukkit MapController and ReactMapGUI. The settings live in
 * {@code core/map.toml} as on Bukkit, and an edit to that file is applied while the server runs, as Bukkit's hotload does.
 */
final class ModMaps implements MapRuntimeConfig {
    private static final MapHubModel MODEL = new MapHubModel(
            (ReactRenderer renderer) -> SamplerRenderer.samplerOf(renderer) instanceof Sampler sampler ? sampler.getName() : null);

    private static final String SETTINGS_SOURCE_TAG = "core:map";
    private final MinecraftServer server;
    private final File settingsFile;
    private volatile MapSettings settings = new MapSettings();
    private volatile ModReactPlatform activePlatform;
    private final ModTicks ticks;
    private final ModMapHost host;
    private final ModMapHubScreenHost hub;
    private final Supplier<SampleController> samples;
    private volatile MapRendererRegistry registry;
    private volatile MapRuntime<ItemStack> runtime;
    private volatile ModDashboardMaps dashboards;

    ModMaps(MinecraftServer server, File dataFolder, ModTicks ticks, Supplier<SampleController> samples) {
        this.server = server;
        this.settingsFile = new File(new File(dataFolder, "core"), "map.toml");
        this.ticks = ticks;
        this.host = new ModMapHost(server, ticks);
        this.hub = new ModMapHubScreenHost(server, ticks);
        this.samples = samples;
    }

    void start(ModReactPlatform platform) {
        try {
            settings = TomlFiles.load(settingsFile, MapSettings.class, new MapSettings(), SETTINGS_SOURCE_TAG, true, platform::info);
        } catch (IOException failure) {
            platform.warn("Failed to load config for Map: " + failure.getMessage(), failure);
        }
        activePlatform = platform;
        MapRendererRegistry created = new MapRendererRegistry();
        created.catalog(this::samplerRenderers);
        MapRuntime<ItemStack> started = new MapRuntime<ItemStack>(host, this, created);
        registry = created;
        runtime = started;
        started.start();
        ModDashboardMaps createdDashboards = ModDashboardMaps.create(platform);
        createdDashboards.registerInto(created);
        dashboards = createdDashboards;
        for (ReactRenderer renderer : samplerRenderers("", "")) {
            created.register(renderer);
        }
        started.seedOnlineState();
    }

    /** The frame a map hangs in, for framed heatmaps; null while maps are stopped or the map is not framed. */
    MapRuntime.FrameAnchor frameAnchor(int mapId) {
        MapRuntime<ItemStack> active = runtime;
        return active == null ? null : active.frameAnchor(mapId);
    }

    /** The dashboard heatmaps React Web lists, empty while maps are stopped. */
    List<ChunkGridExporter> heatmapExporters() {
        ModDashboardMaps active = dashboards;
        return active == null ? List.of() : active.heatmapExporters();
    }

    Optional<HeatmapChunkSampler> heatmapSampler() {
        ModDashboardMaps active = dashboards;
        return active == null ? Optional.empty() : Optional.of(active.heatmapSampler());
    }

    void tick() {
        MapRuntime<ItemStack> active = runtime;
        if (active != null) {
            active.onTick(System.currentTimeMillis());
        }
    }

    MapSettings settings() {
        return settings;
    }

    File settingsFile() {
        return settingsFile;
    }

    /**
     * Re-reads {@code core/map.toml} on the server thread; a file that does not parse keeps the current settings.
     */
    boolean reloadSettings() {
        ModReactPlatform platform = activePlatform;
        try {
            settings = TomlFiles.load(settingsFile, MapSettings.class, settings, SETTINGS_SOURCE_TAG, false,
                    platform == null ? (String message) -> { } : platform::info);
            return true;
        } catch (IOException | RuntimeException failure) {
            if (platform != null) {
                platform.warn("Kept the current map settings; core/map.toml could not be read: " + failure.getMessage());
            }
            return false;
        }
    }

    void stop() {
        MapRuntime<ItemStack> active = runtime;
        runtime = null;
        registry = null;
        activePlatform = null;
        dashboards = null;
        if (active != null) {
            active.stop();
        }
    }

    private List<ReactRenderer> samplerRenderers(String requested, String requestedAlias) {
        List<ReactRenderer> candidates = new ArrayList<>();
        SampleController controller = samples.get();
        if (controller != null && controller.getSamplers() != null) {
            for (Sampler sampler : controller.getSamplers().all()) {
                candidates.add(SamplerRenderer.of(sampler));
            }
        }
        return candidates;
    }

    List<String> rendererIds() {
        MapRendererRegistry current = registry;
        List<String> ids = new ArrayList<>();
        if (current != null) {
            for (ReactRenderer renderer : current.renderers().values()) {
                ids.add(renderer.getId());
            }
        }
        return ids;
    }

    /** {@code /react map [renderer]}: a named renderer goes to the main hand, anything else opens the hub. */
    void map(ServerPlayer player, String rendererId) {
        MapRendererRegistry current = registry;
        ReactRenderer renderer = null;
        if (rendererId != null && current != null) {
            for (ReactRenderer candidate : current.renderers().values()) {
                if (candidate.getId().equalsIgnoreCase(rendererId)) {
                    renderer = candidate;
                    break;
                }
            }
        }
        if (renderer == null || MapHubModel.UNKNOWN_RENDERER_ID.equalsIgnoreCase(renderer.getId())) {
            openHub(player);
            return;
        }
        MapRuntime<ItemStack> active = runtime;
        if (active != null) {
            active.giveMap(player.getUUID(), renderer);
        }
    }

    void openHub(ServerPlayer player) {
        LanguageAudience.run(player.getUUID(), () -> {
            List<ReactRenderer> renderers = renderers(player);
            MapRuntime<ItemStack> active = runtime;
            if (renderers.isEmpty() || active == null) {
                return;
            }
            hub.playPageTurn(player.getUUID());
            MapHubScreen screen = MODEL.hub(renderers, active.megamapStatusByRenderer());
            hub.open(player.getUUID(), screen, (viewerId, tile, click, action) -> handle(player, byId(renderers), action));
        });
    }

    private void openGroup(ServerPlayer player, int groupOrder, int page) {
        LanguageAudience.run(player.getUUID(), () -> {
            List<ReactRenderer> renderers = renderers(player);
            MapRuntime<ItemStack> active = runtime;
            if (renderers.isEmpty() || active == null) {
                return;
            }
            Map<String, MegamapStatus> megamapStatus = active.megamapStatusByRenderer();
            MapHubScreen screen = MODEL.group(renderers, groupOrder, page, megamapStatus);
            if (screen == null) {
                openHub(player);
                return;
            }
            hub.playPageTurn(player.getUUID());
            hub.open(player.getUUID(), screen, (viewerId, tile, click, action) -> handle(player, byId(renderers), action));
        });
    }

    private List<ReactRenderer> renderers(ServerPlayer player) {
        MapRendererRegistry current = registry;
        if (current == null) {
            ReactLanguage.send(player, GuiMessages.MAP_CONTROLLER_UNAVAILABLE);
            return List.of();
        }
        List<ReactRenderer> renderers = MODEL.sortedRenderers(current.renderers().values());
        if (renderers.isEmpty()) {
            ReactLanguage.send(player, GuiMessages.MAP_NONE_AVAILABLE);
        }
        return renderers;
    }

    private static Map<String, ReactRenderer> byId(List<ReactRenderer> renderers) {
        Map<String, ReactRenderer> byId = new LinkedHashMap<>();
        for (ReactRenderer renderer : renderers) {
            byId.put(renderer.getId(), renderer);
        }
        return byId;
    }

    private void handle(ServerPlayer player, Map<String, ReactRenderer> renderers, MapHubAction action) {
        if (player.hasDisconnected()) {
            return;
        }
        MapRuntime<ItemStack> active = runtime;
        switch (action) {
            case MapHubAction.OpenHub ignored -> openHub(player);
            case MapHubAction.OpenGroup group -> openGroup(player, group.groupOrder(), group.page());
            case MapHubAction.SelectMap select -> {
                ReactRenderer renderer = renderers.get(select.rendererId());
                if (renderer != null && active != null) {
                    active.giveMap(player.getUUID(), renderer);
                }
                ReactLanguage.send(player, GuiMessages.MAP_SELECTED,
                        MessageArgument.untrusted("renderer", select.rendererName()),
                        MessageArgument.untrusted("scope", select.scopeTag()));
                hub.close(player.getUUID());
            }
            case MapHubAction.AddMap add -> {
                ReactRenderer renderer = renderers.get(add.rendererId());
                if (renderer != null && active != null) {
                    active.giveMapToInventory(player.getUUID(), renderer);
                }
                ReactLanguage.send(player, GuiMessages.MAP_ADDED,
                        MessageArgument.untrusted("renderer", add.rendererName()),
                        MessageArgument.untrusted("scope", add.scopeTag()));
            }
        }
    }

    @Override
    public long getMaintenanceTickIntervalMs() {
        return settings.getMaintenanceTickIntervalMs();
    }

    @Override
    public long getInventoryRepairCadenceMs() {
        return settings.getInventoryRepairCadenceMs();
    }

    @Override
    public int getInventoryRepairBatchSize() {
        return settings.getInventoryRepairBatchSize();
    }

    @Override
    public long getItemFrameRepairCadenceMs() {
        return settings.getItemFrameRepairCadenceMs();
    }

    @Override
    public int getItemFrameChunkBatchSize() {
        return settings.getItemFrameChunkBatchSize();
    }

    @Override
    public long getStartupBoostDurationMs() {
        return settings.getStartupBoostDurationMs();
    }

    @Override
    public int getStartupBoostInventoryBatchSize() {
        return settings.getStartupBoostInventoryBatchSize();
    }

    @Override
    public int getStartupBoostItemFrameChunkBatchSize() {
        return settings.getStartupBoostItemFrameChunkBatchSize();
    }

    @Override
    public long getFrameMapPushIntervalMs() {
        return settings.getFrameMapPushIntervalMs();
    }

    @Override
    public long getStartupFrameMapPushIntervalMs() {
        return settings.getStartupFrameMapPushIntervalMs();
    }

    @Override
    public long getFrameMapIdlePushIntervalMs() {
        return settings.getFrameMapIdlePushIntervalMs();
    }

    @Override
    public double getFrameMapPushRadiusBlocks() {
        return settings.getFrameMapPushRadiusBlocks();
    }

    @Override
    public boolean isFrameMapPushOutsideRangeWhenHolding() {
        return settings.isFrameMapPushOutsideRangeWhenHolding();
    }

    @Override
    public double getFrameMapLookDotThreshold() {
        return settings.getFrameMapLookDotThreshold();
    }

    @Override
    public boolean isFrameMapRequireLineOfSight() {
        return settings.isFrameMapRequireLineOfSight();
    }

    @Override
    public long getFrameMapPushStateRetentionMs() {
        return settings.getFrameMapPushStateRetentionMs();
    }

    @Override
    public long getHeldMapRedrawIntervalMs() {
        return settings.getHeldMapRedrawIntervalMs();
    }

    @Override
    public long getFrameMapRedrawIntervalMs() {
        return settings.getFrameMapRedrawIntervalMs();
    }

    @Override
    public long getFrameMapValidateIntervalMs() {
        return settings.getFrameMapValidateIntervalMs();
    }

    @Override
    public int getFrameMapPushBatchSize() {
        return settings.getFrameMapPushBatchSize();
    }

    @Override
    public boolean isMegamapEnabled() {
        return settings.isMegamapEnabled();
    }

    @Override
    public int getMegamapMaxTiles() {
        return settings.getMegamapMaxTiles();
    }

    @Override
    public boolean isMegamapSplitDuplicates() {
        return settings.isMegamapSplitDuplicates();
    }
}
