package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.core.controller.PluginApiPackController;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime.PackStatus;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.List;
import java.util.Map;

@Director(
    name = "plugin-api",
    aliases = {"packs"},
    origin = DirectorOrigin.BOTH,
    description = "Manage community Plugin API metric packs",
    descriptionKey = "command.description.plugin_api"
)
public final class CommandPluginApi implements DirectorExecutor {
  @Director(
      name = "status",
      aliases = {"list"},
      origin = DirectorOrigin.BOTH,
      description = "Show loaded Plugin API packs and validation errors",
      descriptionKey = "command.description.plugin_api.status"
  )
  public void status() {
    PluginApiPackController controller = React.controller(PluginApiPackController.class);
    if (controller == null) {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.PLUGIN_API_NOT_READY);
      return;
    }
    List<PackStatus> statuses = controller.statuses();
    ReactLanguage.sendPrefixed(
        sender(),
        CommandMessages.PLUGIN_API_HEADER,
        MessageArgument.untrusted("count", statuses.size())
    );
    for (PackStatus status : statuses) {
      ReactLanguage.send(
          sender(),
          CommandMessages.PLUGIN_API_ENTRY,
          MessageArgument.untrusted("id", status.id()),
          MessageArgument.untrusted("state", status.state().name().toLowerCase()),
          MessageArgument.untrusted("target", status.targetPlugin()),
          MessageArgument.untrusted("metrics", status.metrics().size()),
          MessageArgument.untrusted("detail", status.detail())
      );
    }
    Map<String, String> errors = controller.validationErrors();
    if (errors.isEmpty()) {
      return;
    }
    ReactLanguage.send(
        sender(),
        CommandMessages.PLUGIN_API_ERROR_HEADER,
        MessageArgument.untrusted("count", errors.size())
    );
    for (Map.Entry<String, String> entry : errors.entrySet()) {
      ReactLanguage.send(
          sender(),
          CommandMessages.PLUGIN_API_ERROR_ENTRY,
          MessageArgument.untrusted("file", entry.getKey()),
          MessageArgument.untrusted("detail", entry.getValue())
      );
    }
  }

  @Director(
      name = "reload",
      aliases = {"rl"},
      origin = DirectorOrigin.BOTH,
      description = "Rescan the Plugin API pack folder",
      descriptionKey = "command.description.plugin_api.reload"
  )
  public void reload() {
    PluginApiPackController controller = React.controller(PluginApiPackController.class);
    if (controller == null) {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.PLUGIN_API_NOT_READY);
      return;
    }
    controller.requestReload();
    ReactLanguage.sendPrefixed(sender(), CommandMessages.PLUGIN_API_RELOAD_QUEUED);
  }
}
