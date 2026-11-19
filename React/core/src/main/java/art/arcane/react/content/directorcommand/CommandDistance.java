package art.arcane.react.content.directorcommand;

import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;

@Director(
    name = "distance",
    aliases = {"distances"},
    origin = DirectorOrigin.BOTH,
    description = "Set a server, world, or player view, simulation, or send distance",
    descriptionKey = "command.description.distance"
)
public final class CommandDistance implements DirectorExecutor {
  private CommandServerDistance server;
  private CommandWorldDistance world;
  private CommandPlayerDistance player;
}
