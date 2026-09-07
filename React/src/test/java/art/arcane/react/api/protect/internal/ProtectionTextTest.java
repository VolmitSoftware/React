package art.arcane.react.api.protect.internal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class ProtectionTextTest {

  private static Stream<Arguments> unsafeDisplayText() {
    return Stream.of(
        Arguments.of("a\u0000b\u001Fc\u007F", "abc"),
        Arguments.of("one\ntwo", "onetwo"),
        Arguments.of("  name  ", "name")
    );
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("unsafeDisplayText")
  void sanitizeStripsControlCharactersAndSurroundingWhitespace(String raw, String expected) {
    Assertions.assertEquals(expected, ProtectionText.sanitize(raw));
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
