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
import art.arcane.react.content.feature.FeatureIncidentMode;
import art.arcane.react.platform.ComponentHost;
import art.arcane.react.platform.WebHost;
import art.arcane.react.platform.WebMetricsSource;
import art.arcane.react.util.project.config.TomlFiles;
import art.arcane.react.api.web.ConfigTreeSerializer;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.api.web.dto.EnvironmentDto;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.gui.ReactSettingsMenu;
import art.arcane.react.core.telemetry.HostTelemetryFeed;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * React Web on a mod server: {@code web.toml} from React's folder, sampler values and history, the dashboard heatmaps,
 * host telemetry, the incident score, the {@code react.toml} config tree and the server's identity.
 */
final class ModWebHost implements WebHost {
    private static final long CONFIG_WAIT_SECONDS = 10L;
    private final MinecraftServer server;
    private final File dataFolder;
    private final String loader;
    private final WebMetricsSource metrics;
    private final ModMaps maps;
    private final Consumer<String> info;
    private final Supplier<SampleController> samples;
    private final HostTelemetryFeed host;
    private final Supplier<Object> mainSettings;

    ModWebHost(MinecraftServer server, File dataFolder, String loader, WebMetricsSource metrics, ModMaps maps, Consumer<String> info,
               Supplier<SampleController> samples, HostTelemetryFeed host, Supplier<Object> mainSettings) {
        this.server = server;
        this.dataFolder = dataFolder;
        this.loader = loader;
        this.metrics = metrics;
        this.maps = maps;
        this.info = info;
        this.samples = samples;
        this.host = host;
        this.mainSettings = mainSettings;
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
        return VanillaForms.serverDirectory(server);
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

    @Override
    public String incidentState() {
        FeatureIncidentMode feature = ReactComponents.feature(FeatureIncidentMode.class);
        if (feature == null) {
            return "UNKNOWN";
        }
        if (!feature.isEnabled()) {
            return "DISABLED";
        }
        return feature.isIncidentActive() ? "ACTIVE" : "NORMAL";
    }

    @Override
    public IncidentScore incidentScore() {
        return IncidentScore.sample(samples.get());
    }

    @Override
    public EnvironmentDto hostEnvironment() {
        return host.snapshot().environment();
    }

    @Override
    public ConfigSectionDto[] configTree() {
        return new ConfigTreeSerializer().serialize(mainSettings.get());
    }

    @Override
    public boolean applyConfig(String path, Object value) {
        return Boolean.TRUE.equals(onSettingsMenu((ReactSettingsMenu menu) -> menu.applyAndSave(null, path, value)));
    }

    @Override
    public int applyPreset(String name) {
        Integer updated = onSettingsMenu((ReactSettingsMenu menu) -> menu.applyPresetHeadless(name));
        return updated == null ? -1 : updated;
    }

    /** Runs a settings write on the server thread, as Bukkit does; null when the menu is gone or the server did not answer. */
    private <T> T onSettingsMenu(Function<ReactSettingsMenu, T> write) {
        Optional<ModSettings> settings = ModSettings.current();
        if (settings.isEmpty()) {
            return null;
        }
        ReactSettingsMenu menu = settings.get().menu();
        if (server.isSameThread()) {
            return write.apply(menu);
        }
        Supplier<T> task = () -> write.apply(menu);
        try {
            return server.submit(task).get(CONFIG_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException failure) {
            ReactPlatforms.get().warn("React Web config write did not finish on the server thread", failure);
            return null;
        }
    }
}
