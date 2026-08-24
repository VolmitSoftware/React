package art.arcane.react.web;

import art.arcane.react.api.web.WebConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebConfigurationTest {

  @Test
  void defaultsOpenAuthenticatedListenerForExternalConnections() {
    WebConfiguration c = new WebConfiguration();
    assertTrue(c.isListenerEnabled());
    assertEquals("::", c.getListenAddress());
    assertEquals(9696, c.getPort());
    assertEquals("", c.getAdvertisedUrl());
    assertTrue(c.isRequireTokenForReads());
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

  @Test
  void advertisedUrlSetterRoundTrip() {
    WebConfiguration c = new WebConfiguration();
    c.setAdvertisedUrl("https://react.example.net");
    assertEquals("https://react.example.net", c.getAdvertisedUrl());
  }
}
