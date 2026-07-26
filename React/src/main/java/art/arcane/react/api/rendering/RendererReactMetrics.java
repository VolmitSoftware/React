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

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerReactJobsQueue;
import art.arcane.react.content.sampler.SamplerSchedulerBacklog;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.content.sampler.SamplerTicksPerSecond;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public class RendererReactMetrics implements ReactRenderer {
  public static final String ID = "react-metrics";

  private static final List<MetricLine> METRICS = List.of(
      new MetricLine(SamplerTicksPerSecond.ID, RendererMessages.METRIC_TPS, 1, "", 20D),
      new MetricLine(SamplerTickTime.ID, RendererMessages.METRIC_TICK, 1, " ms", 80D),
      new MetricLine(SamplerIncidentScore.ID, RendererMessages.METRIC_INCIDENT, 1, "", 100D),
      new MetricLine(SamplerReactJobsQueue.ID, RendererMessages.METRIC_JOBS, 0, "", 300D),
      new MetricLine(SamplerSchedulerBacklog.ID, RendererMessages.METRIC_BACKLOG, 0, "", 300D)
  );

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
    dashHeader(ReactLanguage.raw(RendererMessages.TITLE_REACT_METRICS), null, MapTheme.INFO);

    Region body = RendererLayout.body(this);
    Region statusRow = body.topBand(MapTheme.lineHeight(s) + (2 * s));
    panel(statusRow, MapTheme.OK, null);
    textIn(
        statusRow,
        MapTheme.pad(s),
        s,
        ReactLanguage.raw(
            RendererMessages.STATUS_LINE,
            MessageArgument.untrusted("status", ReactLanguage.raw(RendererMessages.STATUS_LOCAL))
        ),
        MapTheme.TEXT_STRONG
    );

    Region grid = body.withoutTop(statusRow.height() + MapTheme.gutter(s));
    int cellHeight = MapTheme.lineHeight(s) + (7 * s);
    int columns = Math.max(1, Math.min(megamapColumns(), grid.width() / (76 * s)));
    Region[] cells = grid.splitColumns(MapTheme.gutter(s), equalWeights(columns));
    int rows = Math.max(1, cells[0].rowCapacity(cellHeight));

    int placed = 0;
    for (MetricLine metric : METRICS) {
      if (placed >= columns * rows) {
        break;
      }

      Region cell = cells[placed / rows].rowAt(placed % rows, cellHeight);
      placed++;
      if (visible(cell)) {
        drawMetricCell(metric, cell, s);
      }
    }

    RendererLayout.footer(this, null, null, MapTheme.INFO);
  }

  private void drawMetricCell(MetricLine metric, Region cell, int s) {
    Double sampled = sample(metric.samplerId());
    Region line = cell.topBand(MapTheme.lineHeight(s));
    Region[] halves = line.splitColumns(MapTheme.gutter(s), 58, 42);
    textIn(halves[0], 0, 0, ReactLanguage.raw(metric.labelKey()), MapTheme.TEXT);
    textRightIn(halves[1], 0, 0, formatSample(metric, sampled), MapTheme.TEXT_SOFT);

    Region track = cell.withoutTop(line.height() + s).topBand(3 * s);
    if (sampled == null) {
      fill(track, MapTheme.LINE);
      return;
    }

    double normalized = Math.max(0D, Math.min(1D, sampled / Math.max(1D, metric.maxValue())));
    RendererLayout.bar(this, track, normalized, MapTheme.severity(normalized));
  }

  private int[] equalWeights(int columns) {
    int[] weights = new int[columns];
    for (int i = 0; i < columns; i++) {
      weights[i] = 1;
    }
    return weights;
  }

  private String formatSample(MetricLine metric, Double sampled) {
    if (sampled == null) {
      return ReactLanguage.raw(RendererMessages.VALUE_NOT_AVAILABLE);
    }
    return Form.f(sampled, Math.max(0, metric.decimals())) + metric.unitSuffix();
  }

  private Double sample(String samplerId) {
    Sampler sampler = React.sampler(samplerId);
    if (sampler == null) {
      return null;
    }

    try {
      double value = sampler.sample();
      if (!Double.isFinite(value)) {
        return null;
      }
      return value;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private record MetricLine(String samplerId, TextKey labelKey, int decimals,
                            String unitSuffix, double maxValue) {
  }
}
