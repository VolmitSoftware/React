package art.arcane.react.web.pairing;

import art.arcane.react.api.web.pairing.PairingLink;
import art.arcane.react.api.web.relay.ReactIdentityStore;
import art.arcane.react.api.web.relay.ReactServerIdentity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

class PairingLinkTest {
  private static final String SECRET = "S".repeat(43);

  @Test
  void aLinkRoundTripsAndCarriesWhatTheCardShows(@TempDir File folder) throws Exception {
    ReactServerIdentity identity = ReactIdentityStore.loadOrCreate(folder);
    PairingLink link = link(identity, List.of("read", "op:execute"), Optional.of("laptop"));

    String url = link.url("https://react.volmitsoftware.com/");
    Assertions.assertTrue(url.startsWith("https://react.volmitsoftware.com/pair#RCT3."), url);
    String code = url.substring(url.indexOf('#') + 1);
    Assertions.assertEquals(link, PairingLink.decode(code));
    Assertions.assertEquals(code, PairingLink.decode(code).encode());

    JsonObject payload = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(code.substring(5)), StandardCharsets.UTF_8))
        .getAsJsonObject();
    Assertions.assertEquals(List.of("v", "directUrl", "relayUrl", "fingerprint", "serverKey", "tokenId", "secret", "expiresAt",
        "serverName", "scopes", "role", "lifetimeSeconds", "label"), List.copyOf(payload.keySet()));
    Assertions.assertEquals("operator", payload.get("role").getAsString());
    Assertions.assertEquals(identity.fingerprint(), payload.get("fingerprint").getAsString());
  }

  @Test
  void customScopesCarryNoRole(@TempDir File folder) throws Exception {
    PairingLink link = link(ReactIdentityStore.loadOrCreate(folder), List.of("read", "console:read"), Optional.empty());
    Assertions.assertEquals(Optional.empty(), link.role());
    Assertions.assertFalse(link.encode().isEmpty());
  }

  @Test
  void aWrongFingerprintOrUnknownFieldIsRefused(@TempDir File folder) throws Exception {
    ReactServerIdentity identity = ReactIdentityStore.loadOrCreate(folder);
    Assertions.assertThrows(IllegalArgumentException.class, () -> new PairingLink(Optional.of("http://127.0.0.1:9696"), Optional.empty(),
        "0".repeat(64), identity.publicKeyBase64(), "tok_AAAAAAAAAAAAAAAA", SECRET, Instant.now(), "Lobby",
        List.of("read"), 60, Optional.empty()));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new PairingLink(Optional.empty(), Optional.empty(),
        identity.fingerprint(), identity.publicKeyBase64(), "tok_AAAAAAAAAAAAAAAA", SECRET, Instant.now(), "Lobby",
        List.of("read"), 60, Optional.empty()));

    String extra = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"v\":1,\"token\":\"x\"}".getBytes(StandardCharsets.UTF_8));
    IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class, () -> PairingLink.decode("RCT3." + extra));
    Assertions.assertTrue(refused.getMessage().contains("$.token"), refused.getMessage());
    Assertions.assertThrows(IllegalArgumentException.class, () -> PairingLink.decode("RCT2.abc"));
  }

  private static PairingLink link(ReactServerIdentity identity, List<String> scopes, Optional<String> label) {
    return new PairingLink(Optional.of("http://127.0.0.1:9696"), Optional.of("ws://127.0.0.1:9821"), identity.fingerprint(),
        identity.publicKeyBase64(), "tok_AAAAAAAAAAAAAAAA", SECRET, Instant.parse("2026-11-13T10:10:00Z"), "Lobby", scopes, 604800,
        label);
  }
}
