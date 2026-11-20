package art.arcane.react.platform;

/**
 * A registration with a platform host, such as a scheduled tick or an event listener. Cancelling twice is harmless.
 */
@FunctionalInterface
public interface Subscription {
  Subscription NONE = () -> {
  };

  void cancel();
}
