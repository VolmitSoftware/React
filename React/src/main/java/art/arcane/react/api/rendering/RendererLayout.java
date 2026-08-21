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
import art.arcane.volmlib.util.format.Form;

public final class RendererLayout {
  private RendererLayout() {
  }

  public static Region body(ReactRenderer renderer) {
    return renderer.bodyRegion().inset(MapTheme.gutter(renderer.uiScale()));
  }

  public static void backdrop(ReactRenderer renderer, TinyColor base, TinyColor bodyTone) {
    renderer.fill(renderer.rootRegion(), base);
    if (bodyTone != null) {
      renderer.fill(renderer.bodyRegion(), bodyTone);
    }
  }

  public static void footer(ReactRenderer renderer, String left, String right, TinyColor accent) {
    Region footer = renderer.footerRegion();
    if (footer.isEmpty()) {
      return;
    }

    renderer.fill(footer, MapTheme.SURFACE_2);
    renderer.hSeparator(footer, 0, accent == null ? MapTheme.LINE : accent);

    Region content = footerContent(renderer);
    if (content.isEmpty()) {
      return;
    }

    int baseline = baseline(renderer, content);
    if (right != null && !right.isBlank()) {
      renderer.textRightIn(content, 0, baseline, right, MapTheme.TEXT_SOFT);
    }

    if (left != null && !left.isBlank()) {
      int reserved = right == null || right.isBlank() ? 0 : renderer.textWidth(right) + MapTheme.gutter(renderer.uiScale());
      renderer.textIn(content.withoutRight(reserved), 0, baseline, left, MapTheme.TEXT_MUTED);
    }
  }

  public static Region footerContent(ReactRenderer renderer) {
    int scale = renderer.uiScale();
    return renderer.footerRegion()
        .withoutTop(MapTheme.borderThickness(scale))
        .inset(MapTheme.pad(scale), 0, MapTheme.pad(scale), 0);
  }

  public static int baseline(ReactRenderer renderer, Region region) {
    return Math.max(0, (region.height() - renderer.textHeight()) / 2);
  }

  public static void card(ReactRenderer renderer, Region region, boolean emphasis) {
    renderer.panel(
        region,
        emphasis ? MapTheme.SURFACE_3 : MapTheme.SURFACE_2,
        emphasis ? MapTheme.LINE_STRONG : MapTheme.LINE
    );
  }

  public static void bar(ReactRenderer renderer, Region track, double normalized, TinyColor color) {
    if (track.isEmpty()) {
      return;
    }

    renderer.fill(track, MapTheme.TRACK);
    double clamped = normalized < 0D ? 0D : (normalized > 1D ? 1D : normalized);
    int fill = (int) Math.round(track.width() * clamped);
    if (fill <= 0) {
      return;
    }

    renderer.fill(new Region(track.x(), track.y(), Math.min(track.width(), fill), track.height()), color);
  }

  public static void listRow(
      ReactRenderer renderer,
      int rank,
      Region region,
      int[] weights,
      int gutter,
      double normalized,
      String[] values
  ) {
    if (region.isEmpty()) {
      return;
    }

    int scale = renderer.uiScale();
    card(renderer, region, rank < 3);

    Region inner = region.inset(MapTheme.pad(scale), MapTheme.borderThickness(scale) + scale, MapTheme.pad(scale), MapTheme.borderThickness(scale) + scale);
    if (inner.isEmpty()) {
      return;
    }

    Region textLine = inner.topBand(MapTheme.lineHeight(scale));
    Region[] columns = textLine.splitColumns(gutter, weights);
    for (int i = 0; i < columns.length && i < values.length; i++) {
      TinyColor color = i == 0 ? MapTheme.TEXT : MapTheme.TEXT_SOFT;
      if (i == 0) {
        renderer.textIn(columns[i], 0, 0, values[i], color);
      } else {
        renderer.textRightIn(columns[i], 0, 0, values[i], color);
      }
    }

    Region barRow = inner.withoutTop(textLine.height() + scale);
    if (barRow.isEmpty()) {
      return;
    }

    bar(
        renderer,
        barRow.topBand(Math.min(barRow.height(), barHeight(renderer))),
        normalized,
        MapTheme.categorical(rank)
    );
  }

  public static int listRowHeight(ReactRenderer renderer) {
    int scale = renderer.uiScale();
    return MapTheme.lineHeight(scale) + (8 * scale);
  }

  public static int compactRowHeight(ReactRenderer renderer) {
    int scale = renderer.uiScale();
    return MapTheme.lineHeight(scale) + (3 * scale);
  }

  public static int barHeight(ReactRenderer renderer) {
    return 3 * renderer.uiScale();
  }

  public static void columnLabels(ReactRenderer renderer, Region strip, Region[] columns, String[] labels) {
    if (strip.isEmpty() || columns.length == 0) {
      return;
    }

    renderer.hSeparator(strip, strip.height() - MapTheme.borderThickness(renderer.uiScale()), MapTheme.LINE);
    for (int i = 0; i < columns.length && i < labels.length; i++) {
      String label = labels[i];
      if (label == null || label.isBlank()) {
        continue;
      }

      Region cell = new Region(columns[i].x(), strip.y(), columns[i].width(), strip.height());
      if (i == 0) {
        renderer.textIn(cell, 0, 0, label, MapTheme.TEXT_MUTED);
      } else {
        renderer.textRightIn(cell, 0, 0, label, MapTheme.TEXT_MUTED);
      }
    }
  }

  public static void emptyState(ReactRenderer renderer, Region region, String title, String detail) {
    if (region.isEmpty()) {
      return;
    }

    int scale = renderer.uiScale();
    int lineHeight = MapTheme.lineHeight(scale);
    int panelHeight = Math.min(region.height(), (2 * lineHeight) + (4 * scale));
    Region panel = new Region(
        region.x(),
        region.y() + Math.max(0, (region.height() - panelHeight) / 2),
        region.width(),
        panelHeight
    );

    renderer.panel(panel, MapTheme.SURFACE_2, MapTheme.LINE);
    Region content = panel.inset(MapTheme.pad(scale), 2 * scale, MapTheme.pad(scale), 2 * scale);
    renderer.textCenteredIn(content, 0, title, MapTheme.TEXT);
    if (detail != null && !detail.isBlank()) {
      renderer.textCenteredIn(content, lineHeight, detail, MapTheme.TEXT_MUTED);
    }
  }

  public static String compact(double value) {
    if (!Double.isFinite(value) || value <= 0D) {
      return "0";
    }
    if (value >= 1_000_000D) {
      return Form.f(value / 1_000_000D, 1) + "m";
    }
    if (value >= 1000D) {
      return Form.f(value / 1000D, 1) + "k";
    }
    if (value >= 100D) {
      return Form.f(value, 0);
    }
    return Form.f(value, 1);
  }

  public static String percent(double ratio) {
    return Form.f(Math.max(0D, Math.min(1D, ratio)) * 100D, 1) + "%";
  }
}
