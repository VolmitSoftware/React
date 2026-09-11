package art.arcane.react.content.directorcommand;

import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import java.util.List;

class CommandTreeTest {

  private static DirectorRuntimeNode root;
  private static DirectorRuntimeEngine engine;

  @BeforeAll
  static void buildReactRoot() {
    engine = DirectorEngineFactory.create(new CommandReact());
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
  void versionAppearsOnlyInDebugHelpAndBothRootSpellingsResolve() {
    DirectorMiniMenu.DirectorHelpPage help = DirectorMiniMenu.resolveHelp(engine, List.of()).orElseThrow();
    DirectorMiniMenu.DirectorHelpPage debug = DirectorMiniMenu.resolveHelp(engine, List.of("debug")).orElseThrow();
    Assertions.assertFalse(help.entries().stream().anyMatch(node -> node.getDescriptor().getName().equals("version")));
    Assertions.assertTrue(debug.entries().stream().anyMatch(node -> node.getDescriptor().getName().equals("version")));
    assertExactPath(root, "version");
    assertExactPath(root, "v");
    assertExactPath(root, "debug", "version");
  }

  @Test
  void reactDirectorTreeResolvesMonitoringOnlyPaths() {
    assertExactPath(root, "monitoring-only");
    assertExactPath(root, "monitor-only");
    assertExactPath(root, "monitoring-mode");
    assertExactPath(root, "mo");
  }

  @Test
  void languagePluralAliasResolvesToTheLanguageCommand() {
    DirectorRuntimeNode language = findExactChild(root, "language");
    Assertions.assertNotNull(language);
    Assertions.assertSame(language, findExactChild(root, "languages"));
  }

  @Test
  void reloadIsOnlyAvailableForPluginApiPackRescans() {
    Assertions.assertNull(findExactChild(root, "reload"));
    Assertions.assertNull(findExactChild(root, "rl"));
    assertExactPath(root, "plugin-api", "reload");
    assertExactPath(root, "plugin-api", "rl");
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
