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

import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public class RendererHolouiMetrics extends RendererIntegrationMetricsBase {
  public static final String ID = "holoui-metrics";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  protected String pluginId() {
    return "holoui";
  }

  @Override
  protected TextKey title() {
    return RendererMessages.TITLE_HOLOUI_METRICS;
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(8, 18, 20);
  }

  @Override
  protected TinyColor accentColor() {
    return new TinyColor(64, 196, 188);
  }

  @Override
  protected List<MetricLine> metricLines() {
    return List.of(
        new MetricLine(IntegrationMetricSchema.HOLOUI_SESSION_HOLDERS, RendererMessages.METRIC_SESSIONS, 0, ""),
        new MetricLine(IntegrationMetricSchema.HOLOUI_MENUS_OPEN, RendererMessages.METRIC_MENUS, 0, ""),
        new MetricLine(IntegrationMetricSchema.HOLOUI_PREVIEWS_OPEN, RendererMessages.METRIC_PREVIEWS, 0, ""),
        new MetricLine(IntegrationMetricSchema.HOLOUI_DISPLAY_ENTITIES, RendererMessages.METRIC_ENTITIES, 0, ""),
        new MetricLine(IntegrationMetricSchema.HOLOUI_DISPLAY_ENTITIES_VISIBLE, RendererMessages.METRIC_VISIBLE, 0, ""),
        new MetricLine(IntegrationMetricSchema.HOLOUI_PACKETS_PER_SECOND, RendererMessages.METRIC_TRAFFIC, 0, " pk/s"),
        new MetricLine(IntegrationMetricSchema.HOLOUI_TICK_MS, RendererMessages.METRIC_TICK, 2, " ms/s")
    );
  }
}
