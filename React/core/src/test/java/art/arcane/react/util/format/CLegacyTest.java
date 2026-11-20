package art.arcane.react.util.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CLegacyTest {
  @Test
  void legacyKeepsHexColoursInsteadOfRoundingToNamedOnes() {
    Component header = Component.text("20", TextColor.fromHexString("#00FF73"))
        .append(Component.text("TPS", NamedTextColor.GREEN));
    assertEquals("§x§0§0§f§f§7§320§aTPS", C.LEGACY.serialize(header));
  }

  @Test
  void compressRoundTripsHexColours() {
    assertEquals("§x§e§e§0§0§f§f350", C.compress("§x§e§e§0§0§f§f350"));
  }
}
