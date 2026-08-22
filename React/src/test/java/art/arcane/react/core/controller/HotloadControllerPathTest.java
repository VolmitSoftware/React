package art.arcane.react.core.controller;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HotloadControllerPathTest {
  @Test
  void routesAbsoluteWatcherSnapshotsAgainstARelativePluginFolder() {
    File relativeRoot = new File("build/hotload-path-test/plugins/React");
    File absoluteSnapshot = new File(relativeRoot, "core/hotload.toml").getAbsoluteFile();

    assertEquals(
        new File("core", "hotload.toml").toString(),
        HotloadController.relativizeNormalized(relativeRoot, absoluteSnapshot)
    );
  }

  @Test
  void normalizesDotSegmentsBeforeRoutingManagedConfigs() {
    File relativeRoot = new File("build/hotload-path-test/plugins/React");
    File absoluteSnapshot = new File(relativeRoot, "core/../core/hotload.toml").getAbsoluteFile();

    assertEquals(
        new File("core", "hotload.toml").toString(),
        HotloadController.relativizeNormalized(relativeRoot, absoluteSnapshot)
    );
  }
}
