package art.arcane.react.proxy.bungee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactLineTest {
  @Test
  void permittedPlayersRunReactOnTheProxy() {
    assertEquals(ReactLine.Route.PROXY, ReactLine.route("/react monitor", true));
    assertEquals(ReactLine.Route.PROXY, ReactLine.route("/RE", true));
  }

  @Test
  void unpermittedPlayersReachTheBackend() {
    assertEquals(ReactLine.Route.BACKEND, ReactLine.route("/react environment info", false));
    assertEquals(ReactLine.Route.BACKEND, ReactLine.route("/re ", false));
  }

  @Test
  void otherLinesAreLeftAlone() {
    assertEquals(ReactLine.Route.IGNORE, ReactLine.route("react", true));
    assertEquals(ReactLine.Route.IGNORE, ReactLine.route("/reactor", true));
    assertEquals(ReactLine.Route.IGNORE, ReactLine.route("/server lobby", false));
    assertEquals(ReactLine.Route.IGNORE, ReactLine.route("/", true));
    assertEquals(ReactLine.Route.IGNORE, ReactLine.route(" /react", true));
    assertEquals(ReactLine.Route.IGNORE, ReactLine.route(null, true));
  }

  @Test
  void argumentsKeepTheTrailingEmptyWord() {
    ReactLine line = ReactLine.parse("/React network ").orElseThrow();
    assertEquals("react", line.label());
    assertArrayEquals(new String[]{"network", ""}, line.args());
    assertTrue(line.completesArguments());
  }

  @Test
  void aBareLabelHasNoArguments() {
    ReactLine line = ReactLine.parse("/re").orElseThrow();
    assertEquals(0, line.args().length);
    assertFalse(line.completesArguments());
  }
}
