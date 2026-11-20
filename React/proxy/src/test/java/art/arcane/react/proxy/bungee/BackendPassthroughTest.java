package art.arcane.react.proxy.bungee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendPassthroughTest {
  @Test
  void permittedPlayersKeepTheProxyCommand() {
    assertEquals(BackendPassthrough.Route.PROXY, BackendPassthrough.route("/react monitor", true, 775));
  }

  @Test
  void unpermittedModernClientsForwardTheirCommandPacket() {
    assertEquals(BackendPassthrough.Route.FORWARD_PACKET, BackendPassthrough.route("/react", false, 775));
    assertEquals(BackendPassthrough.Route.FORWARD_PACKET, BackendPassthrough.route("/RE status", false, 759));
  }

  @Test
  void unpermittedOldClientsResendTheLine() {
    assertEquals(BackendPassthrough.Route.RESEND, BackendPassthrough.route("/react monitor", false, 758));
  }

  @Test
  void otherLinesAreLeftAlone() {
    assertEquals(BackendPassthrough.Route.PROXY, BackendPassthrough.route("react", false, 775));
    assertEquals(BackendPassthrough.Route.PROXY, BackendPassthrough.route("/reactor", false, 775));
    assertEquals(BackendPassthrough.Route.PROXY, BackendPassthrough.route("/server lobby", false, 775));
  }

  @Test
  void onlyTheReactLabelsCount() {
    assertTrue(BackendPassthrough.isReactCommand("/re"));
    assertTrue(BackendPassthrough.isReactCommand("/react network list"));
    assertFalse(BackendPassthrough.isReactCommand("/"));
    assertFalse(BackendPassthrough.isReactCommand(null));
  }
}
