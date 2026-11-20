package art.arcane.react.content.directorcommand;

import art.arcane.react.api.action.Action;
import art.arcane.react.content.action.ActionCaptureProfile;
import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.project.config.ConfigLocalization;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.Comparator;

/**
 * The {@code /react action} commands that need nothing from the world.
 */
final class ActionCommands {
  private ActionCommands() {
  }

  static void captureProfile(VolmitSender sender, int seconds) {
    Action<ActionCaptureProfile.Params> action = ReactComponents.action(ActionCaptureProfile.ID);
    if (action == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }
    action.create(action.getDefaultParams().setSeconds(seconds), sender).queue();
  }

  static void collectGarbage(VolmitSender sender) {
    Action<ActionCollectGarbage.Params> action = ReactComponents.action(ActionCollectGarbage.ID);
    if (action == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }
    action.create(action.getDefaultParams(), sender).queue();
  }

  static void audit(VolmitSender sender) {
    ActionController controller = ReactComponents.actions();
    if (controller == null || controller.getActions() == null) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_CONTROLLER_UNAVAILABLE);
      return;
    }

    ReactLanguage.sendPrefixed(sender, CommandMessages.ACTION_AUDIT_HEADER);
    controller.getActions().all().stream()
        .sorted(Comparator.comparing(Action::getId))
        .forEach(action -> {
          String localizedDescription = ConfigLocalization.description(action.getClass());
          String summary = localizedDescription.isBlank()
              ? ReactLanguage.plain(CommandMessages.ACTION_NO_DESCRIPTION)
              : localizedDescription;
          String state = ReactLanguage.plain(
              action.isEnabled() ? CommandMessages.STATE_ENABLED : CommandMessages.STATE_DISABLED
          );
          ReactLanguage.send(
              sender,
              CommandMessages.ACTION_AUDIT_ENTRY,
              MessageArgument.untrusted("id", action.getId()),
              MessageArgument.untrusted("state", state),
              MessageArgument.untrusted("summary", summary)
          );
        });
  }
}
