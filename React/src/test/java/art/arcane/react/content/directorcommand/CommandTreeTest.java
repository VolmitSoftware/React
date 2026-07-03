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
