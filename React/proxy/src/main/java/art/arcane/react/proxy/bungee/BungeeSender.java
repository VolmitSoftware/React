package art.arcane.react.proxy.bungee;

import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.chat.ComponentSerializer;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** A BungeeCord command sender running {@code /react}: the console or a player. Rich text goes over as chat JSON. */
record BungeeSender(CommandSender sender) implements ReactCommandSender {
  @Override
  public String name() {
    return sender instanceof ProxiedPlayer ? sender.getName() : "console";
  }

  @Override
  public boolean isPlayer() {
    return sender instanceof ProxiedPlayer;
  }

  @Override
  public Optional<ReactPlayer> player() {
    return sender instanceof ProxiedPlayer player ? Optional.of(new BungeePlayer(player)) : Optional.empty();
  }

  @Override
  public boolean hasPermission(String node) {
    return sender.hasPermission(node);
  }

  @Override
  public boolean isOp() {
    return !(sender instanceof ProxiedPlayer);
  }

  @Override
  public void send(ComponentText message) {
    String json = GsonComponentSerializer.gson().serialize(MiniMessage.miniMessage().deserialize(message.miniMessage()));
    BaseComponent[] components = ComponentSerializer.parse(json);
    sender.sendMessage(components);
  }

  @Override
  public void sendMessage(String message) {
    sender.sendMessage(TextComponent.fromLegacyText(message));
  }

  @Override
  public Object handle() {
    return sender;
  }

  private record BungeePlayer(ProxiedPlayer player) implements ReactPlayer {
    @Override
    public UUID id() {
      return player.getUniqueId();
    }

    @Override
    public String name() {
      return player.getName();
    }

    @Override
    public Locale locale() {
      Locale locale = player.getLocale();
      return locale == null ? Locale.US : locale;
    }

    @Override
    public boolean hasPermission(String node) {
      return player.hasPermission(node);
    }

    @Override
    public void sendMessage(String message) {
      player.sendMessage(TextComponent.fromLegacyText(message));
    }

    @Override
    public Object handle() {
      return player;
    }
  }
}
