package art.arcane.react.api.web.network;

import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkSealTest {
  @Test
  void sealedTextOpensWithTheSameSecret() throws GeneralSecurityException {
    String secret = NetworkSeal.newSecret();
    String sealed = NetworkSeal.seal(secret, "{\"bearer\":\"tok_a.b\"}");

    assertEquals("{\"bearer\":\"tok_a.b\"}", NetworkSeal.open(secret, sealed));
  }

  @Test
  void sealingTwiceGivesDifferentTexts() throws GeneralSecurityException {
    String secret = NetworkSeal.newSecret();

    assertNotEquals(NetworkSeal.seal(secret, "same"), NetworkSeal.seal(secret, "same"));
  }

  @Test
  void anotherSecretCannotOpen() throws GeneralSecurityException {
    String sealed = NetworkSeal.seal(NetworkSeal.newSecret(), "private");

    assertThrows(GeneralSecurityException.class, () -> NetworkSeal.open(NetworkSeal.newSecret(), sealed));
  }

  @Test
  void alteredTextIsRefused() throws GeneralSecurityException {
    String secret = NetworkSeal.newSecret();
    String sealed = NetworkSeal.seal(secret, "private");
    char last = sealed.charAt(sealed.length() - 2);
    String altered = sealed.substring(0, sealed.length() - 2) + (last == 'A' ? 'B' : 'A') + sealed.charAt(sealed.length() - 1);

    assertThrows(GeneralSecurityException.class, () -> NetworkSeal.open(secret, altered));
  }

  @Test
  void blankSecretOrGarbageIsRefused() {
    assertThrows(GeneralSecurityException.class, () -> NetworkSeal.seal(" ", "text"));
    assertThrows(GeneralSecurityException.class, () -> NetworkSeal.open(NetworkSeal.newSecret(), "not base64 !"));
    assertThrows(GeneralSecurityException.class, () -> NetworkSeal.open(NetworkSeal.newSecret(), "AAAA"));
  }
}
