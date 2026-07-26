package art.arcane.react.api.protect.internal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProtectionTextTest {

  @Test
  void controlCharactersAreStripped() {
    Assertions.assertEquals("abc", ProtectionText.sanitize("a\u0000b\u001Fc\u007F"));
  }

  @Test
  void newlinesCannotSurviveIntoDisplayText() {
    Assertions.assertEquals("onetwo", ProtectionText.sanitize("one\ntwo"));
  }

  @Test
  void textIsTruncatedToTheLimit() {
    Assertions.assertEquals(ProtectionText.MAX_LENGTH, ProtectionText.sanitize("x".repeat(500)).length());
    Assertions.assertEquals("abcd", ProtectionText.sanitize("abcdefgh", 4));
  }

  @Test
  void nullAndEmptyBecomeEmpty() {
    Assertions.assertEquals("", ProtectionText.sanitize(null));
    Assertions.assertEquals("", ProtectionText.sanitize(""));
  }

  @Test
  void surroundingWhitespaceIsStripped() {
    Assertions.assertEquals("name", ProtectionText.sanitize("  name  "));
  }

  @Test
  void ownerTokenIsLowerCaseAndNamespacedKeySafe() {
    Assertions.assertEquals("guardianpets", ProtectionText.toOwnerToken("GuardianPets"));
    Assertions.assertEquals("my_plugin-1.0", ProtectionText.toOwnerToken("My Plugin-1.0"));
    Assertions.assertEquals("", ProtectionText.toOwnerToken(null));
    Assertions.assertEquals("", ProtectionText.toOwnerToken(""));
  }

  @Test
  void ownerTokenIsCapped() {
    Assertions.assertEquals(48, ProtectionText.toOwnerToken("a".repeat(200)).length());
  }

  @Test
  void syntheticLambdaIdsAreDetected() {
    Assertions.assertTrue(ProtectionText.isSyntheticProviderId("com.example.Rules$$Lambda/0x00007f2a"));
    Assertions.assertFalse(ProtectionText.isSyntheticProviderId("com.example.Rules"));
    Assertions.assertFalse(ProtectionText.isSyntheticProviderId(null));
  }
}
