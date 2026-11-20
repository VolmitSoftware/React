package art.arcane.react.proxy.velocity;

import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** A Velocity command source running {@code /react}: the console or a player. */
record VelocitySender(CommandSource source) implements ReactCommandSender {
  @Override
  public String name() {
    return source instanceof Player player ? player.getUsername() : "console";
  }

  @Override
  public boolean isPlayer() {
    return source instanceof Player;
  }

  @Override
  public Optional<ReactPlayer> player() {
    return source instanceof Player player ? Optional.of(new VelocityPlayer(player)) : Optional.empty();
  }

  @Override
  public boolean hasPermission(String node) {
    return source.hasPermission(node);
  }

  @Override
  public boolean isOp() {
    return source instanceof ConsoleCommandSource;
  }

  @Override
  public void send(ComponentText message) {
    source.sendMessage(MiniMessage.miniMessage().deserialize(message.miniMessage()));
  }

  @Override
  public void sendMessage(String message) {
    source.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message));
  }

  @Override
  public Object handle() {
    return source;
  }

  private record VelocityPlayer(Player player) implements ReactPlayer {
    @Override
    public UUID id() {
      return player.getUniqueId();
    }

    @Override
    public String name() {
      return player.getUsername();
    }

    @Override
    public Locale locale() {
      Locale locale = player.getEffectiveLocale();
      return locale == null ? Locale.US : locale;
    }

    @Override
    public boolean hasPermission(String node) {
      return player.hasPermission(node);
    }

    @Override
    public void sendMessage(String message) {
      player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message));
    }

    @Override
    public Object handle() {
      return player;
    }
  }
}
