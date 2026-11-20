package art.arcane.react.content.directorcommand;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;

/**
 * The reply of {@code /react debug entity-data} for the entity a player looks at.
 */
public final class EntityDataCommands {
  private EntityDataCommands() {
  }

  public static void send(Object target, double priority, double crowding, double nearestPlayer, long stalenessMs) {
    ReactLanguage.send(target, CommandMessages.DEBUG_PRIORITY, MessageArgument.untrusted("value", Form.f((int) priority)));
    ReactLanguage.send(target, CommandMessages.DEBUG_CROWDING, MessageArgument.untrusted("value", Form.f((int) crowding)));
    ReactLanguage.send(target, CommandMessages.DEBUG_NEAREST_PLAYER, MessageArgument.untrusted("value", Form.f(nearestPlayer, 1)));
    ReactLanguage.send(target, CommandMessages.DEBUG_UPDATED, MessageArgument.untrusted("duration", Form.duration(stalenessMs, 0)));
  }
}
