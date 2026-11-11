package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.project.config.ConfigHotloadSnapshot;
import art.arcane.react.util.project.registry.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hotload of core/runtime-diagnostics.toml must leave the running sampler state alone, and that
 * state must never reach the file.
 */
class RuntimeDiagnosticsHotloadStateTest {
  private static final long HOTLOAD_FILE_LIMIT = 256L * 1024L;
  private static final List<String> RUNTIME_KEYS =
      List.of("snapshot", "previous", "previousNanos", "active", "registeredIds");
  private static final String FILE_FROM_OLDER_BUILD = """
      previousNanos = 0
      active = false
      registeredIds = ["react-ticker-executed"]

      [snapshot]
      sampledAtMs = 0
      windowMs = 0
      values = [0.0, 0.0]
      """;

  @TempDir
  Path dataFolder;

  private React previousPlugin;
  private Ticker ticker;
  private SampleController samples;
  private HistoryController history;
  private List<Sampler> registered;

  @BeforeEach
  void installPlugin() {
    previousPlugin = React.instance;
    React plugin = Mockito.mock(React.class);
    ticker = new Ticker(Runnable::run);
    Mockito.when(plugin.getTicker()).thenReturn(ticker);
    Mockito.when(plugin.getDataFile(ArgumentMatchers.any(String[].class))).thenAnswer(invocation -> {
      Path file = dataFolder;
      for (Object part : invocation.getArguments()) {
        file = file.resolve((String) part);
      }
      Files.createDirectories(file.getParent());
      return file.toFile();
    });
    React.instance = plugin;

    registered = new ArrayList<>();
    samples = Mockito.mock(SampleController.class);
    history = Mockito.mock(HistoryController.class);
    @SuppressWarnings("unchecked")
    Registry<Sampler> registry = Mockito.mock(Registry.class);
    Mockito.when(samples.getSamplers()).thenReturn(registry);
    Mockito.when(registry.all()).thenReturn(registered);
    Mockito.when(samples.registerSampler(Mockito.any())).thenAnswer(invocation -> {
      Sampler sampler = invocation.getArgument(0);
      sampler.start();
      registered.add(sampler);
      return true;
    });
    Mockito.when(history.latest()).thenReturn(MetricSnapshot.of(1L, System.currentTimeMillis(), List.of()));
  }

  @AfterEach
  void restorePlugin() {
    ticker.close();
    React.instance = previousPlugin;
  }

  private Path configFile() {
    return dataFolder.resolve("core").resolve("runtime-diagnostics.toml");
  }

  private void writeConfig(String content) throws IOException {
    Files.createDirectories(configFile().getParent());
    Files.writeString(configFile(), content, StandardCharsets.UTF_8);
  }

  private RuntimeDiagnosticsController runningController() {
    RuntimeDiagnosticsController controller = new RuntimeDiagnosticsController();
    controller.start();
    controller.postStart();
    controller.onTick();
    controller.onTick();
    assertPublishing();
    return controller;
  }

  private void assertPublishing() {
    assertEquals(20, registered.size());
    for (Sampler sampler : registered) {
      Sampler.Reading reading = assertDoesNotThrow(sampler::captureReading, sampler.getId());
      assertTrue(reading.available(), sampler.getId());
      assertTrue(reading.sampledAtMs() > 0L, sampler.getId());
    }
  }

  private void assertNoRuntimeKeys() throws IOException {
    String written = Files.readString(configFile(), StandardCharsets.UTF_8);
    for (String key : RUNTIME_KEYS) {
      assertFalse(written.contains(key), "runtime state '" + key + "' written to config:\n" + written);
    }
  }

  private MockedStatic<React> controllers() {
    MockedStatic<React> react = Mockito.mockStatic(React.class);
    react.when(() -> React.controller(SampleController.class)).thenReturn(samples);
    react.when(() -> React.controller(HistoryController.class)).thenReturn(history);
    return react;
  }

  @Test
  void runtimeStateIsNotWrittenToTheConfigFile() throws IOException {
    try (MockedStatic<React> react = controllers()) {
      RuntimeDiagnosticsController controller = runningController();

      assertTrue(controller.reloadFromDisk(true));

      assertNoRuntimeKeys();
      assertPublishing();
    }
  }

  @Test
  void hotloadSnapshotLeavesRunningSamplersPublishing() throws IOException {
    try (MockedStatic<React> react = controllers()) {
      RuntimeDiagnosticsController controller = runningController();
      writeConfig(FILE_FROM_OLDER_BUILD + "# edited by hand\n");

      ConfigHotloadSnapshot snapshot = ConfigHotloadSnapshot.capture(configFile(), HOTLOAD_FILE_LIMIT);
      Object prepared = controller.prepareConfigurationSnapshot(configFile().toFile(), snapshot.rawContent());
      assertTrue(controller.applyConfigurationSnapshot(prepared));

      assertPublishing();
      controller.onTick();
      assertPublishing();
    }
  }

  @Test
  void reloadFromDiskLeavesRunningSamplersPublishing() throws IOException {
    try (MockedStatic<React> react = controllers()) {
      RuntimeDiagnosticsController controller = runningController();
      writeConfig(FILE_FROM_OLDER_BUILD);

      assertTrue(controller.reloadFromDisk(false));

      assertPublishing();
      controller.onTick();
      assertPublishing();
    }
  }

  @Test
  void fileFromOlderBuildLoadsAndLosesItsRuntimeKeys() throws IOException {
    try (MockedStatic<React> react = controllers()) {
      RuntimeDiagnosticsController controller = runningController();
      writeConfig(FILE_FROM_OLDER_BUILD);

      assertTrue(controller.reloadFromDisk(true));

      assertNoRuntimeKeys();
      assertPublishing();
    }
  }
}
