package art.arcane.react.content.directorcommand;

import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class CommandTreeTest {

  private static DirectorRuntimeNode root;

  @BeforeAll
  static void buildReactRoot() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CommandReact());
    root = engine.getRoot();
  }

  static Stream<Arguments> commandPaths() {
    return Stream.of(
        Arguments.of((Object) new String[]{"test"}),
        Arguments.of((Object) new String[]{"test", "run"}),
        Arguments.of((Object) new String[]{"test", "loadtest"}),
        Arguments.of((Object) new String[]{"web"}),
        Arguments.of((Object) new String[]{"web", "pair"}),
        Arguments.of((Object) new String[]{"web", "list"}),
        Arguments.of((Object) new String[]{"web", "revoke"})
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("commandPaths")
  void reactDirectorTreeResolvesEveryDocumentedCommandPath(String[] path) {
    assertExactPath(root, path);
  }

  @Test
  void reactDirectorTreeResolvesMonitoringOnlyPaths() {
    assertExactPath(root, "monitoring-only");
    assertExactPath(root, "monitor-only");
    assertExactPath(root, "monitoring-mode");
    assertExactPath(root, "mo");
  }

  @Test
  void reactDirectorTreeResolvesIndependentDistancePaths() {
    assertExactPath(root, "distance", "world", "view");
    assertExactPath(root, "distance", "world", "simulation");
    assertExactPath(root, "distance", "world", "send");
    assertExactPath(root, "distance", "server", "view");
    assertExactPath(root, "distance", "server", "simulation");
    assertExactPath(root, "distance", "server", "send");
    assertExactPath(root, "distance", "player", "view");
    assertExactPath(root, "distance", "player", "simulation");
    assertExactPath(root, "distance", "player", "send");
    assertExactPath(root, "distances", "world", "view-distance");
    assertExactPath(root, "distance", "server", "simulation-distance");
    assertExactPath(root, "distance", "player", "send-view-distance");
  }

  private static void assertExactPath(DirectorRuntimeNode start, String... path) {
    DirectorRuntimeNode cursor = start;
    for (String token : path) {
      cursor = findExactChild(cursor, token);
      Assertions.assertNotNull(cursor, "Missing Director token: " + token);
    }
  }

  private static DirectorRuntimeNode findExactChild(DirectorRuntimeNode node, String token) {
    for (DirectorRuntimeNode child : node.getChildren()) {
      for (String name : child.allNames()) {
        if (name.equalsIgnoreCase(token)) {
          return child;
        }
      }
    }

    return null;
  }
}
