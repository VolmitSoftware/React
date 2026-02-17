package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.decree.DecreeExecutor;
import art.arcane.react.util.format.C;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.format.Form;

@Director(
        name = "integration",
        aliases = {"int"},
        origin = DirectorOrigin.BOTH,
        description = "Cross-plugin integration status"
)
public class CommandIntegration implements DecreeExecutor {
    @Director(
            name = "status",
            aliases = {"s"},
            origin = DirectorOrigin.BOTH,
            description = "Show Iris/Adapt integration health, protocol and heartbeat status"
    )
    public void status() {
        IntegrationController controller = React.controller(IntegrationController.class);
        if (controller == null) {
            sender().sendMessage(C.RED + "Integration controller is not ready.");
            return;
        }

        long now = System.currentTimeMillis();
        sender().sendMessage(C.REACT + "Integration Status");
        for (IntegrationController.IntegrationStatus status : controller.statuses()) {
            String heartbeatAge = status.lastHeartbeatMs() <= 0
                    ? "never"
                    : Form.duration(Math.max(0L, now - status.lastHeartbeatMs()), 1) + " ago";
            sender().sendMessage(
                    C.GRAY + "- " + C.AQUA + status.pluginId()
                            + C.GRAY + " health=" + C.WHITE + status.health().name().toLowerCase()
                            + C.GRAY + " protocol=" + C.WHITE + status.protocol()
                            + C.GRAY + " heartbeat=" + C.WHITE + heartbeatAge
                            + C.GRAY + " detail=" + C.WHITE + status.message()
            );
        }

        if (ReactConfiguration.get().isVerbose() && controller.getLastCorrelationMessage() != null && !controller.getLastCorrelationMessage().isBlank()) {
            sender().sendMessage(C.GRAY + "Correlation: " + C.WHITE + controller.getLastCorrelationMessage());
        }

        var timeline = controller.recentTimeline(4);
        if (!timeline.isEmpty()) {
            sender().sendMessage(C.GRAY + "Incident Timeline:");
            timeline.forEach(line -> sender().sendMessage(C.DARK_GRAY + "  " + line));
        }
    }
}
