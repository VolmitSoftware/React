package art.arcane.react.packaging;

import art.arcane.react.React;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chrono, Spatial and Curse are no longer shipped or relocated, so any leftover reference to
 * their packages would fail at runtime with a missing class.
 */
class RemovedLibraryReferenceTest {
  private static final Pattern REMOVED_PACKAGE = Pattern.compile("art[./]arcane[./](chrono|spatial|curse)(?![A-Za-z0-9_])");
  private static final Pattern REMOVED_COORDINATE = Pattern.compile("VolmitDev[:/.](Curse|Chrono|Spatial)(?![A-Za-z0-9_])");

  private static List<String> matches(Path root, Pattern pattern) throws IOException {
    assertTrue(Files.isDirectory(root), "Missing directory " + root.toAbsolutePath());
    List<String> hits = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
        String content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
          hits.add(root.relativize(file) + ": " + matcher.group());
        }
      }
    }
    return hits;
  }

  @Test
  void mainJavaSourcesDoNotMentionRemovedPackages() throws IOException {
    assertEquals(List.of(), matches(Path.of("src", "main", "java"), REMOVED_PACKAGE));
  }

  @Test
  void mainResourcesDoNotMentionRemovedPackages() throws IOException {
    assertEquals(List.of(), matches(Path.of("src", "main", "resources"), REMOVED_PACKAGE));
  }

  @Test
  void compiledMainClassesDoNotLinkAgainstRemovedPackages() throws IOException, URISyntaxException {
    Path classes = Path.of(React.class.getProtectionDomain().getCodeSource().getLocation().toURI());

    assertEquals(List.of(), matches(classes, REMOVED_PACKAGE));
  }

  @Test
  void buildScriptNeitherDeclaresNorRelocatesRemovedLibraries() throws IOException {
    String buildScript = Files.readString(Path.of("build.gradle"));

    Matcher coordinate = REMOVED_COORDINATE.matcher(buildScript);
    assertTrue(!coordinate.find(), () -> "build.gradle still declares " + coordinate.group());
    Matcher relocation = REMOVED_PACKAGE.matcher(buildScript);
    assertTrue(!relocation.find(), () -> "build.gradle still mentions " + relocation.group());
  }

  @Test
  void scanPatternsCatchTheReferencesTheyGuardAgainst() {
    assertTrue(REMOVED_PACKAGE.matcher("import art.arcane.chrono.ChronoLatch;").find());
    assertTrue(REMOVED_PACKAGE.matcher("art/arcane/curse/Curse").find());
    assertTrue(REMOVED_PACKAGE.matcher("relocate('art.arcane.spatial', lib)").find());
    assertTrue(!REMOVED_PACKAGE.matcher("art.arcane.chronology.Clock").find());
    assertTrue(REMOVED_COORDINATE.matcher("slim('com.github.VolmitDev:Curse:23.4.3')").find());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "art.arcane.curse.Curse",
      "art.arcane.curse.model.CursedComponent",
      "art.arcane.chrono.ChronoLatch",
      "art.arcane.chrono.PrecisionStopwatch",
      "art.arcane.chrono.RollingSequence",
      "art.arcane.spatial.matter.Matter"
  })
  void removedLibraryClassesAreGoneFromTheRuntimeClasspath(String className) {
    assertThrows(ClassNotFoundException.class, () -> Class.forName(className, false, React.class.getClassLoader()));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "art.arcane.volmlib.util.scheduling.ChronoLatch",
      "art.arcane.volmlib.util.scheduling.PrecisionStopwatch",
      "art.arcane.volmlib.util.math.RollingSequence"
  })
  void volmLibReplacementsResolve(String className) {
    assertDoesNotThrow(() -> Class.forName(className, false, React.class.getClassLoader()));
  }
}
