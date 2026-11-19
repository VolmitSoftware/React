package art.arcane.react.platform;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Game events that samplers count. The platform reports each event once through {@link #count};
 * samplers subscribe while they run. A platform only needs to listen for a counter while
 * {@link #isActive} is true and is told about changes through the activation observer.
 */
public final class SamplerEventCounters {
  private static final SamplerEventCounters INSTANCE = new SamplerEventCounters();
  private static final CounterListener[] NONE = new CounterListener[0];

  private final Map<Counter, CounterListener[]> listeners = new EnumMap<>(Counter.class);
  private volatile CounterListener[][] dispatch = emptyDispatch();
  private volatile BiConsumer<Counter, Boolean> activationObserver;

  private SamplerEventCounters() {
  }

  public static SamplerEventCounters get() {
    return INSTANCE;
  }

  /**
   * Subject passed with each counter:
   * <ul>
   *   <li>SERVER_TICK: null, once per server tick after the tick ran.</li>
   *   <li>CHUNK_LOAD, CHUNK_GENERATE (new chunks only), CHUNK_UNLOAD: the native chunk.</li>
   *   <li>CHUNK_LOAD_BEGIN, CHUNK_GENERATE_BEGIN: the native chunk, before any other listener runs.</li>
   *   <li>CHUNK_LOAD_END, CHUNK_GENERATE_END: the same chunk object, after every other listener ran.</li>
   *   <li>WORLD_UNLOAD: the native world.</li>
   *   <li>WORLD_SAVE: the saved world's UUID.</li>
   *   <li>BLOCK_PHYSICS, REDSTONE: the native block.</li>
   *   <li>PISTON_MOVE: the piston block, amount = moved blocks + 2.</li>
   *   <li>FLUID_FLOW: the source block, only when the source is a levelled fluid.</li>
   *   <li>FLUID_EVENT: the source block, when the source or target is a levelled fluid.</li>
   *   <li>HOPPER_MOVE: the hopper block that moved an item.</li>
   *   <li>ENTITY_SPAWN: the native entity, cancelled spawns included.</li>
   *   <li>SPAWNER_SPAWN: null, spawner and trial spawner spawns that were not cancelled.</li>
   *   <li>COMMAND: null, player, console and remote console commands that were not cancelled.</li>
   * </ul>
   */
  public enum Counter {
    SERVER_TICK,
    CHUNK_LOAD,
    CHUNK_GENERATE,
    CHUNK_UNLOAD,
    CHUNK_LOAD_BEGIN,
    CHUNK_LOAD_END,
    CHUNK_GENERATE_BEGIN,
    CHUNK_GENERATE_END,
    WORLD_UNLOAD,
    WORLD_SAVE,
    BLOCK_PHYSICS,
    PISTON_MOVE,
    REDSTONE,
    FLUID_FLOW,
    FLUID_EVENT,
    HOPPER_MOVE,
    ENTITY_SPAWN,
    SPAWNER_SPAWN,
    COMMAND
  }

  @FunctionalInterface
  public interface CounterListener {
    void count(Object subject, int amount);
  }

  public void count(Counter counter, Object subject, int amount) {
    CounterListener[] targets = dispatch[counter.ordinal()];
    for (CounterListener target : targets) {
      target.count(subject, amount);
    }
  }

  public void count(Counter counter, Object subject) {
    count(counter, subject, 1);
  }

  public boolean isActive(Counter counter) {
    return dispatch[counter.ordinal()].length > 0;
  }

  public void setActivationObserver(BiConsumer<Counter, Boolean> observer) {
    activationObserver = observer;
  }

  public void subscribe(Counter counter, CounterListener listener) {
    boolean activated;
    synchronized (listeners) {
      CounterListener[] current = listeners.getOrDefault(counter, NONE);
      for (CounterListener existing : current) {
        if (existing == listener) {
          return;
        }
      }
      CounterListener[] next = Arrays.copyOf(current, current.length + 1);
      next[current.length] = listener;
      listeners.put(counter, next);
      publish();
      activated = current.length == 0;
    }
    if (activated) {
      notifyActivation(counter, true);
    }
  }

  public void unsubscribe(Counter counter, CounterListener listener) {
    boolean deactivated;
    synchronized (listeners) {
      CounterListener[] current = listeners.getOrDefault(counter, NONE);
      int index = -1;
      for (int i = 0; i < current.length; i++) {
        if (current[i] == listener) {
          index = i;
          break;
        }
      }
      if (index < 0) {
        return;
      }
      CounterListener[] next = new CounterListener[current.length - 1];
      System.arraycopy(current, 0, next, 0, index);
      System.arraycopy(current, index + 1, next, index, current.length - index - 1);
      if (next.length == 0) {
        listeners.remove(counter);
      } else {
        listeners.put(counter, next);
      }
      publish();
      deactivated = next.length == 0;
    }
    if (deactivated) {
      notifyActivation(counter, false);
    }
  }

  private void publish() {
    CounterListener[][] next = emptyDispatch();
    for (Map.Entry<Counter, CounterListener[]> entry : listeners.entrySet()) {
      next[entry.getKey().ordinal()] = entry.getValue();
    }
    dispatch = next;
  }

  private void notifyActivation(Counter counter, boolean active) {
    BiConsumer<Counter, Boolean> observer = activationObserver;
    if (observer != null) {
      observer.accept(counter, active);
    }
  }

  private static CounterListener[][] emptyDispatch() {
    CounterListener[][] empty = new CounterListener[Counter.values().length][];
    Arrays.fill(empty, NONE);
    return empty;
  }
}
