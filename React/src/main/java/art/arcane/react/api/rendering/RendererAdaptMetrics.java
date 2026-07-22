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
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public class RendererAdaptMetrics extends RendererIntegrationMetricsBase {
  public static final String ID = "adapt-metrics";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  protected String pluginId() {
    return "adapt";
  }

  @Override
  protected TextKey title() {
    return RendererMessages.TITLE_ADAPT_METRICS;
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(18, 12, 24);
  }

  @Override
  protected TinyColor accentColor() {
    return new TinyColor(160, 80, 70);
  }

  @Override
  protected List<MetricLine> metricLines() {
    return List.of(
        new MetricLine(IntegrationMetricSchema.ADAPT_SESSION_LOAD, RendererMessages.METRIC_SESSION, 1, " %"),
        new MetricLine(ReactConfiguration.adaptAbilityOpsMetricKey(), RendererMessages.METRIC_ABILITY_MODE, 0, " op/m"),
        new MetricLine(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS_TICK, RendererMessages.METRIC_ALL_PER_TICK, 3, " op/t"),
        new MetricLine(IntegrationMetricSchema.ADAPT_WORLD_POLICY_LATENCY, RendererMessages.METRIC_POLICY, 2, " ms")
    );
  }

  @Override
  protected String metricLabel(MetricLine metric) {
    if (metric.labelKey() != RendererMessages.METRIC_ABILITY_MODE) {
      return super.metricLabel(metric);
    }
    return ReactLanguage.raw(
        RendererMessages.METRIC_ABILITY_MODE,
        MessageArgument.untrusted("mode", ReactConfiguration.adaptAbilityOpsMetricLabel())
    );
  }
}
