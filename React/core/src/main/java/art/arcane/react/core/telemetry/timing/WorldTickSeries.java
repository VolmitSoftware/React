package art.arcane.react.core.telemetry.timing;

import art.arcane.react.core.controller.SampleController;
import art.arcane.react.platform.timing.WorldTickTimes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps one {@code world-tick-<world>} sampler registered for every world in a {@link WorldTickTimes}, so each
 * world's measured tick time gets its own history graph and map. Call {@link #sync} after each publish.
 */
public final class WorldTickSeries {
  public static final String PREFIX = "world-tick-";
  private final WorldTickTimes times;
  private final Map<UUID, String> registered = new HashMap<>();

  public WorldTickSeries(WorldTickTimes times) {
    this.times = times;
  }

  public static String samplerId(String worldKey) {
    String base = (worldKey == null ? "" : worldKey).trim().toLowerCase(Locale.ROOT);
    return PREFIX + base.replaceAll("[^a-z0-9_-]+", "-");
  }

  public static boolean isWorldTickId(String id) {
    return id != null && id.startsWith(PREFIX);
  }

  public void sync(SampleController controller) {
    if (controller == null) {
      return;
    }
    Map<UUID, WorldTickTimes.World> worlds = times.worlds();
    for (WorldTickTimes.World world : worlds.values()) {
      if (registered.containsKey(world.id())) {
        continue;
      }
      String id = samplerId(world.key());
      if (controller.registerSampler(new WorldTickSampler(id, world.id(), world.key(), times))) {
        registered.put(world.id(), id);
      }
    }
    Iterator<Map.Entry<UUID, String>> entries = registered.entrySet().iterator();
    while (entries.hasNext()) {
      Map.Entry<UUID, String> entry = entries.next();
      if (!worlds.containsKey(entry.getKey())) {
        controller.unregisterSampler(entry.getValue());
        entries.remove();
      }
    }
  }

  public void clear(SampleController controller) {
    if (controller != null) {
      for (String id : registered.values()) {
        controller.unregisterSampler(id);
      }
    }
    registered.clear();
  }
}
