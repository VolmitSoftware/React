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

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;

public class RendererUnknown implements ReactRenderer {
  public static final String ID = "unknown";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.adaptive(4, 4);
  }

  @Override
  public void render() {
    int s = uiScale();
    RendererLayout.backdrop(this, MapTheme.SURFACE_0, MapTheme.SURFACE_1);
    dashHeader(ReactLanguage.raw(RendererMessages.TITLE_REACT_MAP), null, MapTheme.WARN);

    Region body = RendererLayout.body(this);
    Region[] split = body.splitColumns(MapTheme.gutter(s), 34, 66);
    drawWarningGlyph(split[0], s);

    Region text = split[1];
    int lineHeight = MapTheme.lineHeight(s);
    int top = Math.max(0, (text.height() - (6 * lineHeight)) / 2);
    textIn(text, 0, top, ReactLanguage.raw(RendererMessages.UNKNOWN_MAP_WAS), MapTheme.TEXT);
    textIn(text, 0, top + lineHeight, ReactLanguage.raw(RendererMessages.UNKNOWN_BOUND), MapTheme.TEXT);
    textIn(text, 0, top + (2 * lineHeight), ReactLanguage.raw(RendererMessages.UNKNOWN_MISSING), MapTheme.TEXT);
    textIn(text, 0, top + (4 * lineHeight), ReactLanguage.raw(RendererMessages.UNKNOWN_RESELECT_COMMAND), MapTheme.TEXT_MUTED);
    textIn(text, 0, top + (5 * lineHeight), ReactLanguage.raw(RendererMessages.UNKNOWN_RESELECT), MapTheme.TEXT_MUTED);

    RendererLayout.footer(this, ReactLanguage.raw(RendererMessages.UNKNOWN_RENDERER_UNAVAILABLE), null, MapTheme.WARN);
  }

  private void drawWarningGlyph(Region area, int s) {
    if (!visible(area)) {
      return;
    }

    int extent = (Math.min(area.width(), area.height()) / 2) - MapTheme.pad(s);
    if (extent < 6) {
      return;
    }

    int cx = area.centerX();
    int cy = area.centerY();
    pushClip(area);
    try {
      for (int y = -extent; y <= extent; y++) {
        int halfWidth = Math.max(0, extent - Math.abs(y));
        boolean edge = Math.abs(y) == extent;
        set(cx - halfWidth, cy + y, (2 * halfWidth) + 1, 1, edge ? MapTheme.WARN : MapTheme.SURFACE_3);
        if (!edge) {
          set(cx - halfWidth, cy + y, Math.max(1, s), 1, MapTheme.WARN);
          set(cx + halfWidth, cy + y, Math.max(1, s), 1, MapTheme.WARN);
        }
      }

      int barHeight = Math.max(3, (extent * 3) / 5);
      set(cx, cy - (barHeight / 2), Math.max(1, s), barHeight, MapTheme.ACCENT_YELLOW);
      set(cx, cy + (barHeight / 2) + (2 * s), Math.max(1, s), Math.max(1, s), MapTheme.ACCENT_YELLOW);
    } finally {
      popClip();
    }
  }
}
