package art.arcane.react.platform;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorldKeysTest {
  @Test
  void parseKeepsAValidNamespacedKey() {
    Assertions.assertEquals("minecraft:the_nether", WorldKeys.parse(" minecraft:the_nether "));
    Assertions.assertEquals("iris:worlds/main-1.2", WorldKeys.parse("iris:worlds/main-1.2"));
  }

  @Test
  void parseRejectsUnqualifiedOrInvalidKeys() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> WorldKeys.parse("world"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> WorldKeys.parse("minecraft:"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> WorldKeys.parse("Minecraft:World"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> WorldKeys.parse("a:b:c"));
  }
}
