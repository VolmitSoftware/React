package art.arcane.react.web;

import art.arcane.react.api.web.PairingCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PairingCodeTest {

  @Test
  void encodeDecodesRct2RoundTrip() {
    String directUrl = "https://react.example.net/server";
    String relayUrl = "wss://relay.example.net";
    String serverPubKey = "AbC-_123";
    String fingerprint = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    String code = PairingCode.encode(directUrl, relayUrl, serverPubKey, fingerprint, "tok1", "sig1");

    assertTrue(code.startsWith("RCT2."), "Code must start with RCT2. but was: " + code);
    PairingCode decoded = PairingCode.decode(code);
    assertEquals(directUrl, decoded.directUrl());
    assertEquals(relayUrl, decoded.relayUrl());
    assertEquals(serverPubKey, decoded.serverPubKey());
    assertEquals(fingerprint, decoded.fingerprint());
    assertEquals("tok1", decoded.tokenId());
    assertEquals("sig1", decoded.tokenSig());
  }

  @Test
  void encodedStringUsesBase64UrlWithoutPadding() {
    String code = PairingCode.encode(
        "http://127.0.0.1:9696",
        "",
        "pub-key",
        "fingerprint",
        "id-abc",
        "sigXYZ"
    );
    String payload = code.substring("RCT2.".length());
    assertTrue(payload.matches("[A-Za-z0-9_-]+"), "Payload must be base64url: " + payload);
  }

  @Test
  void rct1PayloadIsRejectedWithoutCompatibilityFallback() {
    assertThrows(IllegalArgumentException.class, () -> PairingCode.decode("RCT1.abc"));
  }

  @Test
  void missingOrNonStringFieldsAreRejected() {
    String missingFingerprint = "{\"directUrl\":\"https://react.example.net\",\"relayUrl\":\"\","
        + "\"serverPubKey\":\"pub\",\"tokenId\":\"id\",\"tokenSig\":\"sig\"}";
    String numericTokenId = "{\"directUrl\":\"https://react.example.net\",\"relayUrl\":\"\","
        + "\"serverPubKey\":\"pub\",\"fingerprint\":\"fp\",\"tokenId\":7,\"tokenSig\":\"sig\"}";

    assertThrows(IllegalArgumentException.class, () -> PairingCode.decode(encodeRaw(missingFingerprint)));
    assertThrows(IllegalArgumentException.class, () -> PairingCode.decode(encodeRaw(numericTokenId)));
  }

  private static String encodeRaw(String json) {
    return "RCT2." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }
}
