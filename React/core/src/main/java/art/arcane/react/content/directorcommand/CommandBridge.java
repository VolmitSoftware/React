package art.arcane.react.content.directorcommand;

import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;

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
      host().bridgeStatus(sender());
    }
}
