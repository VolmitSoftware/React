package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.core.bridge.BridgeHealthReport;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.localization.MessageArgument;

@Director(
    name = "bridge",
    aliases = {"br", "bridges"},
    origin = DirectorOrigin.BOTH,
    description = "Show NMS bridge resolution status",
    descriptionKey = "command.description.bridge"
)
public class CommandBridge implements DirectorExecutor {

    @Director(
        name = "status",
        aliases = {"s", "list"},
        description = "List all registered NMS bridges and their resolution status",
        descriptionKey = "command.description.bridge.status",
        origin = DirectorOrigin.BOTH
    )
    public void status() {
        BridgeHealthReport report = React.bridgeRegistry().snapshotHealth();
        if (report.entries().isEmpty()) {
            ReactLanguage.sendPrefixed(sender(), CommandMessages.BRIDGE_NONE);
            return;
        }
        ReactLanguage.sendPrefixed(
                sender(),
                CommandMessages.BRIDGE_SUMMARY,
                MessageArgument.untrusted("available", report.availableCount()),
                MessageArgument.untrusted("unavailable", report.unavailableCount())
        );
        for (BridgeHealthReport.BridgeHealthEntry entry : report.entries()) {
            ReactLanguage.send(
                    sender(),
                    entry.available() ? CommandMessages.BRIDGE_ENTRY_AVAILABLE : CommandMessages.BRIDGE_ENTRY_UNAVAILABLE,
                    MessageArgument.untrusted("id", entry.logicalId()),
                    MessageArgument.untrusted("detail", entry.available() ? entry.resolutionSummary() : entry.failureReason())
            );
        }
    }
}
