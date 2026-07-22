package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.List;

@Director(
    name = "integration",
    aliases = {"int"},
    origin = DirectorOrigin.BOTH,
    description = "Cross-plugin integration status",
    descriptionKey = "command.description.integration"
)
public class CommandIntegration implements DirectorExecutor {
  @Director(
      name = "status",
      aliases = {"s"},
      origin = DirectorOrigin.BOTH,
      description = "Show Iris, Adapt, and Wormholes integration health",
      descriptionKey = "command.description.integration.status"
  )
  public void status() {
    IntegrationController controller = React.controller(IntegrationController.class);
    if (controller == null) {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.INTEGRATION_NOT_READY);
      return;
    }

    long now = System.currentTimeMillis();
    ReactLanguage.sendPrefixed(sender(), CommandMessages.INTEGRATION_HEADER);
    for (IntegrationController.IntegrationStatus status : controller.statuses()) {
      String heartbeatAge = status.lastHeartbeatMs() <= 0
          ? ReactLanguage.plain(CommandMessages.INTEGRATION_HEARTBEAT_NEVER)
          : ReactLanguage.plain(
              CommandMessages.INTEGRATION_HEARTBEAT_AGE,
              MessageArgument.untrusted("duration", Form.duration(Math.max(0L, now - status.lastHeartbeatMs()), 1))
          );
      ReactLanguage.send(
          sender(),
          CommandMessages.INTEGRATION_ENTRY,
          MessageArgument.untrusted("plugin", status.pluginId()),
          MessageArgument.untrusted("health", status.health().name().toLowerCase()),
          MessageArgument.untrusted("protocol", status.protocol()),
          MessageArgument.untrusted("heartbeat", heartbeatAge),
          MessageArgument.untrusted("detail", status.message())
      );
    }

    if (ReactConfiguration.get().isVerbose() && controller.getLastCorrelationMessage() != null && !controller.getLastCorrelationMessage().isBlank()) {
      ReactLanguage.send(
          sender(),
          CommandMessages.INTEGRATION_CORRELATION,
          MessageArgument.untrusted("detail", controller.getLastCorrelationMessage())
      );
    }

    List<String> timeline = controller.recentTimeline(4);
    if (!timeline.isEmpty()) {
      ReactLanguage.send(sender(), CommandMessages.INTEGRATION_TIMELINE_HEADER);
      timeline.forEach(line -> ReactLanguage.send(
          sender(),
          CommandMessages.INTEGRATION_TIMELINE_ENTRY,
          MessageArgument.untrusted("entry", line)
      ));
    }
  }
}
