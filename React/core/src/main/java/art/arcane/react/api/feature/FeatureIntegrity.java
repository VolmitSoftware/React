package art.arcane.react.api.feature;

/**
 * A feature that keeps watching while it is inactive, so it can undo or verify what it changed. The controller
 * attaches it for the whole run and calls {@link #onIntegrityTick()} every controller tick.
 */
public interface FeatureIntegrity {
  default void onIntegrityTick() {
  }
}
