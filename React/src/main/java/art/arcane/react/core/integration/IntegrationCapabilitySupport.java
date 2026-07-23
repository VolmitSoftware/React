package art.arcane.react.core.integration;

import art.arcane.react.core.controller.IntegrationController;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.List;
import java.util.Locale;

public final class IntegrationCapabilitySupport {
  private static final List<String> INTEGRATION_PLUGIN_IDS = List.of(
      "iris", "adapt", "wormholes", "holoui", "hiddenore", "biletools"
  );

  private IntegrationCapabilitySupport() {
  }

  public static String integrationPluginFor(String normalizedRendererId) {
    if (normalizedRendererId == null || normalizedRendererId.isBlank()) {
      return null;
    }

    for (String pluginId : INTEGRATION_PLUGIN_IDS) {
      if (normalizedRendererId.startsWith(pluginId + "-")) {
        return pluginId;
      }
    }

    return null;
  }

  public static boolean isCapabilityPresent(IntegrationController controller, String capability) {
    return hasCapability(controller, capability) || isPluginInstalled(capability);
  }

  public static boolean hasCapability(IntegrationController controller, String capability) {
    String normalized = normalize(capability);
    if (normalized.isBlank()) {
      return false;
    }

    if (controller != null) {
      IntegrationController.IntegrationStatus status = controller.statusFor(normalized);
      return status != null && status.health() == IntegrationController.Health.HEALTHY;
    }

    return isPluginInstalled(normalized);
  }

  public static boolean isPluginInstalled(String capability) {
    String normalized = normalize(capability);
    if (normalized.isBlank()) {
      return false;
    }

    PluginManager pluginManager = Bukkit.getPluginManager();
    if (pluginManager == null) {
      return false;
    }

    String expectedName = switch (normalized) {
      case "iris" -> "Iris";
      case "adapt" -> "Adapt";
      default -> normalized;
    };

    Plugin direct = pluginManager.getPlugin(expectedName);
    if (direct != null && direct.isEnabled()) {
      return true;
    }

    for (Plugin plugin : pluginManager.getPlugins()) {
      if (plugin == null || !plugin.isEnabled()) {
        continue;
      }
      if (plugin.getName().equalsIgnoreCase(expectedName) || plugin.getName().equalsIgnoreCase(normalized)) {
        return true;
      }
    }

    return false;
  }

  public static String normalize(String capability) {
    return capability == null ? "" : capability.trim().toLowerCase(Locale.ROOT);
  }
}
