package art.arcane.react.content.directorcommand;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.List;

/**
 * The report of {@code /react integration status}, built from whatever integration state the platform tracks.
 */
public final class IntegrationCommands {
  private IntegrationCommands() {
  }

  /**
   * One integration. {@code lastHeartbeatMs} is zero or less when it never answered.
   */
  public record Entry(String id, String health, String protocol, long lastHeartbeatMs, String detail) {
  }

  public static void status(Object sender, List<Entry> entries, String correlation, List<String> timeline, long nowMs) {
    ReactLanguage.sendPrefixed(sender, CommandMessages.INTEGRATION_HEADER);
    for (Entry entry : entries) {
      ReactLanguage.send(sender, CommandMessages.INTEGRATION_ENTRY,
          MessageArgument.untrusted("plugin", entry.id()),
          MessageArgument.untrusted("health", entry.health()),
          MessageArgument.untrusted("protocol", entry.protocol()),
          MessageArgument.untrusted("heartbeat", heartbeatAge(entry.lastHeartbeatMs(), nowMs)),
          MessageArgument.untrusted("detail", entry.detail()));
    }
    if (correlation != null && !correlation.isBlank()) {
      ReactLanguage.send(sender, CommandMessages.INTEGRATION_CORRELATION, MessageArgument.untrusted("detail", correlation));
    }
    if (!timeline.isEmpty()) {
      ReactLanguage.send(sender, CommandMessages.INTEGRATION_TIMELINE_HEADER);
      for (String line : timeline) {
        ReactLanguage.send(sender, CommandMessages.INTEGRATION_TIMELINE_ENTRY, MessageArgument.untrusted("entry", line));
      }
    }
  }

  static String heartbeatAge(long lastHeartbeatMs, long nowMs) {
    if (lastHeartbeatMs <= 0) {
      return ReactLanguage.plain(CommandMessages.INTEGRATION_HEARTBEAT_NEVER);
    }
    return ReactLanguage.plain(CommandMessages.INTEGRATION_HEARTBEAT_AGE,
        MessageArgument.untrusted("duration", Form.duration(Math.max(0L, nowMs - lastHeartbeatMs), 1)));
  }
}
