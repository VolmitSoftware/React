package art.arcane.react.util.project.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileWatcherTest {
  @TempDir
  Path folder;

  @Test
  void reportsAnEditOnceItHasSettled() throws IOException {
    File file = write("a.toml", "enabled = true\n");
    ConfigFileWatcher watcher = new ConfigFileWatcher(() -> List.of(file));
    watcher.prime();
    assertTrue(watcher.poll().isEmpty());

    write("a.toml", "enabled = false\nextra = 1\n");
    file.setLastModified(file.lastModified() + 2000L);
    assertTrue(watcher.poll().isEmpty());
    List<ConfigFileWatcher.Change> changes = watcher.poll();

    assertEquals(1, changes.size());
    assertEquals("enabled = true", changes.get(0).before());
    assertEquals("enabled = false\nextra = 1", changes.get(0).after());
    assertTrue(watcher.poll().isEmpty());
  }

  @Test
  void ownWritesAreNotReportedAsEdits() throws IOException {
    File file = write("b.toml", "x = 1\n");
    ConfigFileWatcher watcher = new ConfigFileWatcher(() -> List.of(file));
    watcher.prime();

    write("b.toml", "x = 2\n");
    file.setLastModified(file.lastModified() + 2000L);
    watcher.accept(file, "x = 2\n");
    watcher.poll();

    assertTrue(watcher.poll().isEmpty());
  }

  @Test
  void newAndDeletedFilesAreChanges() throws IOException {
    File file = folder.resolve("c.toml").toFile();
    ConfigFileWatcher watcher = new ConfigFileWatcher(() -> file.isFile() ? List.of(file) : List.of());
    watcher.prime();

    write("c.toml", "y = 3\n");
    watcher.poll();
    List<ConfigFileWatcher.Change> created = watcher.poll();
    assertEquals(1, created.size());
    assertNull(created.get(0).before());
    watcher.accept(file, created.get(0).after());

    Files.delete(file.toPath());
    watcher.poll();
    List<ConfigFileWatcher.Change> deleted = watcher.poll();
    assertEquals(1, deleted.size());
    assertNull(deleted.get(0).after());
  }

  @Test
  void noticesListEachChangedKey() {
    List<ConfigFileWatcher.Notice> notices = ConfigFileWatcher.notices("feature/x.toml", "a = 1\nb = 2\n", "a = 5\nb = 2\n", null);

    assertEquals(1, notices.size());
    assertEquals("runtime.hotload.diff", notices.get(0).key().id());
  }

  private File write(String name, String text) throws IOException {
    Path path = folder.resolve(name);
    Files.writeString(path, text, StandardCharsets.UTF_8);
    return path.toFile();
  }
}
