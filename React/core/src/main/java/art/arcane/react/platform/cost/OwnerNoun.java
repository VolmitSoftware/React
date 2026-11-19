package art.arcane.react.platform.cost;

import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;

/**
 * Picks the plugin or mod variant of owner-facing text for the running platform.
 */
public final class OwnerNoun {
  public static final String MOD = "mod";

  private OwnerNoun() {
  }

  public static boolean modded() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform != null && MOD.equals(platform.ownerNoun());
  }

  public static <T> T pick(T pluginVariant, T modVariant) {
    return modded() ? modVariant : pluginVariant;
  }
}
