package art.arcane.react.mod;

import art.arcane.react.core.incident.IncidentRecord;
import art.arcane.react.platform.IncidentHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.api.web.ActionBackend;
import art.arcane.react.api.web.ActionParamSerializer;
import art.arcane.react.api.web.ControlBackend;
import art.arcane.react.api.web.ControlSerializer;
import art.arcane.react.api.web.RegistryActionBackend;
import art.arcane.react.api.web.RegistryControlBackend;
import art.arcane.react.api.web.FeatureWorldBackend;
import art.arcane.react.api.web.WorldBackend;
import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapChunkSampler;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.platform.ComponentHost;
import art.arcane.react.platform.WebHost;
import art.arcane.react.platform.WebMetricsSource;
import art.arcane.react.util.project.config.TomlFiles;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * React Web on a mod server: {@code web.toml} from React's folder, sampler values and history, the dashboard heatmaps,
 * and the server's identity.
 */
final class ModWebHost implements WebHost {
    private final MinecraftServer server;
    private final File dataFolder;
    private final String loader;
    private final WebMetricsSource metrics;
    private final ModMaps maps;
    private final Consumer<String> info;

    ModWebHost(MinecraftServer server, File dataFolder, String loader, WebMetricsSource metrics, ModMaps maps, Consumer<String> info) {
        this.server = server;
        this.dataFolder = dataFolder;
        this.loader = loader;
        this.metrics = metrics;
        this.maps = maps;
        this.info = info;
    }

    @Override
    public Optional<WebConfiguration> loadConfiguration() throws IOException {
        return Optional.of(TomlFiles.load(new File(dataFolder, "web.toml"), WebConfiguration.class, new WebConfiguration(), "web-config", false,
                message -> info.accept("web.toml: " + message)));
    }

    @Override
    public WebMetricsSource metrics() {
        return metrics;
    }

    @Override
    public List<ChunkGridExporter> heatmapExporters() {
        return maps.heatmapExporters();
    }

    @Override
    public WorldBackend worlds() {
        return new FeatureWorldBackend();
    }

    @Override
    public HeatmapChunkSampler heatmapSampler() {
        return (ChunkGridExporter exporter, String world, Integer centerX, Integer centerZ, int radius) -> maps.heatmapSampler()
                .map((HeatmapChunkSampler sampler) -> sampler.scan(exporter, world, centerX, centerZ, radius))
                .orElse(null);
    }

    @Override
    public List<IncidentRecord> incidents(int limit) {
        ReactPlatform platform = ReactPlatforms.getOrNull();
        return platform == null ? List.of() : platform.incidentHost().map((IncidentHost host) -> host.recent(limit)).orElse(List.of());
    }

    @Override
    public String motd() {
        String motd = server.getMotd();
        return motd == null ? "" : motd;
    }

    @Override
    public File serverFolder() {
        return server.getServerDirectory().toFile();
    }

    @Override
    public String serverAddress() {
        String ip = server.getLocalIp();
        return (ip == null ? "" : ip) + ":" + server.getPort();
    }

    @Override
    public String serverVersion() {
        return loader + " " + server.getServerVersion();
    }

    @Override
    public ControlBackend features() {
        return new RegistryControlBackend<Feature>(
                "feature",
                () -> {
                    FeatureController controller = ReactComponents.features();
                    return controller == null || controller.getFeatures() == null ? List.of() : controller.getFeatures().all();
                },
                ReactComponents::feature,
                Feature::isEnabled,
                new ControlSerializer(),
                ModWebHost::applyConfigValue);
    }

    @Override
    public ControlBackend tweaks() {
        return new RegistryControlBackend<Tweak>(
                "tweak",
                () -> {
                    TweakController controller = ReactComponents.tweaks();
                    return controller == null || controller.getTweaks() == null ? List.of() : controller.getTweaks().all();
                },
                ReactComponents::tweak,
                Tweak::isEnabled,
                new ControlSerializer(),
                ModWebHost::applyConfigValue);
    }

    @Override
    public ActionBackend actions() {
        return new RegistryActionBackend(
                () -> {
                    ActionController controller = ReactComponents.actions();
                    return controller == null || controller.getActions() == null ? List.<Action<?>>of() : controller.getActions().all();
                },
                ReactComponents::action,
                new ActionParamSerializer());
    }

    private static boolean applyConfigValue(String path, Object value) {
        return ReactComponents.host().map((ComponentHost host) -> host.applyConfigValue(path, value)).orElse(false);
    }
}
