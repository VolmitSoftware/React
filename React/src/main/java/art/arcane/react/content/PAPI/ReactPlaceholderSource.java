package art.arcane.react.content.PAPI;

import art.arcane.react.content.sampler.SamplerChunks;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.content.sampler.SamplerGroundItems;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerMemoryFree;
import art.arcane.react.content.sampler.SamplerMemoryUsed;
import art.arcane.react.content.sampler.SamplerPerWorldTickTime;
import art.arcane.react.content.sampler.SamplerTickMsP95;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.content.sampler.SamplerTicksPerSecond;
import art.arcane.react.content.sampler.SamplerTopWorldMSPT;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderKeyRegistry;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderSnapshot;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleUnaryOperator;

public final class ReactPlaceholderSource {
  public static final String SAMPLER_GROUP = "sampler";
  public static final String WORLD_MSPT_KEY = "world.mspt";
  public static final long OFFLINE_GRACE_MS = PlayerSnapshotStore.DEFAULT_GRACE_MS;

  private static final double BYTES_PER_MEGABYTE = 1048576.0D;
  private static final double INTEGRAL_LIMIT = 9.007199254740992E15D;
  private static final DoubleUnaryOperator IDENTITY = value -> value;
  private static final DoubleUnaryOperator HEALTH = value -> Math.max(0.0D, Math.min(100.0D, 100.0D - value));
  private static final DoubleUnaryOperator MEGABYTES = value -> value / BYTES_PER_MEGABYTE;

  private static final List<NamedMetric> NAMED = List.of(
      new NamedMetric("tps", SamplerTicksPerSecond.ID, true, IDENTITY),
      new NamedMetric("mspt", SamplerTickTime.ID, true, IDENTITY),
      new NamedMetric("mspt-p95", SamplerTickMsP95.ID, true, IDENTITY),
      new NamedMetric("health", SamplerIncidentScore.ID, true, HEALTH),
      new NamedMetric("top-world-mspt", SamplerTopWorldMSPT.ID, true, IDENTITY),
      new NamedMetric("entities", SamplerEntities.ID, false, IDENTITY),
      new NamedMetric("chunks", SamplerChunks.ID, false, IDENTITY),
      new NamedMetric("ground-items", SamplerGroundItems.ID, false, IDENTITY),
      new NamedMetric("memory.used", SamplerMemoryUsed.ID, false, MEGABYTES),
      new NamedMetric("memory.free", SamplerMemoryFree.ID, false, MEGABYTES)
  );

  private final PlaceholderSnapshot<ReactPlaceholderSnapshot> snapshot = new PlaceholderSnapshot<>();
  private final PlayerSnapshotStore<UUID> playerWorlds = new PlayerSnapshotStore<>();
  private final Set<String> demand = ConcurrentHashMap.newKeySet();
  private final PlaceholderKeyRegistry registry = buildRegistry();

  public PlaceholderKeyRegistry registry() {
    return registry;
  }

  public ReactPlaceholderSnapshot snapshot() {
    return snapshot.get();
  }

  public Set<String> demandedSamplerIds() {
    return Set.copyOf(demand);
  }

  public void trackPlayerWorld(UUID playerId, UUID worldId) {
    playerWorlds.publish(playerId, worldId);
  }

  public void releasePlayer(UUID playerId) {
    playerWorlds.evictAfterGrace(playerId, OFFLINE_GRACE_MS);
  }

  public void clear() {
    playerWorlds.clear();
    demand.clear();
    snapshot.publish(null);
  }

  public void publish(SamplerReader samplers, Map<UUID, Double> worldMeanMs) {
    Set<String> reported = samplers == null ? null : samplers.ids();
    Set<String> knownIds = reported == null ? Set.of() : Set.copyOf(reported);
    demand.retainAll(knownIds);

    Map<String, Double> raw = new HashMap<>();
    Map<String, String> generic = new HashMap<>();

    for (String id : demand) {
      double value = samplers.sample(id);
      raw.put(id, value);
      generic.put(id, auto(value));
    }

    Map<String, String> named = new HashMap<>();

    for (NamedMetric metric : NAMED) {
      Double value = raw.get(metric.samplerId());

      if (value != null) {
        named.put(metric.key(), metric.render(value));
      }
    }

    Map<UUID, String> worlds = new HashMap<>();

    if (worldMeanMs != null) {
      for (Map.Entry<UUID, Double> entry : worldMeanMs.entrySet()) {
        if (entry.getKey() != null && entry.getValue() != null) {
          worlds.put(entry.getKey(), PlaceholderValues.num(entry.getValue()));
        }
      }
    }

    snapshot.publish(new ReactPlaceholderSnapshot(named, generic, knownIds, worlds));
  }

  private PlaceholderKeyRegistry buildRegistry() {
    PlaceholderKeyRegistry.Builder builder = PlaceholderKeyRegistry.builder();
    builder.key(PlaceholderKeyRegistry.AVAILABLE, playerId -> snapshot.available());

    for (NamedMetric metric : NAMED) {
      builder.key(metric.key(), playerId -> resolveNamed(metric));
    }

    builder.key(WORLD_MSPT_KEY, this::resolveWorldMspt);
    builder.group(SAMPLER_GROUP, (playerId, tail) -> resolveSampler(tail));
    return builder.build();
  }

  private String resolveNamed(NamedMetric metric) {
    demand(metric.samplerId());
    ReactPlaceholderSnapshot current = snapshot.get();

    if (current == null) {
      return PlaceholderValues.UNAVAILABLE;
    }

    String value = current.named().get(metric.key());
    return value == null ? PlaceholderValues.UNAVAILABLE : value;
  }

  private String resolveSampler(String samplerId) {
    ReactPlaceholderSnapshot current = snapshot.get();

    if (current == null) {
      return PlaceholderValues.UNAVAILABLE;
    }

    if (!current.samplerIds().contains(samplerId)) {
      return null;
    }

    demand(samplerId);
    String value = current.samplers().get(samplerId);
    return value == null ? PlaceholderValues.UNAVAILABLE : value;
  }

  private String resolveWorldMspt(UUID playerId) {
    demand(SamplerPerWorldTickTime.ID);

    if (playerId == null) {
      return PlaceholderValues.UNAVAILABLE;
    }

    UUID worldId = playerWorlds.get(playerId);

    if (worldId == null) {
      return PlaceholderValues.UNAVAILABLE;
    }

    ReactPlaceholderSnapshot current = snapshot.get();

    if (current == null) {
      return PlaceholderValues.UNAVAILABLE;
    }

    String value = current.worldMspt().get(worldId);
    return value == null ? PlaceholderValues.UNAVAILABLE : value;
  }

  private void demand(String samplerId) {
    if (!demand.contains(samplerId)) {
      demand.add(samplerId);
    }
  }

  private static String auto(double value) {
    if (!Double.isFinite(value)) {
      return PlaceholderValues.UNAVAILABLE;
    }

    if (value == Math.rint(value) && Math.abs(value) < INTEGRAL_LIMIT) {
      return PlaceholderValues.count((long) value);
    }

    return PlaceholderValues.num(value);
  }

  public interface SamplerReader {
    Set<String> ids();

    double sample(String samplerId);
  }

  private record NamedMetric(String key, String samplerId, boolean fractional, DoubleUnaryOperator transform) {
    private String render(double raw) {
      double value = transform.applyAsDouble(raw);

      if (!Double.isFinite(value)) {
        return PlaceholderValues.UNAVAILABLE;
      }

      return fractional ? PlaceholderValues.num(value) : PlaceholderValues.count(Math.round(value));
    }
  }
}
