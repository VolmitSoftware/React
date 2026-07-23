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

public class RendererHiddenoreMetrics extends RendererIntegrationMetricsBase {
  public static final String ID = "hiddenore-metrics";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  protected String pluginId() {
    return "hiddenore";
  }

  @Override
  protected TextKey title() {
    return RendererMessages.TITLE_HIDDENORE_METRICS;
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(20, 15, 8);
  }

  @Override
  protected TinyColor accentColor() {
    return new TinyColor(214, 160, 52);
  }

  @Override
  protected List<MetricLine> metricLines() {
    return List.of(
        new MetricLine(IntegrationMetricSchema.HIDDENORE_BLOCKS_BROKEN_PER_SECOND, RendererMessages.METRIC_BREAKS, 1, " blk/s"),
        new MetricLine(IntegrationMetricSchema.HIDDENORE_DROPS_INJECTED_PER_SECOND, RendererMessages.METRIC_DROPS, 1, " /s"),
        new MetricLine(IntegrationMetricSchema.HIDDENORE_VEINS_DISCOVERED_PER_SECOND, RendererMessages.METRIC_VEIN_FINDS, 2, " /s"),
        new MetricLine(IntegrationMetricSchema.HIDDENORE_VEIN_CACHE_CHUNKS, RendererMessages.METRIC_VEIN_CACHE, 0, ""),
        new MetricLine(IntegrationMetricSchema.HIDDENORE_PDC_READS_PER_SECOND, RendererMessages.METRIC_PDC_READS, 1, " /s"),
        new MetricLine(IntegrationMetricSchema.HIDDENORE_PDC_WRITES_PER_SECOND, RendererMessages.METRIC_PDC_WRITES, 1, " /s"),
        new MetricLine(IntegrationMetricSchema.HIDDENORE_DROP_RULES, RendererMessages.METRIC_RULES, 0, "")
    );
  }
}
