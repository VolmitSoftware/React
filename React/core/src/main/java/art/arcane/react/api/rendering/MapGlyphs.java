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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class MapGlyphs {
  public static final int RUN_STRIDE = 3;
  public static final int FONT_HEIGHT;

  private static final String FONT_RESOURCE = "map-font.txt";
  private static final int ASCII_LIMIT = 128;
  private static final int[] NO_RUNS = new int[0];
  private static final Glyph MISSING = new Glyph(0, 0, NO_RUNS);
  private static final Glyph[] ASCII = new Glyph[ASCII_LIMIT];
  private static final Map<Character, Glyph> WIDE = new HashMap<Character, Glyph>();

  static {
    FONT_HEIGHT = load();
    for (int ch = 0; ch < ASCII_LIMIT; ch++) {
      if (ASCII[ch] == null) {
        ASCII[ch] = MISSING;
      }
    }
  }

  private MapGlyphs() {
  }

  public static Glyph glyph(char ch) {
    if (ch < ASCII_LIMIT) {
      return ASCII[ch];
    }

    Glyph baked = WIDE.get(Character.valueOf(ch));
    return baked == null ? MISSING : baked;
  }

  public static int advance(char ch) {
    return glyph(ch).advance();
  }

  public static int runWidth(String text) {
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

  // Resource layout: "h <height>", then one line per glyph: "<hex code point> <width> <row mask hex> x height",
  // where bit n of a row mask is column n. Baked once from Bukkit's MinecraftFont.
  private static int load() {
    InputStream stream = MapGlyphs.class.getResourceAsStream(FONT_RESOURCE);
    if (stream == null) {
      throw new IllegalStateException("Missing map font resource " + FONT_RESOURCE);
    }

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String header = reader.readLine();
      if (header == null || !header.startsWith("h ")) {
        throw new IllegalStateException("Malformed map font header");
      }

      int height = Integer.parseInt(header.substring(2).trim());
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }

        String[] parts = line.trim().split(" ");
        char ch = (char) Integer.parseInt(parts[0], 16);
        int width = Integer.parseInt(parts[1]);
        long[] rows = new long[height];
        for (int row = 0; row < height; row++) {
          rows[row] = Long.parseLong(parts[2 + row], 16);
        }

        Glyph glyph = bake(width, height, rows);
        if (ch < ASCII_LIMIT) {
          ASCII[ch] = glyph;
        } else {
          WIDE.put(Character.valueOf(ch), glyph);
        }
      }
      return height;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Glyph bake(int glyphWidth, int height, long[] rows) {
    int[] runs = new int[height * ((glyphWidth / 2) + 1) * RUN_STRIDE];
    int count = 0;
    for (int row = 0; row < height; row++) {
      int runStart = -1;
      for (int column = 0; column <= glyphWidth; column++) {
        if (column < glyphWidth && ((rows[row] >>> column) & 1L) != 0L) {
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

  public record Glyph(int width, int advance, int[] runs) {
  }
}
