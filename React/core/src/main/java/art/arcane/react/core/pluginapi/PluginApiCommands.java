package art.arcane.react.core.pluginapi;

import art.arcane.react.core.pluginapi.PluginApiPackRuntime.PackStatus;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.platform.cost.OwnerNoun;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.List;
import java.util.Map;

/** {@code /react plugin-api status} and {@code reload} on every platform. */
public final class PluginApiCommands {
  private PluginApiCommands() {
  }

  public static void status(VolmitSender sender, PluginApiPacks packs) {
    if (packs == null) {
      ReactLanguage.sendPrefixed(sender, OwnerNoun.pick(CommandMessages.PLUGIN_API_NOT_READY, CommandMessages.MOD_API_NOT_READY));
      return;
    }
    List<PackStatus> statuses = packs.statuses();
    ReactLanguage.sendPrefixed(
        sender,
        OwnerNoun.pick(CommandMessages.PLUGIN_API_HEADER, CommandMessages.MOD_API_HEADER),
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
    Map<String, String> errors = packs.validationErrors();
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

  public static void reload(VolmitSender sender, PluginApiPacks packs) {
    if (packs == null) {
      ReactLanguage.sendPrefixed(sender, OwnerNoun.pick(CommandMessages.PLUGIN_API_NOT_READY, CommandMessages.MOD_API_NOT_READY));
      return;
    }
    packs.requestReload();
    ReactLanguage.sendPrefixed(sender, OwnerNoun.pick(CommandMessages.PLUGIN_API_RELOAD_QUEUED, CommandMessages.MOD_API_RELOAD_QUEUED));
  }
}
