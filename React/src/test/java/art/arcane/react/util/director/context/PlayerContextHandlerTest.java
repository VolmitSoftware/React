package art.arcane.react.util.director.context;

import art.arcane.react.platform.bukkit.BukkitCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlayerContextHandlerTest {
  private final PlayerContextHandler handler = new PlayerContextHandler();

  @Test
  void playerSenderResolvesItselfAsTheDefaultTarget() {
    Player player = Mockito.mock(Player.class);

    Assertions.assertSame(player, handler.handle(BukkitCommandSender.volmit(player)).player().handle());
  }

  @Test
  void consoleSenderHasNoImplicitPlayerTarget() {
    CommandSender console = Mockito.mock(CommandSender.class);

    Assertions.assertNull(handler.handle(BukkitCommandSender.volmit(console)));
  }
}
