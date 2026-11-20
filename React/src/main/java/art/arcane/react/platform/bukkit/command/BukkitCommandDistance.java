package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.content.directorcommand.DistanceCommands;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.platform.CommandHost.DistanceKind;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.DistanceSupport;
import art.arcane.react.util.project.world.DistanceSupport.DistanceType;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BukkitCommandDistance {

  static void setServer(CommandSender sender, DistanceKind kind, int distance) {
    DistanceCommands.Reply reply = reply(sender);
    if (!DistanceCommands.isValidWorldDistance(kind, distance)) {
      DistanceCommands.invalid(reply, false);
      return;
    }
    DistanceType type = DistanceType.of(kind);
    if (!DistanceSupport.supportsWorld(type)) {
      DistanceCommands.unavailable(reply, "server", kind);
      return;
    }

    Runnable operation = () -> applyServer(reply, type, distance);
    if (!FoliaScheduler.runGlobal(React.instance, operation)) {
      DistanceCommands.scheduleFailed(reply);
    }
  }

  static void setWorld(CommandSender sender, World world, DistanceKind kind, int distance) {
    DistanceCommands.Reply reply = reply(sender);
    if (!DistanceCommands.isValidWorldDistance(kind, distance)) {
      DistanceCommands.invalid(reply, false);
      return;
    }
    DistanceType type = DistanceType.of(kind);
    if (!DistanceSupport.supportsWorld(type)) {
      DistanceCommands.unavailable(reply, "world", kind);
      return;
    }

    Runnable operation = () -> applyWorld(reply, world, type, distance);
    if (!FoliaScheduler.runGlobal(React.instance, operation)) {
      DistanceCommands.scheduleFailed(reply);
    }
  }

  static void setPlayer(CommandSender sender, Player player, DistanceKind kind, int distance) {
    DistanceCommands.Reply reply = reply(sender);
    if (!DistanceCommands.isValidPlayerDistance(distance)) {
      DistanceCommands.invalid(reply, true);
      return;
    }
    DistanceType type = DistanceType.of(kind);
    if (!DistanceSupport.supportsPlayer(type)) {
      DistanceCommands.unavailable(reply, "player", kind);
      return;
    }

    Runnable operation = () -> applyPlayer(reply, player, type, distance);
    J.runEntity(player, operation, 0, () -> DistanceCommands.scheduleFailed(reply));
  }

  private static void applyServer(DistanceCommands.Reply reply, DistanceType type, int distance) {
    try {
      for (World world : Bukkit.getWorlds()) {
        DistanceSupport.set(world, type, distance);
      }
      DistanceCommands.set(reply, "server", DistanceCommands.SERVER_TARGET, type.kind(), distance);
    } catch (Throwable failure) {
      reportFailure(reply, "server", DistanceCommands.SERVER_TARGET, type, failure);
    }
  }

  private static void applyWorld(DistanceCommands.Reply reply, World world, DistanceType type, int distance) {
    try {
      DistanceSupport.set(world, type, distance);
      DistanceCommands.set(reply, "world", world.getName(), type.kind(), distance);
    } catch (Throwable failure) {
      reportFailure(reply, "world", world.getName(), type, failure);
    }
  }

  private static void applyPlayer(DistanceCommands.Reply reply, Player player, DistanceType type, int distance) {
    try {
      DistanceSupport.set(player, type, distance);
      DistanceCommands.set(reply, "player", player.getName(), type.kind(), distance);
    } catch (Throwable failure) {
      reportFailure(reply, "player", player.getName(), type, failure);
    }
  }

  private static void reportFailure(
      DistanceCommands.Reply reply,
      String scope,
      String target,
      DistanceType type,
      Throwable failure
  ) {
    React.reportError(DistanceCommands.failureContext(scope, target, type.kind()), failure);
    DistanceCommands.failed(reply, scope, target, type.kind());
  }

  private static DistanceCommands.Reply reply(CommandSender sender) {
    return (MessageKey key, MessageArgument... arguments) -> respond(sender, key, arguments);
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
