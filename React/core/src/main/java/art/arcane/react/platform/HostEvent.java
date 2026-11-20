package art.arcane.react.platform;

/**
 * An event a platform host hands to feature listeners. Cancelling stops the game action on every platform.
 */
public interface HostEvent {
  boolean isCancelled();

  void cancel();
}
