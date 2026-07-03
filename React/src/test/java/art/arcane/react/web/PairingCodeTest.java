package art.arcane.react.web;

import art.arcane.react.api.web.PairingCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PairingCodeTest {

  @Test
  void encodeDecodesRoundtrip() {
    String code = PairingCode.encode("localhost", 9696, "tok1", "sig1", "TIGER", "", "", "");
    assertTrue(code.startsWith("RCT1."), "Code must start with RCT1. but was: " + code);
    PairingCode decoded = PairingCode.decode(code);
    assertEquals("localhost", decoded.host());
    assertEquals(9696, decoded.port());
    assertEquals("tok1", decoded.tokenId());
    assertEquals("sig1", decoded.tokenSig());
    assertEquals("TIGER", decoded.confirmWord());
    assertEquals("", decoded.relayUrl());
    assertEquals("", decoded.serverPubKey());
    assertEquals("", decoded.fingerprint());
  }

  @Test
  void encodedStringIsBase64Url() {
    String code = PairingCode.encode("127.0.0.1", 8080, "id-abc", "sigXYZ", "RIVER", "", "", "");
    String payload = code.substring("RCT1.".length());
    assertTrue(payload.matches("[A-Za-z0-9_-]+"), "Payload must be base64url: " + payload);
  }

  @Test
  void differentInputsProduceDifferentCodes() {
    String a = PairingCode.encode("host1", 1, "id1", "sig1", "WORD1", "", "", "");
    String b = PairingCode.encode("host2", 2, "id2", "sig2", "WORD2", "", "", "");
    assertTrue(!a.equals(b), "Different inputs must produce different codes");
  }

  @Test
  void encodeDecodeRoundTripsRelayFields() {
    String relayUrl = "wss://relay.arcane.art";
    String serverPubKey = "AbC-_123";
    String fingerprint = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    String code = PairingCode.encode("relay.host", 7443, "tok-relay", "sig-relay", "RELAY", relayUrl, serverPubKey, fingerprint);
    assertTrue(code.startsWith("RCT1."), "Code must start with RCT1. but was: " + code);
    PairingCode decoded = PairingCode.decode(code);
    assertEquals("relay.host", decoded.host());
    assertEquals(7443, decoded.port());
    assertEquals("tok-relay", decoded.tokenId());
    assertEquals("sig-relay", decoded.tokenSig());
    assertEquals("RELAY", decoded.confirmWord());
    assertEquals(relayUrl, decoded.relayUrl());
    assertEquals(serverPubKey, decoded.serverPubKey());
    assertEquals(fingerprint, decoded.fingerprint());
  }

  @Test
  void decodeToleratesMissingRelayFields() {
    String legacyJson = "{\"host\":\"legacy.host\",\"port\":1234,\"tokenId\":\"tid\",\"tokenSig\":\"tsig\",\"confirmWord\":\"OLDWORD\"}";
    String encoded = "RCT1." + Base64.getUrlEncoder().withoutPadding().encodeToString(legacyJson.getBytes(StandardCharsets.UTF_8));
    PairingCode decoded = PairingCode.decode(encoded);
    assertEquals("legacy.host", decoded.host());
    assertEquals(1234, decoded.port());
    assertEquals("tid", decoded.tokenId());
    assertEquals("tsig", decoded.tokenSig());
    assertEquals("OLDWORD", decoded.confirmWord());
    assertEquals("", decoded.relayUrl());
    assertEquals("", decoded.serverPubKey());
    assertEquals("", decoded.fingerprint());
  }
}
