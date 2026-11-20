package art.arcane.react.api;

import art.arcane.react.api.metric.ReactModMetricSource;
import art.arcane.react.api.protect.ReactModProtectionProvider;
import art.arcane.react.core.integration.ModApiRegistrations;

/**
 * Where mods register with React, the counterpart of Bukkit's ServicesManager registrations. Registrations may be made
 * before or after React starts and survive a React restart; pass the calling mod's id as {@code modId}. Publish metric
 * values with {@code ReactMetrics.publish} and protect single entities with {@code ReactModProtection}.
 */
public final class ReactModApi {
  private ReactModApi() {
  }

  public static boolean registerMetricSource(String modId, ReactModMetricSource source) {
    return ModApiRegistrations.addMetricSource(modId, source);
  }

  public static boolean unregisterMetricSource(ReactModMetricSource source) {
    return ModApiRegistrations.removeMetricSource(source);
  }

  public static boolean registerProtectionProvider(String modId, ReactModProtectionProvider provider) {
    return ModApiRegistrations.addProtectionProvider(modId, provider);
  }

  public static boolean unregisterProtectionProvider(ReactModProtectionProvider provider) {
    return ModApiRegistrations.removeProtectionProvider(provider);
  }
}
