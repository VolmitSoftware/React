package art.arcane.react.util.director;

import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.platform.CommandHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;

import java.util.Optional;

/**
 * The bound platform's command hosts, or the defaults that answer "unavailable" when nothing is bound.
 */
public final class DirectorHosts {
  private DirectorHosts() {
  }

  public static CommandHost command() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? CommandHost.NONE : platform.commandHost().orElse(CommandHost.NONE);
  }

  public static Optional<ReactWebServer> webServer() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? Optional.empty() : platform.webServer();
  }
}
