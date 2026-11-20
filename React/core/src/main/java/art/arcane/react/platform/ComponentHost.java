package art.arcane.react.platform;

/**
 * What features, tweaks and actions need from the server around them: a component ticker, platform listener
 * registration and the gates that decide whether a component may run.
 */
public interface ComponentHost {
  /**
   * Calls {@code tick} every {@code intervalMs} off the server thread, never overlapping itself. Work that touches
   * the world hops back with the scheduler, as it does on Bukkit's React ticker. {@code owner} is the feature, tweak
   * or controller being ticked, for the platform's slow-tick diagnostics.
   */
  Subscription schedule(Object owner, String id, long intervalMs, Runnable tick);

  /**
   * Registers the component's own platform listeners, such as a Bukkit {@code Listener}. No-op where components only
   * use React's hosts.
   */
  default void attach(Object component) {
  }

  default void detach(Object component) {
  }

  /**
   * True while React is enabled, including while it is still starting.
   */
  boolean isEnabled();

  /**
   * True while React is enabled and finished starting.
   */
  boolean isRunning();

  boolean isMonitoringOnly();

  default boolean integrationSecretsEnabled() {
    return false;
  }

  /**
   * True when the named integration currently publishes the capability a gated feature needs.
   */
  default boolean hasCapability(String capability) {
    return false;
  }

  /**
   * True when the named integration is installed at all, so its gated features are worth registering.
   */
  default boolean isIntegrationInstalled(String capability) {
    return false;
  }

  /**
   * Sets one config value of a feature, tweak or action, such as {@code feature.entity-trimmer.enabled}, saves its
   * file and reloads it. False when the path is unknown, the value does not fit, or the reload failed.
   */
  boolean applyConfigValue(String path, Object value);

  /**
   * The per-tick compute budget React's job queue keeps to, in milliseconds.
   */
  default double maxComputeTimeMs() {
    return 1D;
  }
}
