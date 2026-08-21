package art.arcane.react.util.common.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleLegacyAudienceTest {

  @Test
  void legacyTextSerializesSectionCodes() {
    Component component = Component.text("React v1.2.3", NamedTextColor.GREEN);
    assertEquals("§aReact v1.2.3", ConsoleLegacyAudience.legacyText(component));
  }

  @Test
  void isConsoleLikeMatchesConsoleAndRconOnly() {
    assertTrue(ConsoleLegacyAudience.isConsoleLike(Mockito.mock(ConsoleCommandSender.class)));
    assertTrue(ConsoleLegacyAudience.isConsoleLike(Mockito.mock(RemoteConsoleCommandSender.class)));
    assertFalse(ConsoleLegacyAudience.isConsoleLike(Mockito.mock(Player.class)));
    assertFalse(ConsoleLegacyAudience.isConsoleLike(Mockito.mock(CommandSender.class)));
  }

  @Test
  void sendMessageDeliversLegacyStringThroughPlainSender() {
    ConsoleCommandSender sender = Mockito.mock(ConsoleCommandSender.class);
    ConsoleLegacyAudience audience = new ConsoleLegacyAudience(sender);

    audience.sendMessage(Component.text("hello ", NamedTextColor.RED)
        .append(Component.text("world", NamedTextColor.GOLD)));

    ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
    Mockito.verify(sender).sendMessage(line.capture());
    assertEquals("§chello §6world", line.getValue());
  }
}
