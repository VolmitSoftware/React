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

public class RendererBiletoolsMetrics extends RendererIntegrationMetricsBase {
  public static final String ID = "biletools-metrics";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  protected String pluginId() {
    return "biletools";
  }

  @Override
  protected TextKey title() {
    return RendererMessages.TITLE_BILETOOLS_METRICS;
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(10, 18, 9);
  }

  @Override
  protected TinyColor accentColor() {
    return new TinyColor(122, 200, 70);
  }

  @Override
  protected List<MetricLine> metricLines() {
    return List.of(
        new MetricLine(IntegrationMetricSchema.BILETOOLS_WATCHED_JARS, RendererMessages.METRIC_WATCHED_JARS, 0, ""),
        new MetricLine(IntegrationMetricSchema.BILETOOLS_DIRTY_PLUGINS, RendererMessages.METRIC_DIRTY_PLUGINS, 0, ""),
        new MetricLine(IntegrationMetricSchema.BILETOOLS_RELOADS_TOTAL, RendererMessages.METRIC_RELOADS, 0, ""),
        new MetricLine(IntegrationMetricSchema.BILETOOLS_LAST_RELOAD_MS, RendererMessages.METRIC_LAST_RELOAD, 0, " ms"),
        new MetricLine(IntegrationMetricSchema.BILETOOLS_REMOTE_SLAVE_ONLINE, RendererMessages.METRIC_REMOTE_SLAVE, 0, "")
    );
  }
}
