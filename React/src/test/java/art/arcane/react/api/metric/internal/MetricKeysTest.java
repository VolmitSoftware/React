package art.arcane.react.api.metric.internal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MetricKeysTest {

  @Test
  void sourceIdsAreNormalizedToLowerCase() {
    Assertions.assertEquals("guardianpets", MetricKeys.normalizeSourceId("  GuardianPets "));
    Assertions.assertEquals("", MetricKeys.normalizeSourceId(null));
  }

  @Test
  void validSourceIdsAreAlphanumericWithSeparators() {
    Assertions.assertTrue(MetricKeys.isValidSourceId("guardianpets"));
    Assertions.assertTrue(MetricKeys.isValidSourceId("my-plugin_2"));
    Assertions.assertFalse(MetricKeys.isValidSourceId("a"));
    Assertions.assertFalse(MetricKeys.isValidSourceId("-leading"));
    Assertions.assertFalse(MetricKeys.isValidSourceId("Upper"));
    Assertions.assertFalse(MetricKeys.isValidSourceId("has space"));
    Assertions.assertFalse(MetricKeys.isValidSourceId("has.dot"));
    Assertions.assertFalse(MetricKeys.isValidSourceId(null));
    Assertions.assertFalse(MetricKeys.isValidSourceId("x".repeat(MetricKeys.MAX_SOURCE_ID_LENGTH + 1)));
  }

  @Test
  void siblingPluginIdsAreReservedAndUnacceptable() {
    for (String reserved : MetricKeys.RESERVED_SOURCE_IDS) {
      Assertions.assertTrue(MetricKeys.isReserved(reserved), reserved);
      Assertions.assertFalse(MetricKeys.isAcceptableSourceId(reserved), reserved);
    }

    Assertions.assertTrue(MetricKeys.isAcceptableSourceId("guardianpets"));
  }

  @Test
  void keysMustBeNamespacedUnderTheirSource() {
    Assertions.assertTrue(MetricKeys.isValidKey("guardianpets", "guardianpets.pets.live"));
    Assertions.assertFalse(MetricKeys.isValidKey("guardianpets", "otherplugin.pets.live"));
    Assertions.assertFalse(MetricKeys.isValidKey("guardianpets", "guardianpets"));
    Assertions.assertFalse(MetricKeys.isValidKey("guardianpets", "guardianpets."));
    Assertions.assertFalse(MetricKeys.isValidKey("guardianpets", null));
  }

  @Test
  void keysRejectUppercaseWhitespaceAndDoubleSeparators() {
    Assertions.assertFalse(MetricKeys.isValidKey("guardianpets", "guardianpets.Pets"));
    Assertions.assertFalse(MetricKeys.isValidKey("guardianpets", "guardianpets.pets live"));
    Assertions.assertFalse(MetricKeys.isValidKey("guardianpets", "guardianpets..pets"));
    Assertions.assertFalse(MetricKeys.isValidKey("guardianpets", "guardianpets.pets."));
  }

  @Test
  void keysAreLengthCapped() {
    String tooLong = "guardianpets." + "a".repeat(MetricKeys.MAX_KEY_LENGTH);
    Assertions.assertFalse(MetricKeys.isValidKey("guardianpets", tooLong));
  }

  @Test
  void samplerIdIsDerivedFromTheKeyAndIsRegistrySafe() {
    Assertions.assertEquals("guardianpets-pets-live", MetricKeys.samplerIdFor("guardianpets.pets.live"));
    Assertions.assertEquals("", MetricKeys.samplerIdFor(null));
    Assertions.assertEquals("", MetricKeys.samplerIdFor(""));
  }

  @Test
  void samplerIdsAreUniquePerKey() {
    Assertions.assertNotEquals(
        MetricKeys.samplerIdFor("guardianpets.pets.live"),
        MetricKeys.samplerIdFor("guardianpets.pets.dead"));
  }
}
