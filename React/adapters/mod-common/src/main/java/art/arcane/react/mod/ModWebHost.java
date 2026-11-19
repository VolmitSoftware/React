package art.arcane.react.mod;

import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.platform.WebHost;
import art.arcane.react.platform.WebMetricsSource;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/** React Web on a mod server: {@code web.toml} from React's folder, live sampler values, and the server's identity. */
final class ModWebHost implements WebHost {
    private final MinecraftServer server;
    private final File dataFolder;
    private final String loader;
    private final ModWebMetricsSource metrics;
    private final Consumer<String> info;

    ModWebHost(MinecraftServer server, File dataFolder, String loader, ModWebMetricsSource metrics, Consumer<String> info) {
        this.server = server;
        this.dataFolder = dataFolder;
        this.loader = loader;
        this.metrics = metrics;
        this.info = info;
    }

    @Override
    public Optional<WebConfiguration> loadConfiguration() throws IOException {
        return Optional.of(ModConfigFiles.load(new File(dataFolder, "web.toml"), WebConfiguration.class, new WebConfiguration(), false,
                message -> info.accept("web.toml: " + message)));
    }

    @Override
    public WebMetricsSource metrics() {
        return metrics;
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
