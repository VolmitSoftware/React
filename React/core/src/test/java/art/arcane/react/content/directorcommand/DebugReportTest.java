package art.arcane.react.content.directorcommand;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

class DebugReportTest {
  @TempDir
  Path data;

  private DebugReport.Snapshot snapshot(String noun, List<DebugReport.Owner> owners) {
    return new DebugReport.Snapshot(Instant.parse("2026-11-20T12:00:00Z"), "React", "1.2.3", noun, "console-or-other",
        List.of(DebugReport.Field.of("Implementation", "neoforge"), DebugReport.Field.of("Players online", 3)),
        owners, data, null);
  }

  @Test
  void modReportNamesModsInEverySection() {
    String report = DebugReport.create(snapshot("Mod", List.of(
        new DebugReport.Owner("alpha", "1.0", List.of(DebugReport.Field.of("name", "Alpha"))),
        new DebugReport.Owner("beta", "2.0", List.of()))), "Ready: true");

    Assertions.assertTrue(report.startsWith("== React diagnostic report ==\nGenerated (UTC): 2026-11-20T12:00:00Z\nFormat: 1\nMod: React\nVersion: 1.2.3\n"), report);
    Assertions.assertTrue(report.contains("== Server ==\nImplementation: neoforge\nPlayers online: 3\n\nReady: true\n"), report);
    Assertions.assertTrue(report.contains("== Mods ==\nLoaded: 2\n- alpha 1.0 | name=Alpha\n- beta 2.0\n"), report);
    Assertions.assertTrue(report.contains("== Mod data filesystem =="), report);
    Assertions.assertTrue(report.contains("== Mod configuration files =="), report);
    Assertions.assertTrue(report.contains("== Mod artifact ==\nStatus: code source unavailable\n"), report);
    Assertions.assertFalse(report.contains("Plugin"), report);
  }

  @Test
  void sectionsFollowTheBukkitOrder() {
    String report = DebugReport.create(snapshot("Plugin", List.of()), "");
    List<String> order = List.of("== Server ==", "== Plugins ==", "== Java runtime ==", "== Memory ==",
        "== CPU and operating system ==", "== Garbage collectors ==", "== Buffer pools ==", "== Plugin data filesystem ==",
        "== Plugin configuration files ==", "== Plugin artifact ==");
    int previous = -1;
    for (String section : order) {
      int index = report.indexOf(section);
      Assertions.assertTrue(index > previous, section);
      previous = index;
    }
  }

  @Test
  void valuesAreFlattenedToOneLine() {
    DebugReport.Snapshot snapshot = new DebugReport.Snapshot(Instant.EPOCH, "React", "1", "Mod", "player",
        List.of(DebugReport.Field.of("Motd", "line one\nline\ttwo"), DebugReport.Field.of("Missing", null)), List.of(), data, null);

    String report = DebugReport.create(snapshot, null);

    Assertions.assertTrue(report.contains("Motd: line one line two\n"), report);
    Assertions.assertTrue(report.contains("Missing: unavailable\n"), report);
  }

  @Test
  void configurationFilesAreTopLevelConfigsSortedByName() throws IOException {
    Files.writeString(data.resolve("web.toml"), "a");
    Files.writeString(data.resolve("React.toml"), "b");
    Files.writeString(data.resolve("notes.txt"), "c");
    Files.createDirectories(data.resolve("debug"));
    Files.writeString(data.resolve("debug").resolve("nested.toml"), "d");

    Assertions.assertEquals(List.of(Path.of("React.toml"), Path.of("web.toml")), DebugReport.configurationFiles(data));
  }

  @Test
  void configurationFilesAreDescribedWithHashes() throws IOException {
    Files.writeString(data.resolve("react.toml"), "abc");

    String report = DebugReport.create(snapshot("Mod", List.of()), "");

    Assertions.assertTrue(report.contains("react.toml: size=3.00 B (3 bytes), modified="), report);
    Assertions.assertTrue(report.contains("sha256=ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), report);
  }

  @Test
  void missingDirectoryHasNoConfigurationFiles() {
    Assertions.assertEquals(List.of(), DebugReport.configurationFiles(data.resolve("absent")));
  }

  @Test
  void byteSizesUseBinaryUnits() {
    Assertions.assertEquals("unavailable", DebugReport.bytes(-1L));
    Assertions.assertEquals("1.50 KiB (1536 bytes)", DebugReport.bytes(1536L));
  }

  @Test
  void durationsSpellEveryUnit() {
    Assertions.assertEquals("1d 1h 1m 1s 1ms", DebugReport.duration(90_061_001L));
    Assertions.assertEquals("unavailable", DebugReport.duration(-1L));
  }
}
