package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.core.bridge.BridgeHealthReport;
import art.arcane.react.util.format.C;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;

@Director(
    name = "bridge",
    aliases = {"br", "bridges"},
    origin = DirectorOrigin.BOTH,
    description = "Show NMS bridge resolution status"
)
public class CommandBridge implements DirectorExecutor {

    @Director(
        name = "status",
        aliases = {"s", "list"},
        description = "List all registered NMS bridges and their resolution status",
        origin = DirectorOrigin.BOTH
    )
    public void status() {
        BridgeHealthReport report = React.bridgeRegistry().snapshotHealth();
        if (report.entries().isEmpty()) {
            sender().sendMessage(C.REACT + "No bridges registered.");
            return;
        }
        sender().sendMessage(C.REACT + "NMS Bridge Status — "
                + C.GREEN + report.availableCount() + " available"
                + C.GRAY + ", "
                + C.RED + report.unavailableCount() + " unavailable");
        for (BridgeHealthReport.BridgeHealthEntry entry : report.entries()) {
            String status = entry.available()
                    ? C.GREEN + "OK   "
                    : C.RED + "FAIL ";
            String detail = entry.available()
                    ? C.GRAY + entry.resolutionSummary()
                    : C.RED + entry.failureReason();
            sender().sendMessage("  " + status + C.WHITE + entry.logicalId() + " " + detail);
        }
    }
}
