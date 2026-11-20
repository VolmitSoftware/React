package art.arcane.react.api.web.network;

import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebRole;
import art.arcane.react.api.web.pairing.WebTokens;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.WebHost;
import art.arcane.react.platform.WebMetricsSource;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** A proxy and a backend in one JVM: the backend registers, and the proxy serves the backend's API and sockets. */
class NetworkRoutesTest {
  private static final String SECRET = NetworkSeal.newSecret();
  private static final int GAME_PORT = 25566;
  private final HttpClient http = HttpClient.newHttpClient();
  private ReactWebServer proxy;
  private ReactWebServer backend;
  private NetworkHub hub;

  @BeforeEach
  void bindPlatform() {
    ReactPlatforms.unbind();
    ReactPlatforms.bind(new TestPlatform());
  }

  @AfterEach
  void cleanup() {
    for (ReactWebServer server : new ReactWebServer[]{backend, proxy}) {
      if (server != null) {
        server.stop();
      }
    }
    if (hub != null) {
      hub.stop();
    }
    ReactPlatforms.unbind();
  }

  @Test
  void backendJoinsAndIsReachableThroughTheProxy(@TempDir File proxyFolder, @TempDir File backendFolder) throws Exception {
    hub = new NetworkHub(new View());
    hub.start();
    proxy = start(new TestHost(Optional.of(hub), "127.0.0.1:25577"), proxyFolder, configuration(""));
    backend = start(new TestHost(Optional.empty(), "127.0.0.1:" + GAME_PORT), backendFolder,
        configuration("http://127.0.0.1:" + proxy.getBoundPort()));

    long deadline = System.currentTimeMillis() + 10_000L;
    while (backend.networkStatus().orElseThrow().state() != NetworkLink.State.REGISTERED && System.currentTimeMillis() < deadline) {
      Thread.sleep(50L);
    }
    assertTrue(hub.member("lobby").isPresent(), "backend never registered");
    assertEquals(NetworkLink.State.REGISTERED, backend.networkStatus().orElseThrow().state());

    String admin = bearer(proxy, WebRole.ADMIN);
    String viewer = bearer(proxy, WebRole.VIEWER);
    JsonObject network = get("/api/v1/network", admin, 200).getAsJsonObject("data");
    assertEquals(1, network.getAsJsonObject("proxy").get("react").getAsInt());
    JsonObject lobby = network.getAsJsonArray("servers").get(0).getAsJsonObject();
    assertTrue(lobby.get("react").getAsBoolean());
    assertEquals("/network/lobby", lobby.get("basePath").getAsString());

    assertEquals("127.0.0.1:" + GAME_PORT, get("/network/lobby/api/v1/identity", admin, 200).getAsJsonObject("data").get("serverId").getAsString());
    assertEquals("viewer", get("/network/lobby/api/v1/whoami", viewer, 200).getAsJsonObject("data").get("role").getAsString());
    get("/network/lobby/api/v1/identity", "", 401);
    get("/network/nowhere/api/v1/identity", admin, 404);
    assertEquals(403, post("/network/lobby/api/v1/console/execute", viewer, 1L).statusCode());
    assertEquals(202, post("/network/lobby/api/v1/console/execute", admin, 5L).statusCode());
    assertEquals(409, post("/network/lobby/api/v1/console/execute", admin, 5L).statusCode());

    CompletableFuture<String> frame = new CompletableFuture<>();
    WebSocket socket = http.newWebSocketBuilder()
        .buildAsync(URI.create("ws://127.0.0.1:" + proxy.getBoundPort() + "/network/lobby/ws/metrics"), new WebSocket.Listener() {
          @Override
          public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            frame.complete(data.toString());
            return null;
          }
        }).get(5, TimeUnit.SECONDS);
    socket.sendText("{\"type\":\"auth\",\"token\":\"" + admin + "\"}", true);
    assertTrue(frame.get(10, TimeUnit.SECONDS).contains("\"samplers\""));
    socket.abort();
  }

  private ReactWebServer start(WebHost host, File dataFolder, WebConfiguration configuration) throws Exception {
    ReactWebServer server = new ReactWebServer(host) {
      @Override
      protected void executeAsync(Runnable runnable) {
        Thread.ofVirtual().start(runnable);
      }
    };
    server.setConfig(configuration);
    server.setDataFolder(dataFolder);
    server.start();
    server.postStart();
    server.awaitStart(5000);
    return server;
  }

  private static WebConfiguration configuration(String proxyUrl) {
    WebConfiguration configuration = new WebConfiguration();
    configuration.setListenAddress("127.0.0.1");
    configuration.setPort(0);
    configuration.setNetworkProxyUrl(proxyUrl);
    configuration.setNetworkSecret(SECRET);
    return configuration;
  }

  private static String bearer(ReactWebServer server, WebRole role) throws Exception {
    server.loadAuth();
    WebTokens tokens = server.getTokens();
    WebTokens.Link link = tokens.pair(new WebTokens.Grant("test", role.scopes(), Duration.ofDays(1), "console", Optional.empty()));
    return tokens.redeem(link.tokenId(), link.secret(), Optional.empty()).bearer();
  }

  private JsonObject get(String path, String bearer, int status) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + proxy.getBoundPort() + path)).GET();
    if (!bearer.isEmpty()) {
      request.header("Authorization", "Bearer " + bearer);
    }
    HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(status, response.statusCode(), path + " -> " + response.body());
    return JsonParser.parseString(response.body()).getAsJsonObject();
  }

  private HttpResponse<String> post(String path, String bearer, long counter) throws Exception {
    return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + proxy.getBoundPort() + path))
        .header("Authorization", "Bearer " + bearer)
        .header("X-React-Counter", Long.toString(counter))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"command\":\"list\"}"))
        .build(), HttpResponse.BodyHandlers.ofString());
  }

  private record TestHost(Optional<NetworkHub> network, String serverAddress) implements WebHost {
    @Override
    public Optional<WebConfiguration> loadConfiguration() {
      return Optional.empty();
    }

    @Override
    public WebMetricsSource metrics() {
      return WebMetricsSource.empty();
    }
  }

  private static final class View implements NetworkView {
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
      return 0;
    }

    @Override
    public List<Server> servers() {
      return List.of(new Server("lobby", "127.0.0.1", GAME_PORT, 0));
    }

    @Override
    public CompletableFuture<OptionalLong> ping(String server) {
      return CompletableFuture.completedFuture(OptionalLong.of(1L));
    }
  }

  private static final class TestPlatform implements ReactPlatform {
    @Override
    public PlatformKind kind() {
      return PlatformKind.MODDED;
    }

    @Override
    public String platformName() {
      return "NeoForge";
    }

    @Override
    public String minecraftVersion() {
      return "26.3";
    }

    @Override
    public File dataFolder() {
      return null;
    }

    @Override
    public PlatformScheduler scheduler() {
      return mock(PlatformScheduler.class);
    }

    @Override
    public ExecutorService asyncExecutor() {
      return mock(ExecutorService.class);
    }

    @Override
    public boolean dispatchConsoleCommand(String command) {
      return true;
    }

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void verbose(String message) {
    }

    @Override
    public void reportError(String context, Throwable failure) {
    }
  }
}
