package art.arcane.react.content.directorcommand;

import art.arcane.react.core.bridge.BridgeHealthReport;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;

/**
 * The report of {@code /react bridge status}. Platforms without NMS bridges pass an empty report.
 */
public final class BridgeCommands {
  private BridgeCommands() {
  }

  public static void status(Object sender, BridgeHealthReport report) {
    if (report.entries().isEmpty()) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.BRIDGE_NONE);
      return;
    }
    ReactLanguage.sendPrefixed(sender, CommandMessages.BRIDGE_SUMMARY,
        MessageArgument.untrusted("available", report.availableCount()),
        MessageArgument.untrusted("unavailable", report.unavailableCount()));
    for (BridgeHealthReport.BridgeHealthEntry entry : report.entries()) {
      ReactLanguage.send(sender,
          entry.available() ? CommandMessages.BRIDGE_ENTRY_AVAILABLE : CommandMessages.BRIDGE_ENTRY_UNAVAILABLE,
          MessageArgument.untrusted("id", entry.logicalId()),
          MessageArgument.untrusted("detail", entry.available() ? entry.resolutionSummary() : entry.failureReason()));
    }
  }
}
