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
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Plugin Event Impact List Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeaturePluginEventImpactListMap extends ReactFeature implements ReactRenderer {
  public static final String ID = "plugin-event-impact-list-map";
  private static final TinyColor ACCENT = new TinyColor(236, 142, 58);
  private static final TinyColor FRAME_VALUE = new TinyColor(126, 196, 255);
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entries shown by plugin event impact list map.", impact = "Higher values show more plugins but reduce per-row readability.")
  private int maxEntries = 7;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entries shown when plugin event impact list map is placed in item-frames.", impact = "Lower values improve readability from distance; higher values show more rows with denser text.")
  private int frameMaxEntries = 5;

  public FeaturePluginEventImpactListMap() {
    super(ID);
  }

  @Override
  public void render() {
    boolean frameAnchored = isFrameAnchored();
    drawBackdrop();
    dashHeader("Plugin Impact", frameAnchored ? "FRAME" : null, ACCENT, FRAME_VALUE);

    PluginEventImpactSeries.Snapshot snapshot = PluginEventImpactSeries.snapshot();
    List<PluginEventImpactSeries.Entry> entries = snapshot.entries();
    if (entries.isEmpty()) {
      text(4, 20, "No plugin event impact data", TEXT_BRIGHT);
      text(4, 30, "Open this map for a few seconds", TEXT_DIM);
      return;
    }

    int rowHeight = 18;
    int startY = 16;
    int rowsBySpace = Math.max(1, (118 - startY) / rowHeight);
    int configuredLimit = Math.max(1, frameAnchored ? frameMaxEntries : maxEntries);
    int limit = Math.max(1, Math.min(Math.min(configuredLimit, entries.size()), rowsBySpace));
    double maxImpact = Math.max(0.0001D, entries.get(0).impact());
    double total = Math.max(0.0001D, snapshot.totalImpact());
    int y = startY;

    for (int i = 0; i < limit; i++) {
      PluginEventImpactSeries.Entry entry = entries.get(i);
      double normalized = Math.max(0D, Math.min(1D, entry.impact() / maxImpact));
      double percent = Math.max(0D, Math.min(1D, entry.impact() / total)) * 100D;

      drawRowCard(y, rowHeight, frameAnchored);
      if (frameAnchored) {
        drawFrameRow(i, y, normalized, percent, entry);
      } else {
        drawStandardRow(i, y, normalized, percent, entry);
      }

      y += rowHeight;
      if (y > 116) {
        break;
      }
    }
  }

  @Override
  public void onActivate() {

  }

  @Override
  public void onDeactivate() {

  }

  @Override
  public int getTickInterval() {
    return -1;
  }

  @Override
  public void onTick() {

  }

  private void drawBackdrop() {
    for (int y = 0; y < height(); y++) {
      double n = y / (double) Math.max(1, height() - 1);
      set(0, y, width(), 1, gradient(n, new TinyColor(12, 16, 20), new TinyColor(20, 24, 28)));
    }
  }

  private void drawRowCard(int y, int rowHeight, boolean frameAnchored) {
    int cardHeight = Math.max(13, rowHeight - 1);
    TinyColor panel = frameAnchored ? new TinyColor(18, 24, 30) : new TinyColor(16, 22, 28);
    TinyColor border = frameAnchored ? new TinyColor(44, 58, 72) : new TinyColor(32, 42, 54);

    set(2, y - 1, 124, cardHeight, panel);
    set(2, y - 1, 124, 1, border);
    set(2, y + cardHeight - 1, 124, 1, border);
  }

  private void drawStandardRow(int rank, int y, double normalized, double percent, PluginEventImpactSeries.Entry entry) {
    String rankLabel = "#" + (rank + 1);
    text(4, y, rankLabel, TEXT_DIM);

    int pluginX = 18;
    int barX = 74;
    int pluginMaxWidth = Math.max(10, barX - pluginX - 3);
    text(pluginX, y, trimToWidth(entry.plugin(), pluginMaxWidth), TEXT_BRIGHT);

    int barY = y + 2;
    int barW = 48;
    int fill = (int) Math.round(barW * normalized);
    set(barX, barY, barW, 4, new TinyColor(26, 34, 42));
    if (fill > 0) {
      set(barX, barY, fill, 4, rankColor(rank, normalized));
    }

    String line = Form.f(percent, 1) + "%  " + compact(entry.impact()) + " ms";
    text(4, y + 8, line, TEXT_DIM);
  }

  private void drawFrameRow(int rank, int y, double normalized, double percent, PluginEventImpactSeries.Entry entry) {
    text(4, y, "#" + (rank + 1), TEXT_DIM);

    String percentLabel = Form.f(percent, 1) + "%";
    int percentX = Math.max(78, 121 - textWidth(percentLabel) + 1);
    text(20, y, trimToWidth(entry.plugin(), Math.max(10, percentX - 23)), TEXT_BRIGHT);
    text(percentX, y, percentLabel, TEXT_DIM);

    int barX = 20;
    int barY = y + 8;
    int barW = 102;
    int fill = (int) Math.round(barW * normalized);
    set(barX, barY, barW, 5, new TinyColor(26, 34, 42));
    if (fill > 0) {
      set(barX, barY, fill, 5, rankColor(rank, normalized));
    }
  }

  private boolean isFrameAnchored() {
    MapController mapController = React.controller(MapController.class);
    return mapController != null && mapController.hasFrameAnchor(view());
  }

  private TinyColor rankColor(int rank, double normalized) {
    if (rank == 0) {
      return gradient(normalized, new TinyColor(255, 188, 74), new TinyColor(255, 90, 50));
    }
    if (rank <= 2) {
      return gradient(normalized, new TinyColor(226, 134, 68), new TinyColor(232, 78, 56));
    }
    return gradient(normalized, new TinyColor(78, 176, 214), new TinyColor(136, 122, 255));
  }

  private TinyColor gradient(double normalized, TinyColor low, TinyColor high) {
    return new TinyColor(gradientRgb(normalized, low, high));
  }

  private String trimToWidth(String value, int maxWidth) {
    if (value == null || value.isBlank()) {
      return "Unknown";
    }

    String safe = value.trim();
    int limit = Math.max(8, maxWidth);
    if (textWidth(safe) <= limit) {
      return safe;
    }

    String suffix = ".";
    int end = safe.length();
    while (end > 1) {
      String candidate = safe.substring(0, end) + suffix;
      if (textWidth(candidate) <= limit) {
        return candidate;
      }
      end--;
    }

    return safe.substring(0, 1);
  }

  private String compact(double value) {
    if (!Double.isFinite(value) || value <= 0D) {
      return "0";
    }
    if (value >= 1000D) {
      return Form.f(value / 1000D, 1) + "k";
    }
    if (value >= 100D) {
      return Form.f(value, 0);
    }
    return Form.f(value, 1);
  }
}
