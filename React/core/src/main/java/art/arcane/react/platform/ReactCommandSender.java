package art.arcane.react.platform;

import art.arcane.volmlib.util.plugin.ComponentText;

import java.util.Optional;
import java.util.UUID;

/**
 * Whoever ran a React command: the console or a player. {@link #handle()} is the platform's own sender object and is
 * only meaningful to the platform module that produced it.
 */
public interface ReactCommandSender {
  String name();

  boolean isPlayer();

  Optional<ReactPlayer> player();

  default Optional<UUID> playerId() {
    return player().map(ReactPlayer::id);
  }

  boolean hasPermission(String node);

  boolean isOp();

  /**
   * Sends rich text. Players get the formatted component; consoles get what the platform prints for it.
   */
  void send(ComponentText message);

  /**
   * Sends text through the platform's plain string channel, legacy section codes included.
   */
  void sendMessage(String message);

  /**
   * Plays a sound to a player sender at their own position; does nothing for consoles.
   */
  default void playSound(String key, float volume, float pitch) {
  }

  Object handle();
}
