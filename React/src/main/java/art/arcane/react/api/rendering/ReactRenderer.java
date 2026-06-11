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
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;

import java.awt.*;

public interface ReactRenderer {
  TinyColor TEXT_BRIGHT = new TinyColor(228, 236, 244);
  TinyColor TEXT_DIM = new TinyColor(128, 140, 152);
  TinyColor HEADER_BAND = new TinyColor(14, 18, 24);
  TinyColor FOOTER_BAND = new TinyColor(10, 13, 18);

  default void render(MapView map, MapCanvas canvas, Player player) {
    ReactRenderContext.push(ReactRenderContext.builder()
        .view(map).canvas(canvas).player(player)
        .width(128).height(128)
        .build());
    render();
  }

  String getId();

  void render();

  default int textHeight() {
    return MinecraftFont.Font.getHeight();
  }

  default int textWidth(String w) {
    return MinecraftFont.Font.getWidth(w);
  }

  default void text(int x, int y, String text) {
    canvas().drawText(x, y, MinecraftFont.Font, text);
  }

  default void text(int x, int y, String text, TinyColor color) {
    canvas().drawText(x, y, MinecraftFont.Font, MapColors.textPrefix(color) + text);
  }

  @SuppressWarnings("deprecation")
  default void line(int x1, int y1, int x2, int y2, TinyColor color) {
    int dx = Math.abs(x2 - x1);
    int dy = Math.abs(y2 - y1);
    int sx = x1 < x2 ? 1 : -1;
    int sy = y1 < y2 ? 1 : -1;
    int err = dx - dy;
    int steps = Math.max(dx, dy);
    int x = x1;
    int y = y1;
    byte paletteByte = MapColors.byteFor(color.toRGB());
    MapCanvas canvas = canvas();

    for (int i = 0; i <= steps; i++) {
      if (x < 0 || y < 0 || x >= width() || y >= height()) {
        break;
      }

      if (i > 256) {
        break;
      }

      canvas.setPixel(x, y, paletteByte);
      int e2 = 2 * err;

      if (e2 > -dy) {
        err -= dy;
        x += sx;
      }

      if (e2 < dx) {
        err += dx;
        y += sy;
      }

      if (x == x2 && y == y2) {
        break;
      }
    }
  }

  @SuppressWarnings("deprecation")
  default void setRgb(int x, int y, int rgb) {
    canvas().setPixel(x, y, MapColors.byteFor(rgb));
  }

  default void set(int x, int y, int rgb) {
    setRgb(x, y, rgb);
  }

  default void set(int x, int y, Color color) {
    setRgb(x, y, color.getRGB());
  }

  default int x(int x) {
    return Math.max(4, Math.min(width() - 1, x - 4));
  }

  default int y(int y) {
    return Math.max(4, Math.min(height() - 1, y - 4));
  }

  default void textNear(int x, int y, String text) {
    text(x(x - textWidth(text)), y(y - textHeight()), text);
  }

  default void set(int x, int y, TinyColor color) {
    setRgb(x, y, color.toRGB());
  }

  default void set(int x, int y, org.bukkit.Color color) {
    setRgb(x, y, color.asRGB());
  }

  default MapCanvas canvas() {
    return ReactRenderContext.of().getCanvas();
  }

  default MapView view() {
    return ReactRenderContext.of().getView();
  }

  default Player player() {
    return ReactRenderContext.of().getPlayer();
  }

  default int width() {
    return ReactRenderContext.of().getWidth();
  }

  default int height() {
    return ReactRenderContext.of().getHeight();
  }

  default void set(int x, int y, int w, int h, TinyColor c) {
    fillRgb(x, y, w, h, c.toRGB());
  }

  @SuppressWarnings("deprecation")
  default void fillRgb(int x, int y, int w, int h, int rgb) {
    byte paletteByte = MapColors.byteFor(rgb);
    MapCanvas canvas = canvas();
    for (int i = x; i < x + w; i++) {
      for (int j = y; j < y + h; j++) {
        canvas.setPixel(i, j, paletteByte);
      }
    }
  }

  default void clear(TinyColor color) {
    fillRgb(0, 0, width(), height(), color.toRGB());
  }

  default int gradientRgb(double normalized, TinyColor low, TinyColor high) {
    return MapColors.lerpRgb(normalized, low.toRGB(), high.toRGB());
  }

  default void dashHeader(String title, String value, TinyColor accent, TinyColor valueColor) {
    int w = width();
    set(0, 0, w, 13, HEADER_BAND);
    set(0, 0, 2, 13, accent);

    int titleLimit = w - 8;
    if (value != null && !value.isBlank()) {
      int valueX = w - 3 - textWidth(value);
      text(valueX, 3, value, valueColor == null ? accent : valueColor);
      titleLimit = valueX - 9;
    }

    if (title != null && !title.isBlank()) {
      String fitted = fitText(title, titleLimit);
      if (!fitted.isBlank()) {
        text(5, 3, fitted, TEXT_BRIGHT);
      }
    }

    set(0, 13, w, 1, accent);
  }

  default void dashHeader(String title, String value, TinyColor accent) {
    dashHeader(title, value, accent, null);
  }

  default String fitText(String text, int maxWidth) {
    if (text == null || text.isBlank() || maxWidth <= 0) {
      return "";
    }

    if (textWidth(text) <= maxWidth) {
      return text;
    }

    int end = text.length();
    while (end > 1) {
      String candidate = text.substring(0, end).stripTrailing() + ".";
      if (textWidth(candidate) <= maxWidth) {
        return candidate;
      }
      end--;
    }

    return "";
  }
}
