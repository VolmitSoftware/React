package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.project.world.DistanceSupport;
import art.arcane.react.util.project.world.DistanceSupport.DistanceType;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

  static void setServer(CommandSender sender, DistanceType type, int distance) {
    if (!DistanceSupport.isValidWorldDistance(type, distance)) {
      sendInvalid(sender, false);
      return;
    }
    if (!DistanceSupport.supportsWorld(type)) {
      sendUnavailable(sender, "server", type);
      return;
    }

    Runnable operation = () -> applyServer(sender, type, distance);
    if (!FoliaScheduler.runGlobal(React.instance, operation)) {
      respond(sender, CommandMessages.DISTANCE_SCHEDULE_FAILED);
    }
  }

  static void setWorld(CommandSender sender, World world, DistanceType type, int distance) {
    if (!DistanceSupport.isValidWorldDistance(type, distance)) {
      sendInvalid(sender, false);
      return;
    }
    if (!DistanceSupport.supportsWorld(type)) {
      sendUnavailable(sender, "world", type);
      return;
    }

    Runnable operation = () -> applyWorld(sender, world, type, distance);
    if (!FoliaScheduler.runGlobal(React.instance, operation)) {
      respond(sender, CommandMessages.DISTANCE_SCHEDULE_FAILED);
    }
  }

  static void setPlayer(CommandSender sender, Player player, DistanceType type, int distance) {
    if (!DistanceSupport.isValidPlayerDistance(distance)) {
      sendInvalid(sender, true);
      return;
    }
    if (!DistanceSupport.supportsPlayer(type)) {
      sendUnavailable(sender, "player", type);
      return;
    }

    Runnable operation = () -> applyPlayer(sender, player, type, distance);
    J.runEntity(player, operation, 0, () -> respond(sender, CommandMessages.DISTANCE_SCHEDULE_FAILED));
  }

  private static void applyServer(CommandSender sender, DistanceType type, int distance) {
    try {
      for (World world : Bukkit.getWorlds()) {
        DistanceSupport.set(world, type, distance);
      }
      sendSuccess(sender, "server", "all loaded worlds", type, distance);
    } catch (Throwable failure) {
      reportFailure(sender, "server", "all loaded worlds", type, failure);
    }
  }

  private static void applyWorld(CommandSender sender, World world, DistanceType type, int distance) {
    try {
      DistanceSupport.set(world, type, distance);
      sendSuccess(sender, "world", world.getName(), type, distance);
    } catch (Throwable failure) {
      reportFailure(sender, "world", world.getName(), type, failure);
    }
  }

  private static void applyPlayer(CommandSender sender, Player player, DistanceType type, int distance) {
    try {
      DistanceSupport.set(player, type, distance);
      sendSuccess(sender, "player", player.getName(), type, distance);
    } catch (Throwable failure) {
      reportFailure(sender, "player", player.getName(), type, failure);
    }
  }

  private static void sendSuccess(
      CommandSender sender,
      String scope,
      String target,
      DistanceType type,
      int distance
  ) {
    String value = distance == DistanceSupport.INHERIT_DISTANCE ? "inherit" : Integer.toString(distance);
    respond(
        sender,
        CommandMessages.DISTANCE_SET,
        MessageArgument.untrusted("scope", scope),
        MessageArgument.untrusted("target", target),
        MessageArgument.untrusted("type", type.displayName()),
        MessageArgument.untrusted("distance", value)
    );
  }

  private static void sendInvalid(CommandSender sender, boolean inheritanceSupported) {
    String range = inheritanceSupported
        ? "between 2 and 32 chunks, or -1 to inherit"
        : "between 2 and 32 chunks";
    respond(
        sender,
        CommandMessages.DISTANCE_INVALID,
        MessageArgument.untrusted("range", range)
    );
  }

  private static void sendUnavailable(CommandSender sender, String scope, DistanceType type) {
    respond(
        sender,
        CommandMessages.DISTANCE_UNAVAILABLE,
        MessageArgument.untrusted("scope", scope),
        MessageArgument.untrusted("type", type.displayName())
    );
  }

  private static void reportFailure(
      CommandSender sender,
      String scope,
      String target,
      DistanceType type,
      Throwable failure
  ) {
    React.reportError(
        "Failed to set " + scope + " " + type.displayName() + " distance for " + target,
        failure
    );
    respond(
        sender,
        CommandMessages.DISTANCE_FAILED,
        MessageArgument.untrusted("scope", scope),
        MessageArgument.untrusted("target", target),
        MessageArgument.untrusted("type", type.displayName())
    );
  }

  private static void respond(CommandSender sender, MessageKey key, MessageArgument... arguments) {
    Runnable response = () -> ReactLanguage.sendPrefixed(sender, key, arguments);
    if (sender instanceof Player player && !J.isOwnedByCurrentRegion(player)) {
      J.runEntity(player, response);
      return;
    }
    response.run();
  }
}
