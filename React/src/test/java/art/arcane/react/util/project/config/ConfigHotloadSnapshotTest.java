package art.arcane.react.util.project.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigHotloadSnapshotTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void digestDetectsSameSizeSameTimestampReplacement() throws Exception {
    Path config = temporaryDirectory.resolve("config.toml");
    FileTime timestamp = FileTime.fromMillis(1_700_000_000_000L);
    Files.writeString(config, "aaaa", StandardCharsets.UTF_8);
    Files.setLastModifiedTime(config, timestamp);
    ConfigHotloadSnapshot first = ConfigHotloadSnapshot.capture(config, 1024L);

    Files.writeString(config, "bbbb", StandardCharsets.UTF_8);
    Files.setLastModifiedTime(config, timestamp);
    ConfigHotloadSnapshot second = ConfigHotloadSnapshot.capture(config, 1024L);

    assertNotEquals(first.digest(), second.digest());
  }

  @Test
  void rejectsOversizedAndMalformedUtf8Snapshots() throws Exception {
    Path oversized = temporaryDirectory.resolve("oversized.toml");
    Files.writeString(oversized, "12345", StandardCharsets.UTF_8);
    assertThrows(IOException.class, () -> ConfigHotloadSnapshot.capture(oversized, 4L));

    Path malformed = temporaryDirectory.resolve("malformed.toml");
    Files.write(malformed, new byte[]{(byte) 0xC3, (byte) 0x28});
    assertThrows(IOException.class, () -> ConfigHotloadSnapshot.capture(malformed, 4L));
  }
}
