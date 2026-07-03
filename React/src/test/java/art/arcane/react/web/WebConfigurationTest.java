package art.arcane.react.web;

import art.arcane.react.api.web.WebConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebConfigurationTest {

  @Test
  void defaultsAreSafeAndDisabled() {
    WebConfiguration c = new WebConfiguration();
    assertFalse(c.isEnabled());
    assertEquals("127.0.0.1", c.getBindAddress());
    assertEquals(9696, c.getPort());
    assertFalse(c.isRequireTokenForReads() == false && c.isEnabled());
  }

  @Test
  void relayDefaultsAreDisabled() {
    WebConfiguration c = new WebConfiguration();
    assertFalse(c.isRelayEnabled());
    assertEquals("", c.getRelayUrl());
  }

  @Test
  void relaySetterRoundTrip() {
    WebConfiguration c = new WebConfiguration();
    c.setRelayEnabled(true);
    c.setRelayUrl("wss://x");
    assertTrue(c.isRelayEnabled());
    assertEquals("wss://x", c.getRelayUrl());
  }
}
