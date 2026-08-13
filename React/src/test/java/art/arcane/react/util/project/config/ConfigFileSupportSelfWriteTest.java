package art.arcane.react.util.project.config;

import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigFileSupportSelfWriteTest {
  @AfterEach
  void resetSelfWriteListener() {
    ConfigFileSupport.setSelfWriteListener(null);
  }

  @Test
  void managedWriteUpdatesHotloadSnapshotBeforeQueuedEvent(@TempDir Path temporaryDirectory) throws Exception {
    File file = temporaryDirectory.resolve("feature.toml").toFile();
    Files.writeString(file.toPath(), "enabled = true\n", StandardCharsets.UTF_8);
    ConfigHotloadEngine engine = new ConfigHotloadEngine(
        candidate -> candidate != null && candidate.getName().endsWith(".toml"),
        () -> List.of(file),
        candidate -> read(candidate.toPath()),
        ConfigFileSupport::normalize
    );
    AtomicInteger notifications = new AtomicInteger();
    AtomicInteger applyCalls = new AtomicInteger();

    try {
      engine.configure(500L, List.of(file), List.of());
      ConfigFileSupport.setSelfWriteListener((writtenFile, content) -> {
        notifications.incrementAndGet();
        engine.noteSelfWrite(writtenFile, content);
      });

      String updated = "enabled = false\n";
      ConfigFileSupport.writeConfig(file, updated);
      boolean applied = engine.processFileChange(file, changedFile -> {
        applyCalls.incrementAndGet();
        return true;
      }, null);

      assertEquals(
          ConfigFileSupport.normalize(updated),
          ConfigFileSupport.normalize(Files.readString(file.toPath(), StandardCharsets.UTF_8))
      );
      assertEquals(1, notifications.get());
      assertFalse(applied);
      assertEquals(0, applyCalls.get());
    } finally {
      engine.clear();
    }
  }

  @Test
  void invalidHotloadDoesNotRewriteLiveFile(@TempDir Path temporaryDirectory) throws Exception {
    File file = temporaryDirectory.resolve("feature.toml").toFile();
    String invalid = "enabled = [\n";
    Files.writeString(file.toPath(), invalid, StandardCharsets.UTF_8);
    HotloadProbe fallback = new HotloadProbe();

    assertThrows(Exception.class, () -> ConfigFileSupport.load(
        file,
        null,
        HotloadProbe.class,
        fallback,
        false,
        "feature:probe",
        "Created missing config [feature/probe.toml] from defaults."
    ));

    assertEquals(invalid, Files.readString(file.toPath(), StandardCharsets.UTF_8));
  }

  private String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static final class HotloadProbe {
    public boolean enabled = true;
  }
}
