package art.arcane.react.content.directorcommand;

import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.project.world.DistanceSupport.DistanceType;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

@Director(
    name = "server",
    origin = DirectorOrigin.BOTH,
    description = "Set a distance across every loaded world",
    descriptionKey = "command.description.distance"
)
public final class CommandServerDistance implements DirectorExecutor {
  @Director(
      name = "view",
      aliases = {"view-distance", "vd"},
      description = "Set the view distance across every loaded world",
      descriptionKey = "command.description.distance"
  )
  public void view(
      @Param(
          name = "distance",
          description = "Distance in chunks",
          descriptionKey = "command.parameter.distance.value"
      )
      int distance
  ) {
    CommandDistance.setServer(sender().getS(), DistanceType.VIEW, distance);
  }

  @Director(
      name = "simulation",
      aliases = {"simulation-distance", "sd"},
      description = "Set the simulation distance across every loaded world",
      descriptionKey = "command.description.distance"
  )
  public void simulation(
      @Param(
          name = "distance",
          description = "Distance in chunks",
          descriptionKey = "command.parameter.distance.value"
      )
      int distance
  ) {
    CommandDistance.setServer(sender().getS(), DistanceType.SIMULATION, distance);
  }

  @Director(
      name = "send",
      aliases = {"send-view-distance", "svd"},
      description = "Set the send view distance across every loaded world",
      descriptionKey = "command.description.distance"
  )
  public void send(
      @Param(
          name = "distance",
          description = "Distance in chunks; -1 inherits the server default",
          descriptionKey = "command.parameter.distance.value"
      )
      int distance
  ) {
    CommandDistance.setServer(sender().getS(), DistanceType.SEND, distance);
  }
}
