package art.arcane.react.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class SpigotApiBoundaryTest {
  private static final List<Path> SOURCE_ROOTS = List.of(Path.of("src/main/java"), Path.of("core/src/main/java"));
  private static final List<String> PAPER_ONLY_CALLS = List.of(".teleportAsync(", ".getPluginMeta()");

  @Test
  void pluginSourcesNeverCallPaperOnlyEntityOrPluginApis() throws IOException {
    List<String> violations = new ArrayList<>();
    for (Path sourceRoot : SOURCE_ROOTS) {
      try (Stream<Path> files = Files.walk(sourceRoot)) {
        for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
          List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
          for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            for (String call : PAPER_ONLY_CALLS) {
              if (line.contains(call)) {
                violations.add(sourceRoot.relativize(file) + ":" + (index + 1) + " " + call);
              }
            }
          }
        }
      }
    }

    Assertions.assertTrue(violations.isEmpty(), "Paper-only calls break Spigot: " + violations);
  }
}
