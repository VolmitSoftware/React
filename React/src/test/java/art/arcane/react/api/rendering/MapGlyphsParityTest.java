package art.arcane.react.api.rendering;

import org.bukkit.map.MapFont;
import org.bukkit.map.MinecraftFont;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class MapGlyphsParityTest {
  @Test
  public void fontHeightMatchesBukkit() {
    Assertions.assertEquals(MinecraftFont.Font.getHeight(), MapGlyphs.FONT_HEIGHT);
  }

  @Test
  public void everyCharMatchesBukkitSprite() {
    int height = MinecraftFont.Font.getHeight();
    for (int code = 0; code <= 0xFFFF; code++) {
      char ch = (char) code;
      MapFont.CharacterSprite sprite = MinecraftFont.Font.getChar(ch);
      MapGlyphs.Glyph glyph = MapGlyphs.glyph(ch);
      if (sprite == null) {
        Assertions.assertEquals(0, glyph.width(), "width of missing " + code);
        Assertions.assertEquals(0, glyph.advance(), "advance of missing " + code);
        Assertions.assertEquals(0, glyph.runs().length, "runs of missing " + code);
        continue;
      }

      Assertions.assertEquals(sprite.getWidth(), glyph.width(), "width of " + code);
      Assertions.assertEquals(sprite.getWidth() + 1, glyph.advance(), "advance of " + code);
      Assertions.assertArrayEquals(runsOf(sprite, height), glyph.runs(), "runs of " + code);
    }
  }

  private static int[] runsOf(MapFont.CharacterSprite sprite, int height) {
    List<Integer> runs = new ArrayList<Integer>();
    int width = sprite.getWidth();
    for (int row = 0; row < height; row++) {
      int runStart = -1;
      for (int column = 0; column <= width; column++) {
        if (column < width && sprite.get(row, column)) {
          if (runStart < 0) {
            runStart = column;
          }
          continue;
        }

        if (runStart >= 0) {
          runs.add(row);
          runs.add(runStart);
          runs.add(column - runStart);
          runStart = -1;
        }
      }
    }

    int[] result = new int[runs.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = runs.get(i);
    }
    return result;
  }
}
