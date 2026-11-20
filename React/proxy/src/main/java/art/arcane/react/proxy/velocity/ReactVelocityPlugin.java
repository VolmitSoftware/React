package art.arcane.react.proxy.velocity;

import art.arcane.react.proxy.ProxyLibraries;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Velocity entry point. Loads React's libraries, then runs React for the proxy and every server behind it. */
public final class ReactVelocityPlugin {
  private static final Logger LOGGER = Logger.getLogger("React");
  private final ProxyServer proxy;
  private final Path dataDirectory;
  private VelocityReact react;

  @Inject
  public ReactVelocityPlugin(ProxyServer proxy, @DataDirectory Path dataDirectory) {
    this.proxy = proxy;
    this.dataDirectory = dataDirectory;
  }

  @Subscribe
  public void onInitialize(ProxyInitializeEvent event) {
    try {
      ProxyLibraries.load(dataDirectory, url -> {
        try {
          proxy.getPluginManager().addToClasspath(this, Path.of(url.toURI()));
        } catch (URISyntaxException invalid) {
          throw new IllegalArgumentException(invalid);
        }
      }, LOGGER);
      react = new VelocityReact(proxy, this, dataDirectory, LOGGER);
      react.start();
    } catch (RuntimeException | LinkageError failure) {
      LOGGER.log(Level.SEVERE, "React could not start on Velocity", failure);
    }
  }

  @Subscribe
  public void onShutdown(ProxyShutdownEvent event) {
    if (react != null) {
      react.stop();
      react = null;
    }
  }
}
