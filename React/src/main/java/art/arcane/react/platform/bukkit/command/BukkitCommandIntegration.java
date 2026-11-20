package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.content.directorcommand.IntegrationCommands;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.plugin.VolmitSender;

import java.util.ArrayList;
import java.util.List;

public final class BukkitCommandIntegration {
  public void status(VolmitSender sender) {
    IntegrationController controller = React.controller(IntegrationController.class);
    if (controller == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.INTEGRATION_NOT_READY);
      return;
    }

    List<IntegrationCommands.Entry> entries = new ArrayList<>();
    for (IntegrationController.IntegrationStatus status : controller.statuses()) {
      entries.add(new IntegrationCommands.Entry(status.pluginId(), status.health().name().toLowerCase(),
          status.protocol(), status.lastHeartbeatMs(), status.message()));
    }
    String correlation = ReactConfiguration.get().isVerbose() ? controller.getLastCorrelationMessage() : null;
    IntegrationCommands.status(sender, entries, correlation, controller.recentTimeline(4), System.currentTimeMillis());
  }
}
