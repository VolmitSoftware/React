package art.arcane.react.platform.timing;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Measured tick time of each loaded world, published once a window by a platform that times every world's tick.
 */
public final class WorldTickTimes {
  private volatile Map<UUID, World> worlds = Map.of();
  private volatile boolean measuring;

  /**
   * One world's ticks over the last window. {@code key} is the platform's world or dimension name.
   */
  public record World(UUID id, String key, int ticks, double meanMs, double maxMs) {
    public static World measured(UUID id, String key, int ticks, long totalNanos, long maxNanos) {
      int counted = Math.max(0, ticks);
      double meanMs = counted == 0 ? 0D : Math.max(0L, totalNanos) / 1_000_000D / counted;
      return new World(id, key == null ? "" : key, counted, meanMs, Math.max(0L, maxNanos) / 1_000_000D);
    }
  }

  /**
   * Replaces the published worlds. Worlds left out are no longer loaded.
   */
  public void publish(Collection<World> measured) {
    Map<UUID, World> next = new LinkedHashMap<>();
    boolean ticked = measuring;
    for (World world : measured) {
      if (world == null || world.id() == null) {
        continue;
      }
      next.put(world.id(), world);
      ticked |= world.ticks() > 0;
    }
    worlds = Collections.unmodifiableMap(next);
    measuring = ticked;
  }

  /**
   * True once any world has reported a timed tick, so the timers are known to be in place.
   */
  public boolean measuring() {
    return measuring;
  }

  public Map<UUID, World> worlds() {
    return worlds;
  }

  public Optional<World> world(UUID id) {
    return id == null ? Optional.empty() : Optional.ofNullable(worlds.get(id));
  }

  public void clear() {
    worlds = Map.of();
    measuring = false;
  }
}
