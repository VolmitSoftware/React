package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.util.plugin.CommandDummy;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;

public record BukkitCommandSender(CommandSender sender) implements ReactCommandSender {
  public BukkitCommandSender {
    Objects.requireNonNull(sender, "sender");
  }

  public static VolmitSender volmit(CommandSender sender) {
    return new VolmitSender(new BukkitCommandSender(sender));
  }

  /**
   * The Bukkit sender behind a React sender.
   */
  public static CommandSender bukkit(VolmitSender sender) {
    return bukkit(sender.getS());
  }

  public static CommandSender bukkit(ReactCommandSender sender) {
    if (sender instanceof BukkitCommandSender bukkitSender) {
      return bukkitSender.sender();
    }
    Object handle = sender.handle();
    if (handle instanceof CommandSender commandSender) {
      return commandSender;
    }
    throw new IllegalArgumentException("Not a Bukkit command sender: " + sender.name());
  }

  /**
   * The Bukkit player behind a React sender; only call it for player senders.
   */
  public static Player player(VolmitSender sender) {
    return (Player) bukkit(sender);
  }

  @Override
  public String name() {
    return sender.getName();
  }

  @Override
  public boolean isPlayer() {
    return sender instanceof Player;
  }

  @Override
  public Optional<ReactPlayer> player() {
    return sender instanceof Player player ? Optional.of(new BukkitReactPlayer(player)) : Optional.empty();
  }

  @Override
  public boolean hasPermission(String node) {
    return sender.hasPermission(node);
  }

  @Override
  public boolean isOp() {
    return sender.isOp();
  }

  @Override
  public void send(ComponentText message) {
    if (!(sender instanceof CommandDummy)) {
      ComponentMessenger.send(sender, message);
    }
  }

  @Override
  public void sendMessage(String message) {
    sender.sendMessage(message);
  }

  @Override
  public void playSound(String key, float volume, float pitch) {
    if (sender instanceof Player) {
      // audience delivery: spigot CommandSender has no playSound(net.kyori Sound)
      React.audiences().sender(sender).playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, volume, pitch));
    }
  }

  @Override
  public Object handle() {
    return sender;
  }
}
