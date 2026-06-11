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

import art.arcane.react.util.data.TinyColor;
import org.bukkit.map.MapPalette;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicLongArray;

public final class MapColors {
  private static final int CACHE_SIZE = 4096;
  private static final long PRESENT = 1L << 40;
  private static final AtomicLongArray CACHE = new AtomicLongArray(CACHE_SIZE);
  private static final String[] TEXT_PREFIXES = new String[256];

  private MapColors() {
  }

  @SuppressWarnings("deprecation")
  public static byte byteFor(int rgb) {
    int key = rgb & 0xFFFFFF;
    int slot = mix(key) & (CACHE_SIZE - 1);
    long entry = CACHE.get(slot);
    if ((entry & PRESENT) != 0L && ((int) (entry >>> 8) & 0xFFFFFF) == key) {
      return (byte) entry;
    }

    byte matched = MapPalette.matchColor(new Color(key));
    CACHE.set(slot, PRESENT | (((long) key) << 8) | (matched & 0xFFL));
    return matched;
  }

  public static String textPrefix(TinyColor color) {
    byte paletteByte = byteFor(color.toRGB());
    int index = paletteByte & 0xFF;
    String prefix = TEXT_PREFIXES[index];
    if (prefix == null) {
      prefix = "§" + paletteByte + ";";
      TEXT_PREFIXES[index] = prefix;
    }

    return prefix;
  }

  public static int lerpRgb(double normalized, int lowRgb, int highRgb) {
    double n = normalized < 0D ? 0D : (normalized > 1D ? 1D : normalized);
    int r = (int) Math.round((((lowRgb >> 16) & 0xFF) * (1D - n)) + (((highRgb >> 16) & 0xFF) * n));
    int g = (int) Math.round((((lowRgb >> 8) & 0xFF) * (1D - n)) + (((highRgb >> 8) & 0xFF) * n));
    int b = (int) Math.round(((lowRgb & 0xFF) * (1D - n)) + ((highRgb & 0xFF) * n));
    return (r << 16) | (g << 8) | b;
  }

  private static int mix(int key) {
    int h = key * 0x9E3779B1;
    return (h ^ (h >>> 16)) & 0x7FFFFFFF;
  }
}
