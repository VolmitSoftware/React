package art.arcane.react.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Listeners for one host event, run in {@link ListenerPriority} order and then subscription order, the way Bukkit
 * runs event handlers. A listener that asked to ignore cancelled events is skipped once the event is cancelled.
 */
public final class HostListeners<E extends HostEvent> {
  private volatile Entry<E>[] entries = newArray(0);

  public synchronized Subscription subscribe(ListenerPriority priority, boolean ignoreCancelled, Consumer<E> listener) {
    Entry<E> entry = new Entry<>(priority, ignoreCancelled, listener);
    List<Entry<E>> next = new ArrayList<>(List.of(entries));
    int index = 0;
    while (index < next.size() && next.get(index).priority.ordinal() <= priority.ordinal()) {
      index++;
    }
    next.add(index, entry);
    entries = next.toArray(newArray(0));
    return () -> remove(entry);
  }

  private synchronized void remove(Entry<E> entry) {
    List<Entry<E>> next = new ArrayList<>(List.of(entries));
    if (next.remove(entry)) {
      entries = next.toArray(newArray(0));
    }
  }

  public boolean isEmpty() {
    return entries.length == 0;
  }

  public synchronized void clear() {
    entries = newArray(0);
  }

  /**
   * Runs every listener for the event; true when the event ends up not cancelled.
   */
  public boolean dispatch(E event) {
    for (Entry<E> entry : entries) {
      if (entry.ignoreCancelled && event.isCancelled()) {
        continue;
      }
      entry.listener.accept(event);
    }
    return !event.isCancelled();
  }

  @SuppressWarnings("unchecked")
  private static <E extends HostEvent> Entry<E>[] newArray(int size) {
    return (Entry<E>[]) new Entry[size];
  }

  private record Entry<E extends HostEvent>(ListenerPriority priority, boolean ignoreCancelled, Consumer<E> listener) {
  }
}
