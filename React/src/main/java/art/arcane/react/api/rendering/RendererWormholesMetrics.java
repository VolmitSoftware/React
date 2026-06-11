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
import art.arcane.volmlib.integration.IntegrationMetricSchema;

import java.util.List;

public class RendererWormholesMetrics extends RendererIntegrationMetricsBase {
  public static final String ID = "wormholes-metrics";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  protected String pluginId() {
    return "wormholes";
  }

  @Override
  protected String title() {
    return "Wormholes Metrics";
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(14, 10, 22);
  }

  @Override
  protected TinyColor accentColor() {
    return new TinyColor(138, 82, 226);
  }

  @Override
  protected List<MetricLine> metricLines() {
    return List.of(
        new MetricLine(IntegrationMetricSchema.WORMHOLES_PORTALS, "Portals", 0, ""),
        new MetricLine(IntegrationMetricSchema.WORMHOLES_PROJECTIONS_ACTIVE, "Projecting", 0, ""),
        new MetricLine(IntegrationMetricSchema.WORMHOLES_PROJECTION_OBSERVERS, "Observers", 0, ""),
        new MetricLine(IntegrationMetricSchema.WORMHOLES_PROJECTION_RENDER_MS, "Render", 2, " ms/s"),
        new MetricLine(IntegrationMetricSchema.WORMHOLES_PACKETS_PER_SECOND, "Traffic", 0, " pk/s"),
        new MetricLine(IntegrationMetricSchema.WORMHOLES_SPOOFED_ENTITIES, "Entities", 0, ""),
        new MetricLine(IntegrationMetricSchema.WORMHOLES_TRAVERSALS_PER_MINUTE, "Travel", 1, " /min")
    );
  }
}
