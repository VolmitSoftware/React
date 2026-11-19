package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.core.controller.PluginApiPackController;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime.PackStatus;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.List;
import java.util.Map;

public final class BukkitCommandPluginApi {
  public void status(VolmitSender sender) {
    PluginApiPackController controller = React.controller(PluginApiPackController.class);
    if (controller == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.PLUGIN_API_NOT_READY);
      return;
    }
    List<PackStatus> statuses = controller.statuses();
    ReactLanguage.sendPrefixed(
        sender,
        CommandMessages.PLUGIN_API_HEADER,
        MessageArgument.untrusted("count", statuses.size())
    );
    for (PackStatus status : statuses) {
      ReactLanguage.send(
          sender,
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
        sender,
        CommandMessages.PLUGIN_API_ERROR_HEADER,
        MessageArgument.untrusted("count", errors.size())
    );
    for (Map.Entry<String, String> entry : errors.entrySet()) {
      ReactLanguage.send(
          sender,
          CommandMessages.PLUGIN_API_ERROR_ENTRY,
          MessageArgument.untrusted("file", entry.getKey()),
          MessageArgument.untrusted("detail", entry.getValue())
      );
    }
  }

  public void reload(VolmitSender sender) {
    PluginApiPackController controller = React.controller(PluginApiPackController.class);
    if (controller == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.PLUGIN_API_NOT_READY);
      return;
    }
    controller.requestReload();
    ReactLanguage.sendPrefixed(sender, CommandMessages.PLUGIN_API_RELOAD_QUEUED);
  }
}
