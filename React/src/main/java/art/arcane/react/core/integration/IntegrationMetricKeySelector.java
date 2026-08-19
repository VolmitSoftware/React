package art.arcane.react.core.integration;

import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationServiceContract;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class IntegrationMetricKeySelector {
  private IntegrationMetricKeySelector() {
  }

  public static Set<String> expectedKeys(String pluginId, IntegrationServiceContract provider) {
    String normalizedPlugin = normalize(pluginId);
    Set<String> keys = new LinkedHashSet<>(fixedKeys(normalizedPlugin));
    if (provider == null || normalizedPlugin.isBlank()) {
      return Set.copyOf(keys);
    }

    Set<IntegrationMetricDescriptor> descriptors = provider.metricDescriptors();
    if (descriptors == null) {
      return Set.copyOf(keys);
    }

    for (IntegrationMetricDescriptor descriptor : descriptors) {
      if (descriptor == null || descriptor.key() == null || !descriptor.key().startsWith(normalizedPlugin + ".")) {
        continue;
      }
      keys.add(descriptor.key());
    }
    return Set.copyOf(keys);
  }

  private static Set<String> fixedKeys(String pluginId) {
    return switch (pluginId) {
      case "iris" -> IntegrationMetricSchema.irisKeys();
      case "adapt" -> IntegrationMetricSchema.adaptKeys();
      case "wormholes" -> IntegrationMetricSchema.wormholesKeys();
      case "gloss" -> IntegrationMetricSchema.glossKeys();
      case "hiddenore" -> IntegrationMetricSchema.hiddenoreKeys();
      case "biletools" -> IntegrationMetricSchema.biletoolsKeys();
      default -> Set.of();
    };
  }

  private static String normalize(String pluginId) {
    return pluginId == null ? "" : pluginId.trim().toLowerCase(Locale.ROOT);
  }
}
