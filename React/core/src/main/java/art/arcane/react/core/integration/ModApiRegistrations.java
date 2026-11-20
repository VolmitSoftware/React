package art.arcane.react.core.integration;

import art.arcane.react.api.metric.ReactModMetricSource;
import art.arcane.react.api.protect.ReactModProtectionProvider;
import art.arcane.react.api.protect.internal.ModProtectionEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** What mods registered through {@code ReactModApi}; kept across React restarts. */
public final class ModApiRegistrations {
  private static final List<ThirdPartyMetricRegistry.Registration> METRIC_SOURCES = new CopyOnWriteArrayList<>();
  private static final List<ModProtectionEngine.Provider> PROTECTION_PROVIDERS = new CopyOnWriteArrayList<>();

  private ModApiRegistrations() {
  }

  public static boolean addMetricSource(String modId, ReactModMetricSource source) {
    if (source == null || modId == null || modId.isBlank()) {
      return false;
    }
    removeMetricSource(source);
    METRIC_SOURCES.add(new ThirdPartyMetricRegistry.Registration(source, modId.strip()));
    return true;
  }

  public static boolean removeMetricSource(ReactModMetricSource source) {
    List<ThirdPartyMetricRegistry.Registration> matching = new ArrayList<>();
    for (ThirdPartyMetricRegistry.Registration registration : METRIC_SOURCES) {
      if (registration.source() == source) {
        matching.add(registration);
      }
    }
    return METRIC_SOURCES.removeAll(matching);
  }

  public static boolean addProtectionProvider(String modId, ReactModProtectionProvider provider) {
    if (provider == null || modId == null || modId.isBlank()) {
      return false;
    }
    removeProtectionProvider(provider);
    PROTECTION_PROVIDERS.add(new ModProtectionEngine.Provider(provider, modId.strip()));
    return true;
  }

  public static boolean removeProtectionProvider(ReactModProtectionProvider provider) {
    List<ModProtectionEngine.Provider> matching = new ArrayList<>();
    for (ModProtectionEngine.Provider registration : PROTECTION_PROVIDERS) {
      if (registration.provider() == provider) {
        matching.add(registration);
      }
    }
    return PROTECTION_PROVIDERS.removeAll(matching);
  }

  public static List<ThirdPartyMetricRegistry.Registration> metricSources() {
    return List.copyOf(METRIC_SOURCES);
  }

  public static List<ModProtectionEngine.Provider> protectionProviders() {
    return List.copyOf(PROTECTION_PROVIDERS);
  }
}
