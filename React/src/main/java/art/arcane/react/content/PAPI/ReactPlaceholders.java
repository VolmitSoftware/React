package art.arcane.react.content.PAPI;

import art.arcane.react.React;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;

import java.util.Objects;
import java.util.logging.Logger;

public final class ReactPlaceholders {
  private final Logger logger;
  private final ReactPlaceholderSource source;
  private final PlaceholderRegistration registration;
  private volatile ReactPlaceholderPublisher publisher;

  public ReactPlaceholders(Logger logger) {
    this.logger = Objects.requireNonNull(logger, "logger");
    this.source = new ReactPlaceholderSource();
    this.registration = new PlaceholderRegistration(logger);
  }

  public ReactPlaceholderSource source() {
    return source;
  }

  public boolean isRegistered() {
    return registration.isRegistered();
  }

  public boolean start() {
    if (!PlaceholderRegistration.isPlaceholderApiEnabled()) {
      return false;
    }

    ReactPlaceholderPublisher started = new ReactPlaceholderPublisher(source);
    publisher = started;
    React.instance.registerListener(started);
    started.seedOnlinePlayers();

    if (!ReactPlaceholderInstaller.install(registration, source, logger)) {
      stop();
      return false;
    }

    return true;
  }

  public void stop() {
    registration.unregister();
    ReactPlaceholderPublisher running = publisher;
    publisher = null;

    if (running != null) {
      if (React.instance != null) {
        React.instance.unregisterListener(running);
      }

      running.unregister();
    }

    source.clear();
  }
}
