package art.arcane.react.content.directorcommand;

import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.volmlib.util.plugin.ComponentText;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

class DebugDumpTest {
  @TempDir
  Path data;

  private final class Host implements DebugDump.Host {
    final List<String> uploaded = new ArrayList<>();
    URI url = URI.create("https://mclo.gs/abc123");
    IOException uploadFailure;
    boolean serverThreadOpen = true;

    @Override
    public DebugReport.Snapshot capture(ReactCommandSender sender) {
      return new DebugReport.Snapshot(Instant.parse("2026-11-20T12:30:45Z"), "React", "1.2.3", "Mod",
          sender.isPlayer() ? "player" : "console-or-other", List.of(DebugReport.Field.of("Implementation", "fabric")),
          List.of(new DebugReport.Owner("react", "1.2.3", List.of())), data, null);
    }

    @Override
    public String details() {
      return "Ready: true";
    }

    @Override
    public boolean onServerThread(Runnable task) {
      if (serverThreadOpen) {
        task.run();
      }
      return serverThreadOpen;
    }

    @Override
    public boolean async(Runnable task) {
      task.run();
      return true;
    }

    @Override
    public URI upload(String report, String title, String source) throws IOException {
      if (uploadFailure != null) {
        throw uploadFailure;
      }
      uploaded.add(title + "|" + source);
      return url;
    }

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message, Throwable failure) {
    }
  }

  private List<Path> dumps() throws IOException {
    Path debug = data.resolve("debug");
    if (!Files.isDirectory(debug)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(debug)) {
      return files.sorted().toList();
    }
  }

  @Test
  void localDumpIsWrittenAndReported() throws IOException {
    Host host = new Host();
    RecordingSender sender = new RecordingSender();

    new DebugDump("React", "1.2.3", host).request(sender, false);

    List<Path> dumps = dumps();
    Assertions.assertEquals(1, dumps.size());
    Path dump = dumps.get(0);
    Assertions.assertEquals("react-v1.2.3-debugdump-2026-11-20-12-30-45.txt", dump.getFileName().toString());
    String report = Files.readString(dump);
    Assertions.assertTrue(report.startsWith("== React diagnostic report =="), report);
    Assertions.assertTrue(report.contains("\nMod: React\n"), report);
    Assertions.assertTrue(report.contains("\nReady: true\n"), report);
    Assertions.assertEquals(List.of(
        "--- /react debug dump ---", "⇀ Preparing React debug dump...",
        "--- /react debug dump ---", "⇀ Saved React debug dump to " + dump.toAbsolutePath().normalize() + ".", "⇀ Copy local path"),
        sender.lines);
    Assertions.assertTrue(host.uploaded.isEmpty());
  }

  @Test
  void uploadedDumpLinksTheReport() throws IOException {
    Host host = new Host();
    RecordingSender sender = new RecordingSender();

    new DebugDump("React", "1.2.3", host).request(sender, true);

    Assertions.assertEquals(List.of("VolmitSoftware - React - v1.2.3|VolmitSoftware/React/1.2.3"), host.uploaded);
    Assertions.assertEquals(1, dumps().size());
    Assertions.assertTrue(sender.lines.contains("⇀ Uploaded as VolmitSoftware - React - v1.2.3."), sender.lines.toString());
    Assertions.assertTrue(sender.lines.contains("⇀ Open: https://mclo.gs/abc123"), sender.lines.toString());
  }

  @Test
  void failedUploadKeepsTheLocalReport() throws IOException {
    Host host = new Host();
    host.uploadFailure = new IOException("offline");
    RecordingSender sender = new RecordingSender();

    new DebugDump("React", "1.2.3", host).request(sender, true);

    Assertions.assertEquals(1, dumps().size());
    Assertions.assertTrue(sender.lines.contains("⇀ Debug dump upload failed; the local report is saved."), sender.lines.toString());
    Assertions.assertFalse(sender.lines.stream().anyMatch((String line) -> line.contains("Open:")));
  }

  @Test
  void repeatedDumpsInOneSecondGetDistinctFiles() throws IOException {
    DebugDump dump = new DebugDump("React", "1.2.3", new Host());

    dump.request(new RecordingSender(), false);
    dump.request(new RecordingSender(), false);

    List<Path> dumps = dumps();
    Assertions.assertEquals(2, dumps.size());
    Assertions.assertEquals(List.of("react-v1.2.3-debugdump-2026-11-20-12-30-45-2.txt", "react-v1.2.3-debugdump-2026-11-20-12-30-45.txt"),
        dumps.stream().map((Path file) -> file.getFileName().toString()).toList());
  }

  @Test
  void missingPermissionIsRefused() throws IOException {
    List<String> lines = new ArrayList<>();
    ReactCommandSender denied = new ReactCommandSender() {
      @Override
      public String name() {
        return "guest";
      }

      @Override
      public boolean isPlayer() {
        return false;
      }

      @Override
      public Optional<ReactPlayer> player() {
        return Optional.empty();
      }

      @Override
      public boolean hasPermission(String node) {
        return false;
      }

      @Override
      public boolean isOp() {
        return false;
      }

      @Override
      public void send(ComponentText message) {
        lines.add(message.plain());
      }

      @Override
      public void sendMessage(String message) {
        lines.add(message);
      }

      @Override
      public Object handle() {
        return this;
      }
    };

    new DebugDump("React", "1.2.3", new Host()).request(denied, true);

    Assertions.assertEquals(List.of("--- /react debug dump ---", "⇀ Missing permission: react.debugdump"), lines);
    Assertions.assertTrue(dumps().isEmpty());
  }

  @Test
  void unscheduledCaptureFreesTheDumpForTheNextRequest() throws IOException {
    Host host = new Host();
    DebugDump dump = new DebugDump("React", "1.2.3", host);
    host.serverThreadOpen = false;
    dump.request(new RecordingSender(), false);
    host.serverThreadOpen = true;

    dump.request(new RecordingSender(), false);

    Assertions.assertEquals(1, dumps().size());
  }

  @Test
  void fileTokensKeepOnlySafeCharacters() {
    Assertions.assertEquals("react_mod-1.0_beta", DebugDump.fileToken("React Mod-1.0/beta"));
  }
}
