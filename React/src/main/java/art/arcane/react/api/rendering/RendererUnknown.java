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
  public void render() {
    for (int y = 0; y < height(); y++) {
      double n = y / (double) Math.max(1, height() - 1);
      TinyColor row = gradient(n, new TinyColor(12, 16, 20), new TinyColor(18, 26, 34));
      set(0, y, width(), 1, row);
    }

    dashHeader("React Map", null, new TinyColor(226, 102, 88));
    text(4, 16, "Renderer unavailable", TEXT_DIM);

    int cx = 26;
    int cy = 64;
    drawWarningGlyph(cx, cy);

    text(46, 48, "This map was", TEXT_BRIGHT);
    text(46, 58, "bound to a renderer", TEXT_BRIGHT);
    text(46, 68, "that is missing", TEXT_BRIGHT);
    text(46, 90, "Use /re map", TEXT_DIM);
    text(46, 100, "to reselect.", TEXT_DIM);
  }

  private void drawWarningGlyph(int cx, int cy) {
    TinyColor border = new TinyColor(238, 180, 80);
    TinyColor fill = new TinyColor(74, 52, 26);

    for (int y = -15; y <= 15; y++) {
      int halfWidth = Math.max(0, 15 - Math.abs(y));
      for (int x = -halfWidth; x <= halfWidth; x++) {
        int px = cx + x;
        int py = cy + y;
        if (Math.abs(x) == halfWidth || y == -15 || y == 15) {
          set(px, py, border);
        } else {
          set(px, py, fill);
        }
      }
    }

    set(cx, cy - 6, 1, 9, new TinyColor(255, 222, 120));
    set(cx, cy + 7, 1, 1, new TinyColor(255, 222, 120));
  }

  private TinyColor gradient(double normalized, TinyColor low, TinyColor high) {
    return new TinyColor(gradientRgb(normalized, low, high));
  }
}
