package art.arcane.react.proxy;

import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.network.NetworkHub;
import art.arcane.react.api.web.network.NetworkSeal;
import art.arcane.react.platform.WebHost;
import art.arcane.react.platform.WebMetricsSource;
import art.arcane.react.util.project.config.TomlFiles;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * React Web on a proxy: the proxy's own JVM and host metrics, and the network routes for every server behind it. The
 * first start writes a new {@code networkSecret} into web.toml for the backends to copy.
 */
final class ProxyWebHost implements WebHost {
  private static final String SOURCE_TAG = "web-config";
  private final ProxyHost host;
  private final WebMetricsSource metrics;
  private final NetworkHub network;

  ProxyWebHost(ProxyHost host, WebMetricsSource metrics, NetworkHub network) {
    this.host = host;
    this.metrics = metrics;
    this.network = network;
  }

  @Override
  public Optional<WebConfiguration> loadConfiguration() throws IOException {
    File file = new File(host.dataFolder(), "web.toml");
    WebConfiguration configuration = TomlFiles.load(file, WebConfiguration.class, new WebConfiguration(), SOURCE_TAG, false,
        (String message) -> host.logger().info("web.toml: " + message));
    if (configuration.getNetworkSecret() == null || configuration.getNetworkSecret().isBlank()) {
      configuration.setNetworkSecret(NetworkSeal.newSecret());
      TomlFiles.save(file, configuration, SOURCE_TAG);
      host.logger().info("Wrote a new networkSecret to web.toml; run /react network join in this console for what backends need.");
    }
    return Optional.of(configuration);
  }

  @Override
  public WebMetricsSource metrics() {
    return metrics;
  }

  @Override
  public Optional<NetworkHub> network() {
    return Optional.of(network);
  }

  @Override
  public String motd() {
    return host.motd();
  }

  @Override
  public File serverFolder() {
    File plugins = host.dataFolder().getAbsoluteFile().getParentFile();
    return plugins == null || plugins.getParentFile() == null ? host.dataFolder() : plugins.getParentFile();
  }

  @Override
  public String serverAddress() {
    return host.bindAddress();
  }

  @Override
  public Logger consoleLogger() {
    return host.consoleLogger();
  }

  @Override
  public String serverVersion() {
    return host.kind() + " " + host.version();
  }
}
