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

public class RendererUnknown implements ReactRenderer {
  public static final String ID = "unknown";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean rendersNativeMegamap() {
    return true;
  }

  @Override
  public void render() {
    int h = height();
    int s = uiScale();
    int bgY0 = Math.max(0, clipY0());
    int bgY1 = Math.min(h, clipY1());
    for (int y = bgY0; y < bgY1; y++) {
      double n = y / (double) Math.max(1, h - 1);
      TinyColor row = gradient(n, new TinyColor(12, 16, 20), new TinyColor(18, 26, 34));
      set(0, y, width(), 1, row);
    }

    dashHeader("React Map", null, new TinyColor(226, 102, 88));
    text(4 * s, 16 * s, "Renderer unavailable", TEXT_DIM);

    int cx = 26 * s;
    int cy = 64 * s;
    drawWarningGlyph(cx, cy, s);

    text(46 * s, 48 * s, "This map was", TEXT_BRIGHT);
    text(46 * s, 58 * s, "bound to a renderer", TEXT_BRIGHT);
    text(46 * s, 68 * s, "that is missing", TEXT_BRIGHT);
    text(46 * s, 90 * s, "Use /re map", TEXT_DIM);
    text(46 * s, 100 * s, "to reselect.", TEXT_DIM);
  }

  private void drawWarningGlyph(int cx, int cy, int s) {
    TinyColor border = new TinyColor(238, 180, 80);
    TinyColor fill = new TinyColor(74, 52, 26);

    for (int y = -15; y <= 15; y++) {
      int halfWidth = Math.max(0, 15 - Math.abs(y));
      for (int x = -halfWidth; x <= halfWidth; x++) {
        int px = cx + (x * s);
        int py = cy + (y * s);
        if (Math.abs(x) == halfWidth || y == -15 || y == 15) {
          set(px, py, s, s, border);
        } else {
          set(px, py, s, s, fill);
        }
      }
    }

    set(cx, cy - (6 * s), s, 9 * s, new TinyColor(255, 222, 120));
    set(cx, cy + (7 * s), s, s, new TinyColor(255, 222, 120));
  }

  private TinyColor gradient(double normalized, TinyColor low, TinyColor high) {
    return new TinyColor(gradientRgb(normalized, low, high));
  }
}
