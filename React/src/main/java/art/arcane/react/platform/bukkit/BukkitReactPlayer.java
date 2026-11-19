package art.arcane.react.platform.bukkit;

import art.arcane.react.platform.ReactPlayer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record BukkitReactPlayer(Player player) implements ReactPlayer {
  public BukkitReactPlayer {
    Objects.requireNonNull(player, "player");
  }

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
    String tag = player.getLocale();
    return tag == null || tag.isBlank() ? Locale.ROOT : Locale.forLanguageTag(tag.replace('_', '-'));
  }

  @Override
  public boolean hasPermission(String node) {
    return player.hasPermission(node);
  }

  @Override
  public void sendMessage(String message) {
    player.sendMessage(message);
  }

  @Override
  public Object handle() {
    return player;
  }
}
