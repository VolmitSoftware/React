package art.arcane.react.content.directorcommand;

import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.handlers.PlayerHandler;
import art.arcane.react.util.project.world.DistanceSupport.DistanceType;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.bukkit.entity.Player;

@Director(
    name = "player",
    origin = DirectorOrigin.BOTH,
    description = "Set a player's view, simulation, or send distance",
    descriptionKey = "command.description.distance"
)
public final class CommandPlayerDistance implements DirectorExecutor {
  @Director(
      name = "view",
      aliases = {"view-distance", "vd"},
      description = "Set the player's view distance",
      descriptionKey = "command.description.distance"
  )
  public void view(
      @Param(
          name = "distance",
          description = "Distance in chunks; -1 inherits the world setting",
          descriptionKey = "command.parameter.distance.value"
      )
      int distance,
      @Param(
          name = "player",
          description = "Player target; defaults to the command sender",
          descriptionKey = "command.parameter.distance.target",
          contextual = true,
          contextualOverride = true,
          customHandler = PlayerHandler.class
      )
      Player player
  ) {
    CommandDistance.setPlayer(sender().getS(), player, DistanceType.VIEW, distance);
  }

  @Director(
      name = "simulation",
      aliases = {"simulation-distance", "sd"},
      description = "Set the player's simulation distance",
      descriptionKey = "command.description.distance"
  )
  public void simulation(
      @Param(
          name = "distance",
          description = "Distance in chunks; -1 inherits the world setting",
          descriptionKey = "command.parameter.distance.value"
      )
      int distance,
      @Param(
          name = "player",
          description = "Player target; defaults to the command sender",
          descriptionKey = "command.parameter.distance.target",
          contextual = true,
          contextualOverride = true,
          customHandler = PlayerHandler.class
      )
      Player player
  ) {
    CommandDistance.setPlayer(sender().getS(), player, DistanceType.SIMULATION, distance);
  }

  @Director(
      name = "send",
      aliases = {"send-view-distance", "svd"},
      description = "Set the player's send view distance",
      descriptionKey = "command.description.distance"
  )
  public void send(
      @Param(
          name = "distance",
          description = "Distance in chunks; -1 inherits the world setting",
          descriptionKey = "command.parameter.distance.value"
      )
      int distance,
      @Param(
          name = "player",
          description = "Player target; defaults to the command sender",
          descriptionKey = "command.parameter.distance.target",
          contextual = true,
          contextualOverride = true,
          customHandler = PlayerHandler.class
      )
      Player player
  ) {
    CommandDistance.setPlayer(sender().getS(), player, DistanceType.SEND, distance);
  }
}
