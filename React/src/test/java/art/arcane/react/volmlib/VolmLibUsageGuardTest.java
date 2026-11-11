package art.arcane.react.volmlib;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Keeps production code off the Chrono-era APIs that VolmLib lacks or implements differently. */
class VolmLibUsageGuardTest {
  private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

  @Test
  void productionCodeDoesNotCallRollingSequenceMedian() throws IOException {
    Assertions.assertEquals(List.of(), filesMatching(source -> source.contains(".getMedian(")),
        "VolmLib's RollingSequence median sorts values lexicographically by their string form, so it is wrong for"
            + " mixed magnitudes and negative numbers");
  }

  @Test
  void productionCodeDoesNotCallStopwatchResetAndBegin() throws IOException {
    Assertions.assertEquals(List.of(), filesMatching(source -> source.contains("resetAndBegin")),
        "VolmLib's PrecisionStopwatch has no resetAndBegin; call reset() then begin()");
  }

  @Test
  void productionCodeDoesNotUseRollingSequenceAsADoubleConsumer() throws IOException {
    Assertions.assertEquals(List.of(), filesMatching(source -> source.contains("DoubleConsumer")
            && (source.contains("art.arcane.volmlib.util.math.RollingSequence")
            || source.contains("art.arcane.volmlib.util.math.Average"))),
        "VolmLib's Average and RollingSequence do not implement DoubleConsumer; pass sequence::put");
  }

  private static List<String> filesMatching(Predicate<String> matcher) throws IOException {
    Assertions.assertTrue(Files.isDirectory(MAIN_SOURCES), "missing " + MAIN_SOURCES.toAbsolutePath());
    List<String> matches = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(MAIN_SOURCES)) {
      for (Path path : (Iterable<Path>) paths.filter(candidate -> candidate.toString().endsWith(".java"))::iterator) {
        if (matcher.test(Files.readString(path, StandardCharsets.UTF_8))) {
          matches.add(MAIN_SOURCES.relativize(path).toString());
        }
      }
    }
    Assertions.assertFalse(Files.notExists(MAIN_SOURCES.resolve("art/arcane/react/React.java")), "source scan found no React sources");
    return matches;
  }
}
