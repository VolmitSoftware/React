package art.arcane.react.platform;

import java.util.Locale;
import java.util.UUID;

/**
 * Opaque handle to an online player. {@link #handle()} is the platform's own player object and is only meaningful
 * to the platform module that produced it.
 */
public interface ReactPlayer {
  UUID id();

  String name();

  Locale locale();

  boolean hasPermission(String node);

  void sendMessage(String message);

  Object handle();
}
