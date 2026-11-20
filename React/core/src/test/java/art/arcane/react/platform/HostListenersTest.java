package art.arcane.react.platform;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class HostListenersTest {
  @Test
  void runsListenersInPriorityThenSubscriptionOrder() {
    HostListeners<TestEvent> listeners = new HostListeners<>();
    List<String> order = new ArrayList<>();
    listeners.subscribe(ListenerPriority.MONITOR, false, event -> order.add("monitor"));
    listeners.subscribe(ListenerPriority.LOW, false, event -> order.add("low-1"));
    listeners.subscribe(ListenerPriority.LOWEST, false, event -> order.add("lowest"));
    listeners.subscribe(ListenerPriority.LOW, false, event -> order.add("low-2"));

    Assertions.assertTrue(listeners.dispatch(new TestEvent()));
    Assertions.assertEquals(List.of("lowest", "low-1", "low-2", "monitor"), order);
  }

  @Test
  void skipsIgnoreCancelledListenersOnceCancelled() {
    HostListeners<TestEvent> listeners = new HostListeners<>();
    List<String> order = new ArrayList<>();
    listeners.subscribe(ListenerPriority.LOWEST, true, event -> event.cancel());
    listeners.subscribe(ListenerPriority.NORMAL, true, event -> order.add("ignored"));
    listeners.subscribe(ListenerPriority.MONITOR, false, event -> order.add("monitor"));

    Assertions.assertFalse(listeners.dispatch(new TestEvent()));
    Assertions.assertEquals(List.of("monitor"), order);
  }

  @Test
  void cancelledSubscriptionStopsListening() {
    HostListeners<TestEvent> listeners = new HostListeners<>();
    List<String> order = new ArrayList<>();
    Subscription subscription = listeners.subscribe(ListenerPriority.NORMAL, false, event -> order.add("once"));
    listeners.dispatch(new TestEvent());
    subscription.cancel();
    subscription.cancel();
    listeners.dispatch(new TestEvent());

    Assertions.assertEquals(List.of("once"), order);
    Assertions.assertTrue(listeners.isEmpty());
  }

  private static final class TestEvent implements HostEvent {
    private boolean cancelled;

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }
}
