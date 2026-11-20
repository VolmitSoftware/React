package art.arcane.react.api.rendering;

import org.bukkit.map.MapPalette;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.Color;

@SuppressWarnings("removal")
public class MapPaletteParityTest {
  @Test
  public void paletteColorsMatchBukkit() {
    for (int i = 0; i < MapColors.PALETTE_SIZE; i++) {
      byte index = (byte) i;
      Assertions.assertEquals(MapPalette.getColor(index).getRGB(), MapColors.paletteColor(index).getRGB(), "palette index " + i);
    }
  }

  @Test
  public void paletteColorsMatchThemselves() {
    for (int i = MapColors.FIRST_OPAQUE_INDEX; i < MapColors.PALETTE_SIZE; i++) {
      int rgb = MapColors.paletteArgb((byte) i) & 0xFFFFFF;
      Assertions.assertEquals(MapPalette.matchColor(new Color(rgb)), MapColors.indexFor(rgb), "palette rgb " + Integer.toHexString(rgb));
    }
  }

  @Test
  public void nearestMatchAgreesWithBukkitAcrossRgbSpace() {
    for (int r = 0; r < 256; r += 3) {
      for (int g = 0; g < 256; g += 2) {
        for (int b = 0; b < 256; b += 2) {
          Assertions.assertEquals(MapPalette.matchColor(r, g, b), MapColors.matchColor(r, g, b), "rgb " + r + "," + g + "," + b);
        }
      }
    }
  }

  @Test
  public void indexForAgreesWithBukkitOnSample() {
    long seed = 0x5DEECE66DL;
    for (int i = 0; i < 200_000; i++) {
      seed = (seed * 6364136223846793005L) + 1442695040888963407L;
      int rgb = (int) (seed >>> 40) & 0xFFFFFF;
      Assertions.assertEquals(MapPalette.matchColor(new Color(rgb)), MapColors.indexFor(rgb), "rgb " + Integer.toHexString(rgb));
      Assertions.assertEquals(MapPalette.getColor(MapPalette.matchColor(new Color(rgb))).getRGB(), MapColors.colorFor(rgb).getRGB());
    }
  }
}
