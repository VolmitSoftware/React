/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.api.rendering;

import org.bukkit.map.MapFont;
import org.bukkit.map.MinecraftFont;

import java.util.concurrent.ConcurrentHashMap;

final class MapGlyphs {
  static final int FONT_HEIGHT = MinecraftFont.Font.getHeight();
  static final int RUN_STRIDE = 3;

  private static final int ASCII_LIMIT = 128;
  private static final int[] NO_RUNS = new int[0];
  private static final Glyph MISSING = new Glyph(0, 0, NO_RUNS);
  private static final Glyph[] ASCII = new Glyph[ASCII_LIMIT];
  private static final ConcurrentHashMap<Character, Glyph> WIDE = new ConcurrentHashMap<Character, Glyph>();

  static {
    for (char ch = 0; ch < ASCII_LIMIT; ch++) {
      ASCII[ch] = bake(ch);
    }
  }

  private MapGlyphs() {
  }

  static Glyph glyph(char ch) {
    if (ch < ASCII_LIMIT) {
      return ASCII[ch];
    }

    Character key = Character.valueOf(ch);
    Glyph cached = WIDE.get(key);
    if (cached != null) {
      return cached;
    }

    Glyph baked = bake(ch);
    WIDE.putIfAbsent(key, baked);
    return baked;
  }

  static int advance(char ch) {
    return glyph(ch).advance();
  }

  static int runWidth(String text) {
    int widest = 0;
    int advance = 0;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '\n') {
        if (advance > widest) {
          widest = advance;
        }
        advance = 0;
        continue;
      }

      advance += glyph(ch).advance();
    }

    if (advance > widest) {
      widest = advance;
    }
    return widest <= 0 ? 0 : widest - 1;
  }

  private static Glyph bake(char ch) {
    MapFont.CharacterSprite sprite = MinecraftFont.Font.getChar(ch);
    if (sprite == null) {
      return MISSING;
    }

    int glyphWidth = sprite.getWidth();
    int[] runs = new int[FONT_HEIGHT * ((glyphWidth / 2) + 1) * RUN_STRIDE];
    int count = 0;
    for (int row = 0; row < FONT_HEIGHT; row++) {
      int runStart = -1;
      for (int column = 0; column <= glyphWidth; column++) {
        if (column < glyphWidth && sprite.get(row, column)) {
          if (runStart < 0) {
            runStart = column;
          }
          continue;
        }

        if (runStart >= 0) {
          if (count + RUN_STRIDE > runs.length) {
            int[] grown = new int[Math.max(RUN_STRIDE, runs.length * 2)];
            System.arraycopy(runs, 0, grown, 0, count);
            runs = grown;
          }

          runs[count] = row;
          runs[count + 1] = runStart;
          runs[count + 2] = column - runStart;
          count += RUN_STRIDE;
          runStart = -1;
        }
      }
    }

    if (count == 0) {
      return new Glyph(glyphWidth, glyphWidth + 1, NO_RUNS);
    }

    int[] trimmed = new int[count];
    System.arraycopy(runs, 0, trimmed, 0, count);
    return new Glyph(glyphWidth, glyphWidth + 1, trimmed);
  }

  record Glyph(int width, int advance, int[] runs) {
  }
}
