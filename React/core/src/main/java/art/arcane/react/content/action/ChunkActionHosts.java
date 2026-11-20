package art.arcane.react.content.action;

import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.WorldHost;

final class ChunkActionHosts {
  private static final int DEFAULT_SPEED = 128;

  private ChunkActionHosts() {
  }

  static ChunkHost chunks() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? null : platform.chunkHost().orElse(null);
  }

  static WorldHost worlds() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? null : platform.worldHost().orElse(null);
  }

  static EntityHost entities() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? null : platform.entityHost().orElse(null);
  }

  static int actionSpeed() {
    ActionController controller = ReactComponents.actions();
    return controller == null ? DEFAULT_SPEED : controller.getActionSpeedMultiplier();
  }
}
