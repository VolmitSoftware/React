package art.arcane.react.content.PAPI;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;

import java.util.logging.Logger;

final class ReactPlaceholderInstaller {
  private ReactPlaceholderInstaller() {
  }

  static boolean install(PlaceholderRegistration registration, ReactPlaceholderSource source, Logger logger) {
    return registration.register(() -> new PapiExpansion(source, logger));
  }
}
