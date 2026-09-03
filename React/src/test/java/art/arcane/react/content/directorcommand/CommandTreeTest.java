package art.arcane.react.content.directorcommand;

import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CommandTreeTest {

  @Test  void reactDirectorTreeResolvesTestPath() {
    DirectorRuntimeNode root = reactRoot();
    assertExactPath(root, "test");
  }

  @Test  void reactDirectorTreeResolvesTestRunPath() {
    DirectorRuntimeNode root = reactRoot();
    assertExactPath(root, "test", "run");
  }

  @Test  void reactDirectorTreeResolvesTestLoadtestPath() {
    DirectorRuntimeNode root = reactRoot();
    assertExactPath(root, "test", "loadtest");
  }

  @Test  void reactDirectorTreeResolvesWebPath() {
    DirectorRuntimeNode root = reactRoot();
    assertExactPath(root, "web");
  }

  @Test  void reactDirectorTreeResolvesWebPairPath() {
    DirectorRuntimeNode root = reactRoot();
    assertExactPath(root, "web", "pair");
  }

  @Test  void reactDirectorTreeResolvesWebListPath() {
    DirectorRuntimeNode root = reactRoot();
    assertExactPath(root, "web", "list");
  }

  @Test  void reactDirectorTreeResolvesWebRevokePath() {
    DirectorRuntimeNode root = reactRoot();
    assertExactPath(root, "web", "revoke");
  }

  @Test
  void reactDirectorTreeResolvesMonitoringOnlyPaths() {
    DirectorRuntimeNode root = reactRoot();
    assertExactPath(root, "monitoring-only");
    assertExactPath(root, "monitor-only");
    assertExactPath(root, "monitoring-mode");
    assertExactPath(root, "mo");
  }

  @Test
  void reactDirectorTreeResolvesIndependentDistancePaths() {
    DirectorRuntimeNode root = reactRoot();
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

  private static DirectorRuntimeNode reactRoot() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CommandReact());
    return engine.getRoot();
  }

  private static void assertExactPath(DirectorRuntimeNode root, String... path) {
    DirectorRuntimeNode cursor = root;
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
