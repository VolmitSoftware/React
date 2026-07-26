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
import org.bukkit.map.MapView;

import java.awt.Color;

public interface ReactRenderer {
  TinyColor TEXT_BRIGHT = MapTheme.TEXT;
  TinyColor TEXT_DIM = MapTheme.TEXT_MUTED;
  TinyColor HEADER_BAND = MapTheme.SURFACE_2;
  TinyColor FOOTER_BAND = MapTheme.SURFACE_1;
  int CANVAS_SIZE = 128;

  String getId();

  void render();

  default MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.magnify();
  }

  default MegamapGrid.MegamapDetail megamapDetail() {
    ReactRenderContext context = ReactRenderContext.of();
    return megamapCapability().detailFor(context.getGridWidth(), context.getGridHeight());
  }

  default int megamapColumns() {
    return megamapCapability().contentColumns(ReactRenderContext.of().getGridWidth());
  }

  default int megamapRows() {
    return megamapCapability().contentRows(ReactRenderContext.of().getGridHeight());
  }

  default int megamapRowCapacity(int rowHeight) {
    return bodyRegionOf(ReactRenderContext.of()).rowCapacity(Math.max(1, rowHeight));
  }

  default void drawMegamapNotice(String notice) {
    if (notice == null || notice.isBlank()) {
      return;
    }

    ReactRenderContext context = ReactRenderContext.of();
    int scale = uiScaleOf(context);
    int width = textWidthOf(context, notice);
    if (width <= 0) {
      return;
    }

    Region root = rootRegionOf(context);
    int chipWidth = Math.min(root.width(), width + (2 * MapTheme.pad(scale)));
    int chipHeight = (MapGlyphs.FONT_HEIGHT * scale) + (2 * scale);
    Region chip = new Region(
        root.right() - chipWidth - scale,
        root.bottom() - chipHeight - scale,
        chipWidth,
        chipHeight
    );
    if (!visibleIn(context, chip)) {
      return;
    }

    panel(chip, MapTheme.SURFACE_0, MapTheme.LINE_STRONG);
    textIn(chip, MapTheme.pad(scale), scale, notice, MapTheme.TEXT_MUTED);
  }

  default int uiScale() {
    return uiScaleOf(ReactRenderContext.of());
  }

  default int gridWidth() {
    return ReactRenderContext.of().getGridWidth();
  }

  default int gridHeight() {
    return ReactRenderContext.of().getGridHeight();
  }

  default int textHeight() {
    return MapGlyphs.FONT_HEIGHT * uiScaleOf(ReactRenderContext.of());
  }

  default int textWidth(String w) {
    return textWidthOf(ReactRenderContext.of(), w);
  }

  default void text(int x, int y, String text) {
    drawGlyphs(ReactRenderContext.of(), x, y, text, MapColors.indexFor(0x4F4F4F));
  }

  default void text(int x, int y, String text, TinyColor color) {
    drawGlyphs(ReactRenderContext.of(), x, y, text, MapColors.indexFor(color.toRGB()));
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
    byte index = MapColors.indexFor(color.toRGB());
    ReactRenderContext context = ReactRenderContext.of();

    for (int i = 0; i <= steps; i++) {
      blitRect(context, x, y, 1, 1, index);
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
    blitRect(ReactRenderContext.of(), x, y, 1, 1, MapColors.indexFor(rgb));
  }

  default void set(int x, int y, int rgb) {
    setRgb(x, y, rgb);
  }

  default void set(int x, int y, Color color) {
    setRgb(x, y, color.getRGB());
  }

  default void set(int x, int y, TinyColor color) {
    setRgb(x, y, color.toRGB());
  }

  default void set(int x, int y, org.bukkit.Color color) {
    setRgb(x, y, color.asRGB());
  }

  default void textNear(int x, int y, String text) {
    ReactRenderContext context = ReactRenderContext.of();
    int scale = uiScaleOf(context);
    drawGlyphs(
        context,
        x - textWidthOf(context, text),
        y - (MapGlyphs.FONT_HEIGHT * scale),
        text,
        MapColors.indexFor(0x4F4F4F)
    );
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
    return clipX0Of(ReactRenderContext.of());
  }

  default int clipX1() {
    return clipX1Of(ReactRenderContext.of());
  }

  default int clipY0() {
    return clipY0Of(ReactRenderContext.of());
  }

  default int clipY1() {
    return clipY1Of(ReactRenderContext.of());
  }

  default Region rootRegion() {
    return rootRegionOf(ReactRenderContext.of());
  }

  default Region tileRegion() {
    return tileRegionOf(ReactRenderContext.of());
  }

  default Region clipRegion() {
    return clipRegionOf(ReactRenderContext.of());
  }

  default Region headerRegion() {
    ReactRenderContext context = ReactRenderContext.of();
    return rootRegionOf(context).topBand(MapTheme.headerHeight(uiScaleOf(context)));
  }

  default Region footerRegion() {
    ReactRenderContext context = ReactRenderContext.of();
    return rootRegionOf(context).bottomBand(MapTheme.footerHeight(uiScaleOf(context)));
  }

  default Region bodyRegion() {
    return bodyRegionOf(ReactRenderContext.of());
  }

  default void pushClip(Region region) {
    ReactRenderContext.of().pushClip(region.x(), region.y(), region.width(), region.height());
  }

  default void popClip() {
    ReactRenderContext.of().popClip();
  }

  default boolean visible(Region region) {
    return visibleIn(ReactRenderContext.of(), region);
  }

  default void set(int x, int y, int w, int h, TinyColor c) {
    blitRect(ReactRenderContext.of(), x, y, w, h, MapColors.indexFor(c.toRGB()));
  }

  default void fillRgb(int x, int y, int w, int h, int rgb) {
    blitRect(ReactRenderContext.of(), x, y, w, h, MapColors.indexFor(rgb));
  }

  default void fill(Region region, TinyColor color) {
    blitRect(
        ReactRenderContext.of(),
        region.x(),
        region.y(),
        region.width(),
        region.height(),
        MapColors.indexFor(color.toRGB())
    );
  }

  default void border(Region region, TinyColor color) {
    ReactRenderContext context = ReactRenderContext.of();
    borderIn(context, region, color, MapTheme.borderThickness(uiScaleOf(context)));
  }

  default void border(Region region, TinyColor color, int thickness) {
    borderIn(ReactRenderContext.of(), region, color, thickness);
  }

  default void panel(Region region, TinyColor background, TinyColor outline) {
    if (region.isEmpty()) {
      return;
    }

    ReactRenderContext context = ReactRenderContext.of();
    if (background != null) {
      blitRect(
          context,
          region.x(),
          region.y(),
          region.width(),
          region.height(),
          MapColors.indexFor(background.toRGB())
      );
    }

    if (outline != null) {
      borderIn(context, region, outline, MapTheme.borderThickness(uiScaleOf(context)));
    }
  }

  default void hSeparator(Region region, int offsetY, TinyColor color) {
    ReactRenderContext context = ReactRenderContext.of();
    blitRect(
        context,
        region.x(),
        region.y() + offsetY,
        region.width(),
        MapTheme.borderThickness(uiScaleOf(context)),
        MapColors.indexFor(color.toRGB())
    );
  }

  default void vSeparator(Region region, int offsetX, TinyColor color) {
    ReactRenderContext context = ReactRenderContext.of();
    blitRect(
        context,
        region.x() + offsetX,
        region.y(),
        MapTheme.borderThickness(uiScaleOf(context)),
        region.height(),
        MapColors.indexFor(color.toRGB())
    );
  }

  default void textIn(Region region, int dx, int dy, String text, TinyColor color) {
    if (region == null || region.isEmpty() || text == null || text.isEmpty()) {
      return;
    }

    ReactRenderContext context = ReactRenderContext.of();
    context.pushClip(region.x(), region.y(), region.width(), region.height());
    try {
      String fitted = fitTextIn(context, text, region.width() - dx);
      if (!fitted.isEmpty()) {
        drawGlyphs(context, region.x() + dx, region.y() + dy, fitted, MapColors.indexFor(color.toRGB()));
      }
    } finally {
      context.popClip();
    }
  }

  default void textRightIn(Region region, int inset, int dy, String text, TinyColor color) {
    if (region == null || region.isEmpty() || text == null || text.isEmpty()) {
      return;
    }

    ReactRenderContext context = ReactRenderContext.of();
    context.pushClip(region.x(), region.y(), region.width(), region.height());
    try {
      String fitted = fitTextIn(context, text, region.width() - (2 * inset));
      if (!fitted.isEmpty()) {
        int x = region.right() - inset - textWidthOf(context, fitted);
        drawGlyphs(context, Math.max(region.x() + inset, x), region.y() + dy, fitted, MapColors.indexFor(color.toRGB()));
      }
    } finally {
      context.popClip();
    }
  }

  default void textCenteredIn(Region region, int dy, String text, TinyColor color) {
    if (region == null || region.isEmpty() || text == null || text.isEmpty()) {
      return;
    }

    ReactRenderContext context = ReactRenderContext.of();
    context.pushClip(region.x(), region.y(), region.width(), region.height());
    try {
      String fitted = fitTextIn(context, text, region.width());
      if (!fitted.isEmpty()) {
        int x = region.x() + ((region.width() - textWidthOf(context, fitted)) / 2);
        drawGlyphs(context, Math.max(region.x(), x), region.y() + dy, fitted, MapColors.indexFor(color.toRGB()));
      }
    } finally {
      context.popClip();
    }
  }

  default void clear(TinyColor color) {
    ReactRenderContext context = ReactRenderContext.of();
    blitRect(context, 0, 0, context.getWidth(), context.getHeight(), MapColors.indexFor(color.toRGB()));
  }

  default int gradientRgb(double normalized, TinyColor low, TinyColor high) {
    return MapColors.lerpRgb(normalized, low.toRGB(), high.toRGB());
  }

  default void dashHeader(String title, String value, TinyColor accent, TinyColor valueColor) {
    ReactRenderContext context = ReactRenderContext.of();
    int w = context.getWidth();
    int s = uiScaleOf(context);
    blitRect(context, 0, 0, w, 13 * s, MapColors.indexFor(HEADER_BAND.toRGB()));
    blitRect(context, 0, 0, 2 * s, 13 * s, MapColors.indexFor(accent.toRGB()));

    int titleLimit = w - (8 * s);
    if (value != null && !value.isBlank()) {
      int valueX = w - (3 * s) - textWidthOf(context, value);
      TinyColor resolved = valueColor == null ? accent : valueColor;
      drawGlyphs(context, valueX, 3 * s, value, MapColors.indexFor(resolved.toRGB()));
      titleLimit = valueX - (9 * s);
    }

    if (title != null && !title.isBlank()) {
      String fitted = fitTextIn(context, title, titleLimit);
      if (!fitted.isBlank()) {
        drawGlyphs(context, 5 * s, 3 * s, fitted, MapColors.indexFor(TEXT_BRIGHT.toRGB()));
      }
    }

    blitRect(context, 0, 13 * s, w, s, MapColors.indexFor(accent.toRGB()));
  }

  default void dashHeader(String title, String value, TinyColor accent) {
    dashHeader(title, value, accent, null);
  }

  default String fitText(String text, int maxWidth) {
    return fitTextIn(ReactRenderContext.of(), text, maxWidth);
  }

  private static int uiScaleOf(ReactRenderContext context) {
    return Math.max(1, context.getTextScale());
  }

  private static int textWidthOf(ReactRenderContext context, String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }

    return MapGlyphs.runWidth(text) * uiScaleOf(context);
  }

  private static String fitTextIn(ReactRenderContext context, String text, int maxWidth) {
    if (text == null || text.isBlank() || maxWidth <= 0) {
      return "";
    }

    int scale = uiScaleOf(context);
    if (MapGlyphs.runWidth(text) * scale <= maxWidth) {
      return text;
    }

    int dotAdvance = MapGlyphs.advance('.');
    if (dotAdvance <= 0) {
      return "";
    }

    int advance = 0;
    int fitEnd = 0;
    for (int i = 0; i < text.length(); i++) {
      advance += MapGlyphs.advance(text.charAt(i));
      if (((advance + dotAdvance - 1) * scale) > maxWidth) {
        break;
      }
      fitEnd = i + 1;
    }

    if (fitEnd < 2) {
      return "";
    }

    return text.substring(0, fitEnd).stripTrailing() + ".";
  }

  private static Region rootRegionOf(ReactRenderContext context) {
    return new Region(0, 0, context.getWidth(), context.getHeight());
  }

  private static Region tileRegionOf(ReactRenderContext context) {
    int x0 = clipX0Of(context);
    int y0 = clipY0Of(context);
    return new Region(x0, y0, clipX1Of(context) - x0, clipY1Of(context) - y0);
  }

  private static Region clipRegionOf(ReactRenderContext context) {
    return new Region(
        context.getClipX0(),
        context.getClipY0(),
        context.getClipX1() - context.getClipX0(),
        context.getClipY1() - context.getClipY0()
    );
  }

  private static Region bodyRegionOf(ReactRenderContext context) {
    int scale = uiScaleOf(context);
    return rootRegionOf(context).withoutTop(MapTheme.headerHeight(scale)).withoutBottom(MapTheme.footerHeight(scale));
  }

  private static int clipX0Of(ReactRenderContext context) {
    return Math.max(0, context.getOffsetX() / context.getScaleX());
  }

  private static int clipX1Of(ReactRenderContext context) {
    return Math.min(context.getWidth(), Math.ceilDiv(context.getOffsetX() + CANVAS_SIZE, context.getScaleX()));
  }

  private static int clipY0Of(ReactRenderContext context) {
    return Math.max(0, context.getOffsetY() / context.getScaleY());
  }

  private static int clipY1Of(ReactRenderContext context) {
    return Math.min(context.getHeight(), Math.ceilDiv(context.getOffsetY() + CANVAS_SIZE, context.getScaleY()));
  }

  private static boolean visibleIn(ReactRenderContext context, Region region) {
    if (region == null || region.isEmpty()) {
      return false;
    }

    return region.intersects(clipRegionOf(context)) && region.intersects(tileRegionOf(context));
  }

  private static void borderIn(ReactRenderContext context, Region region, TinyColor color, int thickness) {
    if (region.isEmpty() || thickness <= 0) {
      return;
    }

    byte index = MapColors.indexFor(color.toRGB());
    int capped = Math.min(thickness, Math.min(region.width(), region.height()));
    blitRect(context, region.x(), region.y(), region.width(), capped, index);
    blitRect(context, region.x(), region.bottom() - capped, region.width(), capped, index);
    blitRect(context, region.x(), region.y() + capped, capped, region.height() - (2 * capped), index);
    blitRect(context, region.right() - capped, region.y() + capped, capped, region.height() - (2 * capped), index);
  }

  private static void drawGlyphs(ReactRenderContext context, int x, int y, String text, byte paletteIndex) {
    if (text == null || text.isEmpty()) {
      return;
    }

    int scale = uiScaleOf(context);
    int fontHeight = MapGlyphs.FONT_HEIGHT;
    int clipX0 = context.getClipX0();
    int clipX1 = context.getClipX1();
    int clipY0 = context.getClipY0();
    int clipY1 = context.getClipY1();
    int cursorX = x;
    int cursorY = y;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '\n') {
        cursorX = x;
        cursorY += (fontHeight + 1) * scale;
        continue;
      }

      MapGlyphs.Glyph glyph = MapGlyphs.glyph(ch);
      int advance = glyph.advance() * scale;
      if (advance <= 0) {
        continue;
      }

      if (cursorX >= clipX1 || cursorX + advance <= clipX0
          || cursorY >= clipY1 || cursorY + (fontHeight * scale) <= clipY0) {
        cursorX += advance;
        continue;
      }

      int[] runs = glyph.runs();
      for (int run = 0; run < runs.length; run += MapGlyphs.RUN_STRIDE) {
        blitRect(
            context,
            cursorX + (runs[run + 1] * scale),
            cursorY + (runs[run] * scale),
            runs[run + 2] * scale,
            scale,
            paletteIndex
        );
      }

      cursorX += advance;
    }
  }

  private static void blitRect(ReactRenderContext context, int x, int y, int w, int h, byte paletteIndex) {
    int lx0 = Math.max(x, context.getClipX0());
    int ly0 = Math.max(y, context.getClipY0());
    int lx1 = Math.min(x + w, context.getClipX1());
    int ly1 = Math.min(y + h, context.getClipY1());
    if (lx0 >= lx1 || ly0 >= ly1) {
      return;
    }

    int scaleX = context.getScaleX();
    int scaleY = context.getScaleY();
    int offsetX = context.getOffsetX();
    int offsetY = context.getOffsetY();
    context.paintCanvasRect(
        (lx0 * scaleX) - offsetX,
        (ly0 * scaleY) - offsetY,
        (lx1 * scaleX) - offsetX,
        (ly1 * scaleY) - offsetY,
        paletteIndex
    );
  }
}
