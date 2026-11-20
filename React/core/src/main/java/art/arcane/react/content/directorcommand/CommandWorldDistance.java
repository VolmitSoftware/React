package art.arcane.react.content.directorcommand;

import art.arcane.react.platform.CommandHost.DistanceKind;
import art.arcane.react.platform.CommandTargets;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.HostCommand;
import art.arcane.react.util.director.handlers.WorldHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

@Director(
    name = "world",
    origin = DirectorOrigin.BOTH,
    description = "Set a world view, simulation, or send distance",
    descriptionKey = "command.description.distance"
)
public final class CommandWorldDistance implements DirectorExecutor {
  @Director(
      name = "view",
      aliases = {"view-distance", "vd"},
      description = "Set the world's view distance",
      descriptionKey = "command.description.distance"
  )
  @HostCommand("worldDistance")
  public void view(
      @Param(
          name = "distance",
          description = "Distance in chunks",
          descriptionKey = "command.parameter.distance.value"
      )
      int distance,
      @Param(
          name = "world",
          description = "World target; defaults to the current world",
          descriptionKey = "command.parameter.distance.target",
          contextual = true,
          contextualOverride = true,
          customHandler = WorldHandler.class
      )
      CommandTargets.World world
  ) {
    host().worldDistance(sender(), world, DistanceKind.VIEW, distance);
  }

  @Director(
      name = "simulation",
      aliases = {"simulation-distance", "sd"},
      description = "Set the world's simulation distance",
      descriptionKey = "command.description.distance"
  )
  @HostCommand("worldDistance")
  public void simulation(
      @Param(
          name = "distance",
          description = "Distance in chunks",
          descriptionKey = "command.parameter.distance.value"
      )
      int distance,
      @Param(
          name = "world",
          description = "World target; defaults to the current world",
          descriptionKey = "command.parameter.distance.target",
          contextual = true,
          contextualOverride = true,
          customHandler = WorldHandler.class
      )
      CommandTargets.World world
  ) {
    host().worldDistance(sender(), world, DistanceKind.SIMULATION, distance);
  }

  @Director(
      name = "send",
      aliases = {"send-view-distance", "svd"},
      description = "Set the world's send view distance",
      descriptionKey = "command.description.distance"
  )
  @HostCommand("worldDistance")
  public void send(
      @Param(
          name = "distance",
          description = "Distance in chunks; -1 inherits the server default",
          descriptionKey = "command.parameter.distance.value"
      )
      int distance,
      @Param(
          name = "world",
          description = "World target; defaults to the current world",
          descriptionKey = "command.parameter.distance.target",
          contextual = true,
          contextualOverride = true,
          customHandler = WorldHandler.class
      )
      CommandTargets.World world
  ) {
    host().worldDistance(sender(), world, DistanceKind.SEND, distance);
  }
}
