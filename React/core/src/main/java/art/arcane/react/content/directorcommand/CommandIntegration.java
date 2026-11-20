package art.arcane.react.content.directorcommand;

import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.HostCommand;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;

@Director(
    name = "integration",
    aliases = {"int"},
    origin = DirectorOrigin.BOTH,
    description = "Cross-plugin integration status",
    descriptionKey = "command.description.integration"
)
public class CommandIntegration implements DirectorExecutor {
  @Director(
      name = "status",
      aliases = {"s"},
      origin = DirectorOrigin.BOTH,
      description = "Show Iris, Adapt, Wormholes, Gloss, HiddenOre, and BileTools integration health",
      descriptionKey = "command.description.integration.status"
  )
  @HostCommand("integrationStatus")
  public void status() {
    host().integrationStatus(sender());
  }
}
