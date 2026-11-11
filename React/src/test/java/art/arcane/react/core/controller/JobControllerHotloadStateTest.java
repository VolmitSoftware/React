package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.util.project.config.ConfigHotloadSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The adaptive compute target is tuned while the server runs; a hotload of core/job.toml changes
 * the configured limits only.
 */
class JobControllerHotloadStateTest {
  private static final long HOTLOAD_FILE_LIMIT = 256L * 1024L;
  private static final double ADAPTED_TARGET = 0.7D;
  private static final String FILE_FROM_OLDER_BUILD = """
      maxComputeTime = 2.0
      maxSpikeInterval = 400
      currentComputeTarget = 0.01
      highUtilizationThresholdPercent = 0.6
      lowUtilizationThresholdPercent = 0.2
      """;

  @TempDir
  Path dataFolder;

  private React previousPlugin;

  @BeforeEach
  void installPlugin() {
    previousPlugin = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getDataFile(ArgumentMatchers.any(String[].class))).thenAnswer(invocation -> {
      Path file = dataFolder;
      for (Object part : invocation.getArguments()) {
        file = file.resolve((String) part);
      }
      Files.createDirectories(file.getParent());
      return file.toFile();
    });
    React.instance = plugin;
  }

  @AfterEach
  void restorePlugin() {
    React.instance = previousPlugin;
  }

  private Path configFile() {
    return dataFolder.resolve("core").resolve("job.toml");
  }

  private void writeConfig(String content) throws IOException {
    Files.createDirectories(configFile().getParent());
    Files.writeString(configFile(), content, StandardCharsets.UTF_8);
  }

  private static JobController adaptedController() {
    JobController controller = new JobController();
    controller.setCurrentComputeTarget(ADAPTED_TARGET);
    return controller;
  }

  private static void assertLimitsLoadedAndTargetKept(JobController controller) {
    assertEquals(2D, controller.getMaxComputeTime(), 1.0E-9D);
    assertEquals(400L, controller.getMaxSpikeInterval());
    assertEquals(0.6D, controller.getHighUtilizationThresholdPercent(), 1.0E-9D);
    assertEquals(0.2D, controller.getLowUtilizationThresholdPercent(), 1.0E-9D);
    assertEquals(ADAPTED_TARGET, controller.getCurrentComputeTarget(), 1.0E-9D);
  }

  @Test
  void computeTargetIsNotWrittenToTheConfigFile() throws IOException {
    JobController controller = adaptedController();

    assertTrue(controller.reloadFromDisk(true));

    String written = Files.readString(configFile(), StandardCharsets.UTF_8);
    assertTrue(written.contains("maxComputeTime"), written);
    assertFalse(written.contains("currentComputeTarget"), written);
    assertEquals(ADAPTED_TARGET, controller.getCurrentComputeTarget(), 1.0E-9D);
  }

  @Test
  void hotloadSnapshotKeepsTheAdaptedComputeTarget() throws IOException {
    JobController controller = adaptedController();
    writeConfig(FILE_FROM_OLDER_BUILD);

    ConfigHotloadSnapshot snapshot = ConfigHotloadSnapshot.capture(configFile(), HOTLOAD_FILE_LIMIT);
    Object prepared = controller.prepareConfigurationSnapshot(configFile().toFile(), snapshot.rawContent());
    assertTrue(controller.applyConfigurationSnapshot(prepared));

    assertLimitsLoadedAndTargetKept(controller);
  }

  @Test
  void reloadFromDiskKeepsTheAdaptedComputeTarget() throws IOException {
    JobController controller = adaptedController();
    writeConfig(FILE_FROM_OLDER_BUILD);

    assertTrue(controller.reloadFromDisk(false));

    assertLimitsLoadedAndTargetKept(controller);
  }

  @Test
  void fileFromOlderBuildLosesTheComputeTargetKeyOnRewrite() throws IOException {
    JobController controller = adaptedController();
    writeConfig(FILE_FROM_OLDER_BUILD);

    assertTrue(controller.reloadFromDisk(true));

    String written = Files.readString(configFile(), StandardCharsets.UTF_8);
    assertFalse(written.contains("currentComputeTarget"), written);
    assertLimitsLoadedAndTargetKept(controller);
  }
}
