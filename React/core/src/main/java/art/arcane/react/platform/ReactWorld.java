package art.arcane.react.platform;

import java.util.UUID;

/**
 * Opaque handle to a loaded world. {@link #handle()} is the platform's own world object and is only meaningful to
 * the platform module that produced it.
 */
public interface ReactWorld {
  UUID id();

  String name();

  Object handle();
}
