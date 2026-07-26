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
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class MapColors {
  private static final int CACHE_SIZE = 4096;
  private static final AtomicReferenceArray<ColorEntry> CACHE = new AtomicReferenceArray<>(CACHE_SIZE);

  private MapColors() {
  }

  public static Color colorFor(int rgb) {
    return entryFor(rgb).color();
  }

  public static byte indexFor(int rgb) {
    return entryFor(rgb).index();
  }

  public static int lerpRgb(double normalized, int lowRgb, int highRgb) {
    double n = normalized < 0D ? 0D : (normalized > 1D ? 1D : normalized);
    int r = (int) Math.round((((lowRgb >> 16) & 0xFF) * (1D - n)) + (((highRgb >> 16) & 0xFF) * n));
    int g = (int) Math.round((((lowRgb >> 8) & 0xFF) * (1D - n)) + (((highRgb >> 8) & 0xFF) * n));
    int b = (int) Math.round(((lowRgb & 0xFF) * (1D - n)) + ((highRgb & 0xFF) * n));
    return (r << 16) | (g << 8) | b;
  }

  public static TinyColor rampColor(double normalized, TinyColor[] ramp) {
    if (ramp == null || ramp.length == 0) {
      throw new IllegalArgumentException("Ramp must contain at least one color");
    }

    if (ramp.length == 1) {
      return ramp[0];
    }

    double n = normalized < 0D ? 0D : (normalized > 1D ? 1D : normalized);
    int index = (int) Math.round(n * (ramp.length - 1));
    return ramp[Math.max(0, Math.min(ramp.length - 1, index))];
  }

  @SuppressWarnings("removal")
  private static ColorEntry entryFor(int rgb) {
    int key = rgb & 0xFFFFFF;
    int slot = mix(key) & (CACHE_SIZE - 1);
    ColorEntry cached = CACHE.get(slot);
    if (cached != null && cached.rgb() == key) {
      return cached;
    }

    byte index = MapPalette.matchColor(new Color(key));
    ColorEntry entry = new ColorEntry(key, MapPalette.getColor(index), index);
    CACHE.set(slot, entry);
    return entry;
  }

  private static int mix(int key) {
    int h = key * 0x9E3779B1;
    return (h ^ (h >>> 16)) & 0x7FFFFFFF;
  }

  private record ColorEntry(int rgb, Color color, byte index) {
  }
}
