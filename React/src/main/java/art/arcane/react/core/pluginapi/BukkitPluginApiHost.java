package art.arcane.react.core.pluginapi;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.registry.ItemIcons;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.util.List;

public final class BukkitPluginApiHost implements PluginApiHost {
  private static final List<String> EXAMPLES = List.of(
      "adapt-ability-ops.toml",
      "placeholderapi-server.toml",
      "oraxen-runtime.toml"
  );

  @Override
  public String folderName() {
    return "plugin-apis";
  }

  @Override
  public String label() {
    return "Plugin API";
  }

  @Override
  public String targetMissingDetail() {
    return "target-plugin-not-enabled";
  }

  @Override
  public List<String> bundledExamples() {
    return EXAMPLES;
  }

  @Override
  public InputStream bundledExample(String name) {
    return React.instance.getResource("plugin-apis/examples/" + name);
  }

  @Override
  public String icon(String raw) {
    Material material = Material.matchMaterial(raw);
    return material == null ? null : ItemIcons.id(material);
  }

  @Override
  public Target target(String id) {
    Plugin plugin = Bukkit.getPluginManager().getPlugin(id);
    if (plugin == null || !plugin.isEnabled()) {
      return null;
    }
    return new Target(plugin.getDescription().getVersion(), plugin.getClass().getClassLoader());
  }

  @Override
  public double integration(String integrationId, String key) {
    IntegrationController controller = React.controller(IntegrationController.class);
    RemoteSamplerBridge bridge = controller == null ? null : controller.getRemoteSamplerBridge();
    if (bridge == null || !bridge.isAvailable(integrationId, key)) {
      return Double.NaN;
    }
    return bridge.valueOr(integrationId, key, Double.NaN);
  }

  @Override
  public double placeholder(String placeholder, boolean foliaSafe) {
    if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      return Double.NaN;
    }
    if (J.isFoliaThreading() && !foliaSafe) {
      return Double.NaN;
    }
    return PluginApiPlaceholderReader.read(placeholder);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Runnable eventCounter(Target target, String eventClass, Runnable increment) throws ReflectiveOperationException {
    Class<?> rawEventClass = Class.forName(eventClass, false, target.classLoader());
    if (!Event.class.isAssignableFrom(rawEventClass)) {
      throw new IllegalArgumentException("Configured class is not a Bukkit Event");
    }
    Listener listener = new Listener() {
    };
    EventExecutor executor = (ignored, event) -> increment.run();
    Bukkit.getPluginManager().registerEvent(
        (Class<? extends Event>) rawEventClass,
        listener,
        EventPriority.MONITOR,
        executor,
        React.instance,
        true
    );
    return () -> HandlerList.unregisterAll(listener);
  }
}
