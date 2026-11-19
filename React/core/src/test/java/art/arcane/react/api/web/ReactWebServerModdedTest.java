package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.WebHost;
import art.arcane.react.platform.WebMetricsSource;
import com.google.gson.JsonElement;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ReactWebServerModdedTest {
  private ReactWebServer server;

  @BeforeEach
  void bindModdedPlatform() {
    ReactPlatforms.unbind();
    ReactPlatforms.bind(new ModdedPlatform());
  }

  @AfterEach
  void cleanup() {
    if (server != null) {
      server.stop();
    }
    ReactPlatforms.unbind();
  }

  private ReactWebServer start(File dataFolder) throws Exception {
    WebConfiguration config = new WebConfiguration();
    config.setListenerEnabled(true);
    config.setListenAddress("127.0.0.1");
    config.setPort(0);
    config.setRequireTokenForReads(false);
    ReactWebServer created = new ReactWebServer(new MinimalHost()) {
      @Override
      protected void executeAsync(Runnable runnable) {
        Thread.ofVirtual().start(runnable);
      }
    };
    created.setConfig(config);
    created.setDataFolder(dataFolder);
    created.start();
    created.postStart();
    created.awaitStart(5000);
    return created;
  }

  @Test
  void identityReportsPlatformLoaderAndOwnerNoun(@TempDir File dataFolder) {
    server = new ReactWebServer(new MinimalHost());
    server.setDataFolder(dataFolder);

    IdentityDto identity = server.resolveIdentity();

    assertEquals("modded", identity.platform);
    assertEquals("NeoForge", identity.loader);
    assertEquals("mod", identity.ownerNoun);
    assertEquals("127.0.0.1:25565", identity.serverId);
  }

  @Test
  void routesWithoutAPlatformSubsystemAnswerEmptyLists(@TempDir File dataFolder) throws Exception {
    server = start(dataFolder);
    HttpClient client = HttpClient.newHttpClient();
    for (String path : List.of("/api/v1/features", "/api/v1/tweaks", "/api/v1/actions", "/api/v1/worlds",
        "/api/v1/heatmaps")) {
      JsonObject body = get(client, path);
      JsonElement data = body.get("data");
      assertTrue(data != null && data.isJsonArray() && data.getAsJsonArray().isEmpty(), path + " -> " + body);
    }
    JsonObject packs = get(client, "/api/v1/plugin-api-packs").getAsJsonObject("data");
    assertTrue(packs.getAsJsonArray("packs").isEmpty());
    JsonObject integrations = get(client, "/api/v1/integrations").getAsJsonObject("data");
    assertTrue(integrations.getAsJsonArray("integrations").isEmpty());
    JsonObject runtime = get(client, "/api/v1/metrics/runtime").getAsJsonObject("data");
    assertEquals("source-unavailable", runtime.get("jfrUnavailableReason").getAsString());
    JsonObject metrics = get(client, "/api/v1/metrics").getAsJsonObject("data");
    assertTrue(metrics.getAsJsonArray("samplers").isEmpty());
  }

  private JsonObject get(HttpClient client, String path) throws Exception {
    HttpResponse<String> response = client.send(HttpRequest.newBuilder()
        .uri(URI.create("http://127.0.0.1:" + server.getBoundPort() + path))
        .GET()
        .build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), path + " -> " + response.body());
    return JsonParser.parseString(response.body()).getAsJsonObject();
  }

  private static final class MinimalHost implements WebHost {
    @Override
    public Optional<WebConfiguration> loadConfiguration() {
      return Optional.empty();
    }

    @Override
    public WebMetricsSource metrics() {
      return WebMetricsSource.empty();
    }

    @Override
    public String serverAddress() {
      return "127.0.0.1:25565";
    }
  }

  private static final class ModdedPlatform implements ReactPlatform {
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
