package art.arcane.react.core.integration;

import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteSamplerBridge {
  private static final long MAX_FUTURE_SAMPLE_MS = 5_000L;
  private static final long MAX_STALE_SAMPLE_MS = 15_000L;

  private final Map<String, Map<String, IntegrationMetricSample>> samplesByPlugin = new ConcurrentHashMap<>();
  private final Map<String, Map<GroupKey, IntegrationMetricGroup>> groupsByPlugin = new ConcurrentHashMap<>();

  public void updatePluginSamples(
      String pluginId,
      Set<String> expectedKeys,
      Map<String, IntegrationMetricSample> samples,
      String unavailableReason
  ) {
    String normalizedPlugin = normalizePlugin(pluginId);
    Map<String, IntegrationMetricSample> pluginSamples = samplesByPlugin.computeIfAbsent(
        normalizedPlugin,
        ignored -> new ConcurrentHashMap<>()
    );
    long now = System.currentTimeMillis();
    Set<String> safeExpectedKeys = expectedKeys == null ? Set.of() : expectedKeys;
    Map<String, IntegrationMetricSample> safeSamples = samples == null ? Map.of() : samples;
    String reason = unavailableReason == null || unavailableReason.isBlank() ? "unavailable" : unavailableReason;

    pluginSamples.keySet().removeIf(key -> key.startsWith(normalizedPlugin + ".")
        && !safeExpectedKeys.contains(key)
        && !safeSamples.containsKey(key));

    for (String key : safeExpectedKeys) {
      IntegrationMetricSample supplied = safeSamples.get(key);
      IntegrationMetricSample sample = validatedSample(normalizedPlugin, key, supplied, now);
      if (sample != null) {
        pluginSamples.put(key, sample);
        continue;
      }

      IntegrationMetricDescriptor descriptor = descriptorForMissing(pluginSamples.get(key), key);
      pluginSamples.put(key, IntegrationMetricSample.unavailable(
          descriptor,
          supplied == null ? reason : "invalid-sample",
          now
      ));
    }

    for (Map.Entry<String, IntegrationMetricSample> entry : safeSamples.entrySet()) {
      String key = entry.getKey();
      IntegrationMetricSample sample = validatedSample(normalizedPlugin, key, entry.getValue(), now);
      if (sample != null) {
        pluginSamples.put(key, sample);
      }
    }
  }

  public void updatePluginGroups(String pluginId, List<IntegrationMetricGroup> groups) {
    String normalizedPlugin = normalizePlugin(pluginId);
    Map<GroupKey, IntegrationMetricGroup> next = new LinkedHashMap<>();
    if (groups != null) {
      for (IntegrationMetricGroup group : groups) {
        if (!validGroup(normalizedPlugin, group)) {
          continue;
        }
        next.put(new GroupKey(group.scopeKind(), group.scopeId()), group);
      }
    }
    groupsByPlugin.put(normalizedPlugin, Map.copyOf(next));
  }

  public void markPluginUnavailable(String pluginId, Set<String> keys, String reason) {
    String normalizedPlugin = normalizePlugin(pluginId);
    Set<String> unavailableKeys = ConcurrentHashMap.newKeySet();
    if (keys != null) {
      unavailableKeys.addAll(keys);
    }
    Map<String, IntegrationMetricSample> existing = samplesByPlugin.get(normalizedPlugin);
    if (existing != null) {
      unavailableKeys.addAll(existing.keySet());
    }
    updatePluginSamples(pluginId, unavailableKeys, Map.of(), reason);
    groupsByPlugin.remove(normalizedPlugin);
  }

  public IntegrationMetricSample getSample(String pluginId, String key) {
    String normalizedPlugin = normalizePlugin(pluginId);
    Map<String, IntegrationMetricSample> pluginSamples = samplesByPlugin.get(normalizedPlugin);
    if (pluginSamples == null) {
      return IntegrationMetricSample.unavailable(
          IntegrationMetricSchema.descriptor(key),
          "plugin-not-discovered",
          System.currentTimeMillis()
      );
    }

    IntegrationMetricSample sample = pluginSamples.get(key);
    if (sample != null) {
      return sample;
    }

    return IntegrationMetricSample.unavailable(
        IntegrationMetricSchema.descriptor(key),
        "metric-not-published",
        System.currentTimeMillis()
    );
  }

  public IntegrationMetricGroup getGroup(String pluginId, String scopeKind, String scopeId) {
    Map<GroupKey, IntegrationMetricGroup> groups = groupsByPlugin.get(normalizePlugin(pluginId));
    if (groups == null) {
      return null;
    }
    return groups.get(new GroupKey(scopeKind, scopeId));
  }

  public List<IntegrationMetricGroup> groups(String pluginId, String scopeKind) {
    Map<GroupKey, IntegrationMetricGroup> groups = groupsByPlugin.get(normalizePlugin(pluginId));
    if (groups == null || groups.isEmpty()) {
      return List.of();
    }
    String normalizedKind = normalize(scopeKind);
    return groups.values().stream()
        .filter(group -> normalize(group.scopeKind()).equals(normalizedKind))
        .sorted(java.util.Comparator.comparing(IntegrationMetricGroup::scopeId))
        .toList();
  }

  public double valueOr(String pluginId, String key, double fallback) {
    return getSample(pluginId, key).valueOr(fallback);
  }

  public boolean isAvailable(String pluginId, String key) {
    return getSample(pluginId, key).available();
  }

  public Map<String, IntegrationMetricSample> snapshot(String pluginId) {
    String normalizedPlugin = normalizePlugin(pluginId);
    Map<String, IntegrationMetricSample> samples = samplesByPlugin.get(normalizedPlugin);
    if (samples == null) {
      return Map.of();
    }
    return new HashMap<>(samples);
  }

  public void clear() {
    samplesByPlugin.clear();
    groupsByPlugin.clear();
  }

  private IntegrationMetricSample validatedSample(
      String pluginId,
      String mapKey,
      IntegrationMetricSample sample,
      long now
  ) {
    if (mapKey == null || mapKey.isBlank() || sample == null || sample.descriptor() == null) {
      return null;
    }
    if (!mapKey.equals(sample.descriptor().key()) || !mapKey.startsWith(pluginId + ".")) {
      return null;
    }
    String descriptorPlugin = normalize(sample.descriptor().tags().get("plugin"));
    if (!descriptorPlugin.isBlank() && !pluginId.equals(descriptorPlugin)) {
      return null;
    }
    if (sample.sampledAtMs() > now + MAX_FUTURE_SAMPLE_MS) {
      return null;
    }
    if (sample.sampledAtMs() <= 0L || now - sample.sampledAtMs() > MAX_STALE_SAMPLE_MS) {
      return null;
    }
    if (sample.available() && sample.numericValue() == null) {
      return null;
    }
    IntegrationMetricDescriptor schemaDescriptor = IntegrationMetricSchema.descriptor(mapKey);
    boolean knownSchema = !"unknown".equals(schemaDescriptor.tags().get("origin"));
    if (knownSchema
        && (sample.descriptor().type() != schemaDescriptor.type()
        || !sample.descriptor().unit().equals(schemaDescriptor.unit()))) {
      return null;
    }
    return sample;
  }

  private boolean validGroup(String pluginId, IntegrationMetricGroup group) {
    if (group == null || group.scopeKind().isBlank() || group.scopeId().isBlank()) {
      return false;
    }
    String taggedPlugin = normalize(group.tags().get("plugin"));
    if (!taggedPlugin.isBlank() && !pluginId.equals(taggedPlugin)) {
      return false;
    }
    for (Map.Entry<String, IntegrationMetricSample> entry : group.samples().entrySet()) {
      if (validatedSample(pluginId, entry.getKey(), entry.getValue(), System.currentTimeMillis()) == null) {
        return false;
      }
    }
    return true;
  }

  private IntegrationMetricDescriptor descriptorForMissing(IntegrationMetricSample existing, String key) {
    if (existing != null && existing.descriptor() != null && key.equals(existing.descriptor().key())) {
      return existing.descriptor();
    }
    return IntegrationMetricSchema.descriptor(key);
  }

  private String normalizePlugin(String pluginId) {
    return normalize(pluginId);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private record GroupKey(String scopeKind, String scopeId) {
    private GroupKey {
      scopeKind = scopeKind == null ? "" : scopeKind.trim().toLowerCase(Locale.ROOT);
      scopeId = scopeId == null ? "" : scopeId.trim();
    }
  }
}
