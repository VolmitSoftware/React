package art.arcane.react.util.common.plugin;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.ChatType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;

/**
 * Chat delivery for console/RCON senders on platforms without native Adventure (plain Spigot).
 * The adventure-platform-bukkit facade silently drops console chat there: its facets (built
 * against the adventure 4.x core) fail against the slimmed 5.x core and swallow their own
 * errors unless -Dnet.kyori.adventure.debug=true. Bypass the facade entirely: serialize the
 * component to legacy section text and use Bukkit's plain sendMessage(String), which the
 * Spigot console always renders. Paper consoles implement Audience natively and never route
 * here. Non-chat audience operations (titles, sounds, bossbars) intentionally no-op via the
 * Audience defaults, matching console semantics.
 */
public final class ConsoleLegacyAudience implements Audience {
  private final CommandSender sender;

  public ConsoleLegacyAudience(CommandSender sender) {
    this.sender = sender;
  }

  public static boolean isConsoleLike(CommandSender sender) {
    return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
  }

  public static String legacyText(Component component) {
    return LegacyComponentSerializer.legacySection().serialize(component);
  }

  @Override
  public void sendMessage(Component message) {
    sender.sendMessage(legacyText(message));
  }

  @Override
  public void sendMessage(Component message, ChatType.Bound boundChatType) {
    sendMessage(message);
  }
}
