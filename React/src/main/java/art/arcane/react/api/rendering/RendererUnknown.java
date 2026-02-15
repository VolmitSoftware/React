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
        // Subtle vertical gradient background for the fallback card.
        for (int y = 0; y < height(); y++) {
            double n = y / (double) Math.max(1, height() - 1);
            TinyColor row = gradient(n, new TinyColor(12, 16, 20), new TinyColor(18, 26, 34));
            set(0, y, width(), 1, row);
        }

        set(0, 0, width(), 12, new TinyColor(126, 62, 56));
        text(4, 2, "React Map");
        text(4, 14, "Renderer unavailable");

        int cx = 26;
        int cy = 64;
        drawWarningGlyph(cx, cy);

        text(46, 48, "This map was");
        text(46, 58, "bound to a renderer");
        text(46, 68, "that is missing");
        text(46, 90, "Use /re map");
        text(46, 100, "to reselect.");
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
        double n = Math.max(0D, Math.min(1D, normalized));
        int r = (int) Math.round((low.getColor().getRed() * (1D - n)) + (high.getColor().getRed() * n));
        int g = (int) Math.round((low.getColor().getGreen() * (1D - n)) + (high.getColor().getGreen() * n));
        int b = (int) Math.round((low.getColor().getBlue() * (1D - n)) + (high.getColor().getBlue() * n));
        return new TinyColor(r, g, b);
    }
}
