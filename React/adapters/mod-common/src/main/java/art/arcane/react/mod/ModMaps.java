package art.arcane.react.mod;

import art.arcane.react.api.rendering.MapHubModel;
import art.arcane.react.api.rendering.MapRendererRegistry;
import art.arcane.react.api.rendering.MapRuntime;
import art.arcane.react.api.rendering.MapRuntimeConfig;
import art.arcane.react.api.rendering.MegamapStatus;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.SamplerRenderer;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.platform.MapHubAction;
import art.arcane.react.platform.MapHubScreen;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * React maps on a mod server: the renderer registry over the core samplers, the shared map runtime on
 * {@link ModMapHost}, and the map hub. Mirrors the Bukkit MapController and ReactMapGUI with their default settings.
 */
final class ModMaps implements MapRuntimeConfig {
    private static final MapHubModel MODEL = new MapHubModel(
            (ReactRenderer renderer) -> SamplerRenderer.samplerOf(renderer) instanceof Sampler sampler ? sampler.getName() : null);

    private final MinecraftServer server;
    private final ModMapHost host;
    private final ModMapHubScreenHost hub;
    private final Supplier<SampleController> samples;
    private volatile MapRendererRegistry registry;
    private volatile MapRuntime<ItemStack> runtime;

    ModMaps(MinecraftServer server, ModTicks ticks, Supplier<SampleController> samples) {
        this.server = server;
        this.host = new ModMapHost(server, ticks);
        this.hub = new ModMapHubScreenHost(server, ticks);
        this.samples = samples;
    }

    void start(ModReactPlatform platform) {
        MapRendererRegistry created = new MapRendererRegistry();
        created.catalog(this::samplerRenderers);
        MapRuntime<ItemStack> started = new MapRuntime<ItemStack>(host, this, created);
        registry = created;
        runtime = started;
        started.start();
        ModDashboardMaps.create(server, platform, started::frameAnchor).registerInto(created);
        for (ReactRenderer renderer : samplerRenderers("", "")) {
            created.register(renderer);
        }
        started.seedOnlineState();
    }

    void tick() {
        MapRuntime<ItemStack> active = runtime;
        if (active != null) {
            active.onTick(System.currentTimeMillis());
        }
    }

    void stop() {
        MapRuntime<ItemStack> active = runtime;
        runtime = null;
        registry = null;
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

    // Defaults of the Bukkit MapController settings

    @Override
    public long getMaintenanceTickIntervalMs() {
        return 500L;
    }

    @Override
    public long getInventoryRepairCadenceMs() {
        return 250L;
    }

    @Override
    public int getInventoryRepairBatchSize() {
        return 3;
    }

    @Override
    public long getItemFrameRepairCadenceMs() {
        return 250L;
    }

    @Override
    public int getItemFrameChunkBatchSize() {
        return 8;
    }

    @Override
    public long getStartupBoostDurationMs() {
        return 12_000L;
    }

    @Override
    public int getStartupBoostInventoryBatchSize() {
        return 12;
    }

    @Override
    public int getStartupBoostItemFrameChunkBatchSize() {
        return 32;
    }

    @Override
    public long getFrameMapPushIntervalMs() {
        return 600L;
    }

    @Override
    public long getStartupFrameMapPushIntervalMs() {
        return 100L;
    }

    @Override
    public long getFrameMapIdlePushIntervalMs() {
        return 6_000L;
    }

    @Override
    public double getFrameMapPushRadiusBlocks() {
        return 24.0D;
    }

    @Override
    public boolean isFrameMapPushOutsideRangeWhenHolding() {
        return false;
    }

    @Override
    public double getFrameMapLookDotThreshold() {
        return 0.45D;
    }

    @Override
    public boolean isFrameMapRequireLineOfSight() {
        return true;
    }

    @Override
    public long getFrameMapPushStateRetentionMs() {
        return 600_000L;
    }

    @Override
    public long getHeldMapRedrawIntervalMs() {
        return 150L;
    }

    @Override
    public long getFrameMapRedrawIntervalMs() {
        return 300L;
    }

    @Override
    public long getFrameMapValidateIntervalMs() {
        return 2_000L;
    }

    @Override
    public int getFrameMapPushBatchSize() {
        return 128;
    }

    @Override
    public boolean isMegamapEnabled() {
        return true;
    }

    @Override
    public int getMegamapMaxTiles() {
        return 32;
    }

    @Override
    public boolean isMegamapSplitDuplicates() {
        return true;
    }
}
