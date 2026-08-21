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

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MapTheme;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.Region;
import art.arcane.react.api.rendering.RendererLayout;
import art.arcane.react.core.controller.EventController;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.List;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Plugin Event Impact List Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeaturePluginEventImpactListMap extends ReactFeature implements ReactRenderer {
  public static final String ID = "plugin-event-impact-list-map";
  private static final TinyColor ACCENT = MapTheme.WARN;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum rows drawn per map tile, or 0 to fill the available height.", impact = "Zero lets a taller map wall list every plugin; a fixed value multiplies by the number of vertical tiles, so a taller wall still lists more plugins.")
  private int maxRowsPerTile = 0;

  public FeaturePluginEventImpactListMap() {
    super(ID);
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.adaptiveWall();
  }

  @Override
  public void render() {
    boolean frameAnchored = isFrameAnchored();
    RendererLayout.backdrop(this, MapTheme.SURFACE_0, MapTheme.SURFACE_1);
    dashHeader(
        ReactLanguage.raw(RendererMessages.TITLE_PLUGIN_IMPACT),
        frameAnchored ? ReactLanguage.raw(RendererMessages.STATUS_FRAME) : null,
        ACCENT,
        MapTheme.INFO
    );

    PluginEventImpactSeries.Snapshot snapshot = PluginEventImpactSeries.snapshot();
    List<PluginEventImpactSeries.Entry> entries = snapshot.entries();
    Region body = RendererLayout.body(this);
    if (entries.isEmpty()) {
      RendererLayout.emptyState(
          this,
          body,
          ReactLanguage.raw(RendererMessages.PLUGIN_NO_IMPACT),
          ReactLanguage.raw(RendererMessages.PLUGIN_WAIT_FOR_DATA)
      );
      RendererLayout.footer(this, null, null, ACCENT);
      return;
    }

    int columns = megamapColumns();
    int[] weights = columnWeights(columns);
    int gutter = MapTheme.gutter(uiScale());
    Region list = body;
    if (megamapDetail().atLeast(MegamapGrid.MegamapDetail.EXPANDED)) {
      Region strip = body.topBand(MapTheme.lineHeight(uiScale()));
      RendererLayout.columnLabels(this, strip, strip.splitColumns(gutter, weights), columnLabels(columns));
      list = body.withoutTop(strip.height() + gutter);
    }

    int rowHeight = RendererLayout.listRowHeight(this);
    int capacity = Math.max(1, list.rowCapacity(rowHeight));
    if (maxRowsPerTile > 0) {
      capacity = (int) Math.min(capacity, (long) maxRowsPerTile * gridHeight());
    }

    int limit = Math.min(entries.size(), capacity);
    double maxImpact = Math.max(0.0001D, entries.get(0).impact());
    double total = Math.max(0.0001D, snapshot.totalImpact());
    double windowSeconds = windowSeconds();
    double totalMillis = 0D;
    for (PluginEventImpactSeries.Entry entry : entries) {
      totalMillis += entry.currentMS() / windowSeconds;
    }

    for (int i = 0; i < limit; i++) {
      Region row = list.rowAt(i, rowHeight);
      if (!visible(row)) {
        continue;
      }

      PluginEventImpactSeries.Entry entry = entries.get(i);
      double normalized = Math.max(0D, Math.min(1D, entry.impact() / maxImpact));
      double share = Math.max(0D, Math.min(1D, entry.impact() / total));
      String[] values = columnValues(columns, i, share, entry.currentMS() / windowSeconds, entry);
      RendererLayout.listRow(this, i, row.withoutBottom(gutter), weights, gutter, normalized, values);
    }

    RendererLayout.footer(
        this,
        ReactLanguage.raw(
            MapMessages.ROWS_SHOWN,
            MessageArgument.untrusted("shown", Integer.toString(limit)),
            MessageArgument.untrusted("total", Integer.toString(entries.size()))
        ),
        ReactLanguage.raw(
            MapMessages.TOTAL_COST,
            MessageArgument.untrusted("value", RendererLayout.compact(totalMillis))
        ),
        ACCENT
    );
  }

  private int[] columnWeights(int columns) {
    return switch (columns) {
      case 1 -> new int[]{62, 38};
      case 2 -> new int[]{46, 27, 27};
      case 3, 4 -> new int[]{38, 21, 21, 20};
      // 5+ tiles of width: hand the surplus to the name column so plugin names stop truncating.
      default -> new int[]{52, 17, 16, 15};
    };
  }

  private String[] columnLabels(int columns) {
    String name = ReactLanguage.raw(MapMessages.COLUMN_NAME);
    String share = ReactLanguage.raw(MapMessages.COLUMN_SHARE);
    String cost = ReactLanguage.raw(MapMessages.COLUMN_COST);
    return switch (columns) {
      case 1 -> new String[]{name, share};
      case 2 -> new String[]{name, cost, share};
      default -> new String[]{name, ReactLanguage.raw(MapMessages.COLUMN_CALLS), cost, share};
    };
  }

  private String[] columnValues(
      int columns,
      int rank,
      double share,
      double millisPerSecond,
      PluginEventImpactSeries.Entry entry
  ) {
    String name = "#" + (rank + 1) + " " + pluginName(entry);
    String sharePercent = RendererLayout.percent(share);
    String cost = RendererLayout.compact(millisPerSecond);
    return switch (columns) {
      case 1 -> new String[]{name, sharePercent};
      case 2 -> new String[]{name, cost, sharePercent};
      default -> new String[]{name, RendererLayout.compact(entry.currentCalls()), cost, sharePercent};
    };
  }

  private String pluginName(PluginEventImpactSeries.Entry entry) {
    String plugin = entry.plugin();
    return plugin == null || plugin.isBlank() ? ReactLanguage.raw(RendererMessages.PIE_UNKNOWN) : plugin.trim();
  }

  private double windowSeconds() {
    EventController controller = React.controller(EventController.class);
    return controller == null ? 5D : Math.max(1L, controller.getTinterval()) / 1000.0D;
  }

  private boolean isFrameAnchored() {
    MapController mapController = React.controller(MapController.class);
    return mapController != null && mapController.hasFrameAnchor(view());
  }
}
