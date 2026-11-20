package art.arcane.react.api.web;

import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.WebHost;
import art.arcane.react.platform.WebMetricsSource;
import art.arcane.react.util.project.config.TomlFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WebConfigWatcherTest {
  private ReactWebServer server;

  @BeforeEach
  void bind() {
    ReactPlatforms.unbind();
    ReactPlatforms.bind(new TestPlatform());
  }

  @AfterEach
  void cleanup() {
    if (server != null) {
      server.stop();
    }
    ReactPlatforms.unbind();
  }

  @Test
  void savedNetworkSettingsApplyWithoutARestart(@TempDir File folder) throws Exception {
    File file = new File(folder, "web.toml");
    WebHost host = new FileHost(file);
    WebConfiguration initial = new WebConfiguration();
    initial.setListenAddress("127.0.0.1");
    initial.setPort(0);
    TomlFiles.save(file, initial, "web-config");
    server = new ReactWebServer(host) {
      @Override
      protected void executeAsync(Runnable runnable) {
        new Thread(runnable).start();
      }
    };
    server.setConfig(initial);
    server.setDataFolder(folder);
    server.start();
    server.postStart();
    server.awaitStart(5000);
    WebConfigWatcher watcher = new WebConfigWatcher(file, host, server);
    watcher.start();
    watcher.stop();
    assertTrue(server.networkStatus().isEmpty());

    rewrite(file, "networkProxyUrl = \"\"", "networkProxyUrl = \"http://127.0.0.1:1\"");
    rewrite(file, "networkSecret = \"\"", "networkSecret = \"saved-key\"");
    watcher.check();

    assertEquals("saved-key", server.getConfig().getNetworkSecret());
    assertEquals("http://127.0.0.1:1", server.networkStatus().orElseThrow().proxyUrl());
    assertTrue(server.getBoundPort() > 0);

    rewrite(file, "networkSecret = \"saved-key\"", "networkSecret = \"saved-key\"\nport = \"not a number\"");
    watcher.check();
    assertEquals("saved-key", server.getConfig().getNetworkSecret());
  }

  private static void rewrite(File file, String from, String to) throws IOException, InterruptedException {
    String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
    assertTrue(text.contains(from), from);
    Thread.sleep(10L);
    Files.writeString(file.toPath(), text.replace(from, to), StandardCharsets.UTF_8);
    assertTrue(file.setLastModified(System.currentTimeMillis() + 2_000L));
  }

  private record FileHost(File file) implements WebHost {
    @Override
    public Optional<WebConfiguration> loadConfiguration() throws IOException {
      return Optional.of(TomlFiles.load(file, WebConfiguration.class, new WebConfiguration(), "web-config", false, (String message) -> {
      }));
    }

    @Override
    public WebMetricsSource metrics() {
      return WebMetricsSource.empty();
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
