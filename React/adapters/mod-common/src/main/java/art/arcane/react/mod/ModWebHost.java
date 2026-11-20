package art.arcane.react.mod;

import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapChunkSampler;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.platform.WebHost;
import art.arcane.react.platform.WebMetricsSource;
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
        return Optional.of(ModConfigFiles.load(new File(dataFolder, "web.toml"), WebConfiguration.class, new WebConfiguration(), "web-config", false,
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
    public HeatmapChunkSampler heatmapSampler() {
        return (ChunkGridExporter exporter, String world, Integer centerX, Integer centerZ, int radius) -> maps.heatmapSampler()
                .map((HeatmapChunkSampler sampler) -> sampler.scan(exporter, world, centerX, centerZ, radius))
                .orElse(null);
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
}
