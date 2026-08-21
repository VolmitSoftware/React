package art.arcane.react.content.sampler;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SamplerPlayersTest {
  @SuppressWarnings("unchecked")
  private static Server serverWith(int onlinePlayers) {
    Server server = mock(Server.class);
    Player[] players = new Player[onlinePlayers];

    for (int index = 0; index < onlinePlayers; index++) {
      players[index] = mock(Player.class);
    }

    when((List<Player>) server.getOnlinePlayers()).thenReturn(List.of(players));
    return server;
  }

  @Test
  void countsOnlinePlayersOnTheOwningThread() {
    Server server = serverWith(3);
    SamplerPlayers sampler = new SamplerPlayers();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getServer).thenReturn(server);
      bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

      assertEquals(3D, sampler.onSample());
    }
  }

  @Test
  void neverWalksThePlayerListFromAThreadThatDoesNotOwnIt() {
    Server server = serverWith(3);
    SamplerPlayers sampler = new SamplerPlayers();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getServer).thenReturn(server);
      bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

      assertEquals(0D, sampler.onSample());
      verify(server, never()).getOnlinePlayers();
    }
  }

  @Test
  void servesTheLastOwningThreadCountToOffThreadCallers() {
    Server server = serverWith(7);
    SamplerPlayers sampler = new SamplerPlayers();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getServer).thenReturn(server);
      bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
      assertEquals(7D, sampler.onSample());

      bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
      assertEquals(7D, sampler.onSample());
    }
  }
}
