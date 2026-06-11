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
import org.bukkit.map.MapFont;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;

import java.awt.*;

public interface ReactRenderer {
  TinyColor TEXT_BRIGHT = new TinyColor(228, 236, 244);
  TinyColor TEXT_DIM = new TinyColor(128, 140, 152);
  TinyColor HEADER_BAND = new TinyColor(14, 18, 24);
  TinyColor FOOTER_BAND = new TinyColor(10, 13, 18);
  int CANVAS_SIZE = 128;

  String getId();

  void render();

  default boolean rendersNativeMegamap() {
    return false;
  }

  default int uiScale() {
    return Math.max(1, ReactRenderContext.of().getTextScale());
  }

  default int textHeight() {
    return MinecraftFont.Font.getHeight() * uiScale();
  }

  default int textWidth(String w) {
    return MinecraftFont.Font.getWidth(w) * uiScale();
  }

  default void text(int x, int y, String text) {
    drawGlyphs(x, y, text, defaultTextByte());
  }

  default void text(int x, int y, String text, TinyColor color) {
    drawGlyphs(x, y, text, MapColors.byteFor(color.toRGB()));
  }

  @SuppressWarnings("deprecation")
  private byte defaultTextByte() {
    return MapPalette.DARK_GRAY;
  }

  private void drawGlyphs(int x, int y, String text, byte paletteByte) {
    if (text == null || text.isEmpty()) {
      return;
    }

    ReactRenderContext context = ReactRenderContext.of();
    int scale = Math.max(1, context.getTextScale());
    int fontHeight = MinecraftFont.Font.getHeight();
    int cursorX = x;
    int cursorY = y;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '\n') {
        cursorX = x;
        cursorY += (fontHeight + 1) * scale;
        continue;
      }

      MapFont.CharacterSprite sprite = MinecraftFont.Font.getChar(ch);
      if (sprite == null) {
        continue;
      }

      int glyphWidth = sprite.getWidth();
      for (int row = 0; row < fontHeight; row++) {
        for (int col = 0; col < glyphWidth; col++) {
          if (sprite.get(row, col)) {
            blitRect(context, cursorX + (col * scale), cursorY + (row * scale), scale, scale, paletteByte);
          }
        }
      }

      cursorX += (glyphWidth + 1) * scale;
    }
  }

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
    ReactRenderContext context = ReactRenderContext.of();
    int width = context.getWidth();
    int height = context.getHeight();

    for (int i = 0; i <= steps; i++) {
      if (x < 0 || y < 0 || x >= width || y >= height) {
        break;
      }

      if (i > 4096) {
        break;
      }

      blitRect(context, x, y, 1, 1, paletteByte);
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

  default void setRgb(int x, int y, int rgb) {
    blitRect(ReactRenderContext.of(), x, y, 1, 1, MapColors.byteFor(rgb));
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

  default int clipX0() {
    ReactRenderContext context = ReactRenderContext.of();
    return Math.max(0, context.getOffsetX() / Math.max(1, context.getScaleX()));
  }

  default int clipX1() {
    ReactRenderContext context = ReactRenderContext.of();
    return Math.min(context.getWidth(), Math.ceilDiv(context.getOffsetX() + CANVAS_SIZE, Math.max(1, context.getScaleX())));
  }

  default int clipY0() {
    ReactRenderContext context = ReactRenderContext.of();
    return Math.max(0, context.getOffsetY() / Math.max(1, context.getScaleY()));
  }

  default int clipY1() {
    ReactRenderContext context = ReactRenderContext.of();
    return Math.min(context.getHeight(), Math.ceilDiv(context.getOffsetY() + CANVAS_SIZE, Math.max(1, context.getScaleY())));
  }

  default void set(int x, int y, int w, int h, TinyColor c) {
    fillRgb(x, y, w, h, c.toRGB());
  }

  default void fillRgb(int x, int y, int w, int h, int rgb) {
    blitRect(ReactRenderContext.of(), x, y, w, h, MapColors.byteFor(rgb));
  }

  @SuppressWarnings("deprecation")
  private void blitRect(ReactRenderContext context, int x, int y, int w, int h, byte paletteByte) {
    int scaleX = context.getScaleX();
    int scaleY = context.getScaleY();
    int x0 = Math.max(0, (x * scaleX) - context.getOffsetX());
    int y0 = Math.max(0, (y * scaleY) - context.getOffsetY());
    int x1 = Math.min(CANVAS_SIZE, ((x + w) * scaleX) - context.getOffsetX());
    int y1 = Math.min(CANVAS_SIZE, ((y + h) * scaleY) - context.getOffsetY());
    if (x0 >= x1 || y0 >= y1) {
      return;
    }

    MapCanvas canvas = context.getCanvas();
    for (int i = x0; i < x1; i++) {
      for (int j = y0; j < y1; j++) {
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
    int s = uiScale();
    set(0, 0, w, 13 * s, HEADER_BAND);
    set(0, 0, 2 * s, 13 * s, accent);

    int titleLimit = w - (8 * s);
    if (value != null && !value.isBlank()) {
      int valueX = w - (3 * s) - textWidth(value);
      text(valueX, 3 * s, value, valueColor == null ? accent : valueColor);
      titleLimit = valueX - (9 * s);
    }

    if (title != null && !title.isBlank()) {
      String fitted = fitText(title, titleLimit);
      if (!fitted.isBlank()) {
        text(5 * s, 3 * s, fitted, TEXT_BRIGHT);
      }
    }

    set(0, 13 * s, w, s, accent);
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
