package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.core.bridge.BridgeHealthReport;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.localization.MessageArgument;

public final class BukkitCommandBridge {

    public void status(VolmitSender sender) {
        BridgeHealthReport report = React.bridgeRegistry().snapshotHealth();
        if (report.entries().isEmpty()) {
            ReactLanguage.sendPrefixed(sender, CommandMessages.BRIDGE_NONE);
            return;
        }
        ReactLanguage.sendPrefixed(
                sender,
                CommandMessages.BRIDGE_SUMMARY,
                MessageArgument.untrusted("available", report.availableCount()),
                MessageArgument.untrusted("unavailable", report.unavailableCount())
        );
        for (BridgeHealthReport.BridgeHealthEntry entry : report.entries()) {
            ReactLanguage.send(
                    sender,
                    entry.available() ? CommandMessages.BRIDGE_ENTRY_AVAILABLE : CommandMessages.BRIDGE_ENTRY_UNAVAILABLE,
                    MessageArgument.untrusted("id", entry.logicalId()),
                    MessageArgument.untrusted("detail", entry.available() ? entry.resolutionSummary() : entry.failureReason())
            );
        }
    }
}
