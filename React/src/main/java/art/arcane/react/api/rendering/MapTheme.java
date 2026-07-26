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
import org.bukkit.map.MinecraftFont;

public final class MapTheme {
  public static final TinyColor SURFACE_0 = new TinyColor(0x0D0D0D);
  public static final TinyColor SURFACE_1 = new TinyColor(0x111111);
  public static final TinyColor SURFACE_2 = new TinyColor(0x151515);
  public static final TinyColor SURFACE_3 = new TinyColor(0x191919);
  public static final TinyColor TRACK = new TinyColor(0x353535);
  public static final TinyColor LINE = new TinyColor(0x282828);
  public static final TinyColor LINE_STRONG = new TinyColor(0x3B3B3B);
  public static final TinyColor TEXT_MUTED = new TinyColor(0x8D909E);
  public static final TinyColor TEXT_SOFT = new TinyColor(0xC7C7C7);
  public static final TinyColor TEXT = new TinyColor(0xDCDCDC);
  public static final TinyColor TEXT_STRONG = new TinyColor(0xFFFFFF);
  public static final TinyColor OK = new TinyColor(0x7FB238);
  public static final TinyColor WARN = new TinyColor(0xD87F33);
  public static final TinyColor BAD = new TinyColor(0xB40000);
  public static final TinyColor INFO = new TinyColor(0x6699D8);
  public static final TinyColor ACCENT_VIOLET = new TinyColor(0x7F3FB2);
  public static final TinyColor ACCENT_MAGENTA = new TinyColor(0xB24CD8);
  public static final TinyColor ACCENT_TEAL = new TinyColor(0x4FBCB7);
  public static final TinyColor ACCENT_PINK = new TinyColor(0xF27FA5);
  public static final TinyColor ACCENT_YELLOW = new TinyColor(0xE5E533);
  public static final TinyColor HEAT_COLD = new TinyColor(0x2C4199);
  public static final TinyColor HEAT_COOL = new TinyColor(0x4A80FF);
  public static final TinyColor HEAT_LIME = new TinyColor(0x7FCC19);
  public static final TinyColor HEAT_HOT = new TinyColor(0xFF0000);

  public static final TinyColor[] TOKENS = {
      SURFACE_0, SURFACE_1, SURFACE_2, SURFACE_3, TRACK, LINE, LINE_STRONG,
      TEXT_MUTED, TEXT_SOFT, TEXT, TEXT_STRONG, OK, WARN, BAD, INFO,
      ACCENT_VIOLET, ACCENT_MAGENTA, ACCENT_TEAL, ACCENT_PINK, ACCENT_YELLOW,
      HEAT_COLD, HEAT_COOL, HEAT_LIME, HEAT_HOT
  };

  public static final TinyColor[] HEAT_RAMP = {
      HEAT_COLD, HEAT_COOL, ACCENT_TEAL, HEAT_LIME, ACCENT_YELLOW, WARN, HEAT_HOT
  };

  public static final TinyColor[] CATEGORICAL = {
      INFO, OK, WARN, ACCENT_MAGENTA, ACCENT_TEAL, ACCENT_PINK, ACCENT_YELLOW, BAD
  };

  private MapTheme() {
  }

  public static int headerHeight(int uiScale) {
    return 14 * Math.max(1, uiScale);
  }

  public static int footerHeight(int uiScale) {
    return 12 * Math.max(1, uiScale);
  }

  public static int lineHeight(int uiScale) {
    return (MinecraftFont.Font.getHeight() + 1) * Math.max(1, uiScale);
  }

  public static int rowHeight(int uiScale) {
    return (2 * lineHeight(uiScale)) + (2 * Math.max(1, uiScale));
  }

  public static int gutter(int uiScale) {
    return 2 * Math.max(1, uiScale);
  }

  public static int pad(int uiScale) {
    return 3 * Math.max(1, uiScale);
  }

  public static int borderThickness(int uiScale) {
    return Math.max(1, uiScale);
  }

  public static TinyColor categorical(int index) {
    return CATEGORICAL[Math.floorMod(index, CATEGORICAL.length)];
  }

  public static TinyColor heat(double normalized) {
    return MapColors.rampColor(normalized, HEAT_RAMP);
  }

  public static TinyColor severity(double normalized) {
    if (normalized >= 0.75D) {
      return BAD;
    }
    if (normalized >= 0.45D) {
      return WARN;
    }
    return OK;
  }
}
