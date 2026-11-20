package art.arcane.react.api.web.network;

import art.arcane.react.api.web.WebError;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkHubTest {
  private static final Gson GSON = new Gson();
  private static final String SECRET = "network-test-secret";
  private final long[] now = {1_000_000L};

  private NetworkHub hub(List<NetworkView.Server> servers) {
    return new NetworkHub(new View(servers), () -> now[0]);
  }

  private String sealed(String serverName, int gamePort, long sentAtMs) throws GeneralSecurityException {
    return NetworkSeal.seal(SECRET, GSON.toJson(new NetworkRegistration(serverName, "Lobby", gamePort, 9701, "tok_x.secret", "fp", "bukkit",
        "Paper", "Paper 26.3", sentAtMs)));
  }

  @Test
  void registrationMatchesTheServerOnItsGamePort() throws GeneralSecurityException {
    NetworkHub hub = hub(List.of(new NetworkView.Server("lobby", "127.0.0.1", 25566, 3),
        new NetworkView.Server("survival", "127.0.0.1", 25567, 1)));

    assertEquals("survival", hub.register(SECRET, sealed("", 25567, now[0]), "127.0.0.1"));
    NetworkHub.Member member = hub.member("survival").orElseThrow();
    assertEquals("http://127.0.0.1:9701", member.base().toString());
    assertEquals("tok_x.secret", member.bearer());
    assertTrue(hub.member("lobby").isEmpty());
  }

  @Test
  void configuredNameWinsOverThePort() throws GeneralSecurityException {
    NetworkHub hub = hub(List.of(new NetworkView.Server("lobby", "10.0.0.2", 25565, 0),
        new NetworkView.Server("games", "10.0.0.3", 25565, 0)));

    assertEquals("games", hub.register(SECRET, sealed("GAMES", 25565, now[0]), "10.0.0.3"));
  }

  @Test
  void sharedPortIsSettledByTheRemoteHost() throws GeneralSecurityException {
    NetworkHub hub = hub(List.of(new NetworkView.Server("lobby", "10.0.0.2", 25565, 0),
        new NetworkView.Server("games", "10.0.0.3", 25565, 0)));

    assertEquals("lobby", hub.register(SECRET, sealed("", 25565, now[0]), "10.0.0.2"));
    WebError unmatched = assertThrows(WebError.class, () -> hub.register(SECRET, sealed("", 25565, now[0]), "10.0.0.9"));
    assertEquals(404, unmatched.status());
  }

  @Test
  void wrongSecretAndStaleClockAreRefused() throws GeneralSecurityException {
    NetworkHub hub = hub(List.of(new NetworkView.Server("lobby", "127.0.0.1", 25566, 0)));
    String foreign = NetworkSeal.seal("other", GSON.toJson(new NetworkRegistration("", "", 25566, 9701, "tok", "", "", "", "", now[0])));

    assertEquals(401, assertThrows(WebError.class, () -> hub.register(SECRET, foreign, "127.0.0.1")).status());
    assertEquals(401, assertThrows(WebError.class,
        () -> hub.register(SECRET, sealed("", 25566, now[0] - NetworkProtocol.MAXIMUM_CLOCK_SKEW_MS - 1), "127.0.0.1")).status());
    assertEquals(404, assertThrows(WebError.class, () -> hub.register(SECRET, sealed("nowhere", 25566, now[0]), "127.0.0.1")).status());
  }

  @Test
  void memberLapsesWithoutRegistrations() throws GeneralSecurityException {
    NetworkHub hub = hub(List.of(new NetworkView.Server("lobby", "127.0.0.1", 25566, 0)));
    hub.register(SECRET, sealed("", 25566, now[0]), "::1");

    assertEquals("http://[::1]:9701", hub.member("lobby").orElseThrow().base().toString());
    now[0] += NetworkProtocol.MEMBER_TIMEOUT_MS + 1;
    assertTrue(hub.member("lobby").isEmpty());
  }

  @Test
  void snapshotListsEveryServerWithReactMembersMarked() throws GeneralSecurityException {
    NetworkHub hub = hub(List.of(new NetworkView.Server("lobby", "127.0.0.1", 25566, 4),
        new NetworkView.Server("legacy mini", "127.0.0.1", 25568, 1)));
    hub.register(SECRET, sealed("", 25566, now[0]), "127.0.0.1");

    NetworkHub.NetworkDto network = hub.snapshot("Proxy");

    assertEquals(new NetworkHub.ProxyDto("Proxy", "Velocity", "3.4.0", 5, 2, 1, 1), network.proxy());
    NetworkHub.ServerDto lobby = network.servers().get(0);
    assertTrue(lobby.react());
    assertEquals("online", lobby.status());
    assertEquals("Paper", lobby.loader());
    assertEquals("/network/lobby", lobby.basePath());
    NetworkHub.ServerDto legacy = network.servers().get(1);
    assertFalse(legacy.react());
    assertEquals("unknown", legacy.status());
    assertNull(legacy.pingMs());
    assertEquals("/network/legacy%20mini", legacy.basePath());
  }

  @Test
  void statsReadTheLiveFrame() {
    NetworkHub.Stats stats = NetworkHub.stats("{\"data\":{\"samplers\":[{\"id\":\"ticks-per-second\",\"value\":19.5},"
        + "{\"id\":\"tick-time\",\"value\":12.25},{\"id\":\"memory-used\",\"value\":1000},{\"id\":\"memory-free\",\"value\":3000},"
        + "{\"id\":\"players\",\"value\":2}]}}", 5L);

    assertEquals(new NetworkHub.Stats(19.5, 12.25, 1000L, 4000L, 5L), stats);
    assertEquals(new NetworkHub.Stats(null, null, null, null, 6L), NetworkHub.stats("not json", 6L));
  }

  private record View(List<NetworkView.Server> servers) implements NetworkView {
    @Override
    public String kind() {
      return "Velocity";
    }

    @Override
    public String version() {
      return "3.4.0";
    }

    @Override
    public int onlinePlayers() {
      return 5;
    }

    @Override
    public CompletableFuture<OptionalLong> ping(String server) {
      return CompletableFuture.completedFuture(OptionalLong.empty());
    }
  }
}
