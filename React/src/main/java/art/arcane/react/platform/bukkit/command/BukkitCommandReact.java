package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.PlayerController;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.core.gui.ReactMapGUI;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.command.CommandSender;

public final class BukkitCommandReact {

  public void monitor(VolmitSender sender) {
    React.controller(PlayerController.class).getPlayer(BukkitCommandSender.player(sender)).toggleActionBar();
    boolean enabled = React.controller(PlayerController.class).getPlayer(BukkitCommandSender.player(sender)).isActionBarMonitoring();
    ReactLanguage.sendPrefixed(sender, enabled ? RuntimeMessages.MONITOR_ENABLED : RuntimeMessages.MONITOR_DISABLED);
  }
  public void map(VolmitSender sender, String rendererId) {
    MapController maps = React.controller(MapController.class);
    ReactRenderer renderer = rendererId == null || maps == null || maps.getRenderers() == null ? null
        : maps.getRenderers().values().stream().filter(r -> r.getId().equalsIgnoreCase(rendererId)).findFirst().orElse(null);
    if (renderer == null || "unknown".equalsIgnoreCase(renderer.getId())) {
      ReactMapGUI.open(BukkitCommandSender.player(sender));
      return;
    }

    React.controller(MapController.class).openRenderer(BukkitCommandSender.player(sender), renderer);
  }

  public void monitoringOnly(VolmitSender sender) {
    CommandSender commandSender = BukkitCommandSender.bukkit(sender);
    Runnable toggle = () -> toggleMonitoringOnly(commandSender);

    if (J.isFoliaThreading()) {
      if (!FoliaScheduler.runGlobal(React.instance, toggle)) {
        ReactLanguage.sendPrefixed(commandSender, CommandMessages.MONITORING_ONLY_SCHEDULE_FAILED);
      }
      return;
    }

    toggle.run();
  }

  private void toggleMonitoringOnly(CommandSender commandSender) {
    FeatureController featureController = React.controller(FeatureController.class);
    TweakController tweakController = React.controller(TweakController.class);
    if (featureController == null || featureController.getFeatures() == null
        || tweakController == null || tweakController.getTweaks() == null) {
      ReactLanguage.sendPrefixed(commandSender, CommandMessages.MONITORING_ONLY_UNAVAILABLE);
      return;
    }

    boolean monitoringOnly = !React.instance.isMonitoringOnly();
    React.instance.setMonitoringOnly(monitoringOnly);
    ReactLanguage.sendPrefixed(
        commandSender,
        monitoringOnly ? CommandMessages.MONITORING_ONLY_ENABLED : CommandMessages.MONITORING_ONLY_DISABLED
    );
  }
}
