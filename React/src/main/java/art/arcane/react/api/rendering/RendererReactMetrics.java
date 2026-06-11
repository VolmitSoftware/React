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
import art.arcane.react.content.sampler.*;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class RendererReactMetrics implements ReactRenderer {
  public static final String ID = "react-metrics";

  private static final List<MetricLine> METRICS = List.of(
      new MetricLine(SamplerTicksPerSecond.ID, "TPS", 1, "", 20D),
      new MetricLine(SamplerTickTime.ID, "Tick", 1, " ms", 80D),
      new MetricLine(SamplerIncidentScore.ID, "Incident", 1, "", 100D),
      new MetricLine(SamplerReactJobsQueue.ID, "Jobs", 0, "", 300D),
      new MetricLine(SamplerSchedulerBacklog.ID, "Backlog", 0, "", 300D)
  );

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public void render() {
    clear(new TinyColor(12, 16, 24));
    dashHeader("React Metrics", null, new TinyColor(96, 148, 222));

    set(2, 15, 124, 9, new TinyColor(52, 134, 96));
    text(4, 16, "Status: LOCAL", TEXT_BRIGHT);

    int y = 28;
    for (MetricLine metric : METRICS) {
      drawMetricRow(metric, y);
      y += 14;
      if (y > 116) {
        return;
      }
    }
  }

  private void drawMetricRow(MetricLine metric, int y) {
    Double sampled = sample(metric.samplerId());
    String line = metric.label() + ": " + formatSample(metric, sampled);
    text(4, y, trim(line, 24), TEXT_BRIGHT);

    set(4, y + 8, 118, 3, new TinyColor(22, 28, 34));
    if (sampled == null) {
      set(4, y + 8, 118, 3, new TinyColor(42, 42, 42));
      return;
    }

    double normalized = Math.max(0D, Math.min(1D, sampled / Math.max(1D, metric.maxValue())));
    int width = (int) Math.round(118D * normalized);
    if (width <= 0) {
      return;
    }

    TinyColor color = gradient(normalized, new TinyColor(58, 170, 214), new TinyColor(255, 184, 74));
    set(4, y + 8, width, 3, color);
  }

  private String formatSample(MetricLine metric, Double sampled) {
    if (sampled == null) {
      return "n/a";
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

  private TinyColor gradient(double normalized, TinyColor low, TinyColor high) {
    return new TinyColor(gradientRgb(normalized, low, high));
  }

  private String trim(String text, int maxChars) {
    if (text == null || text.isBlank()) {
      return "";
    }

    int max = Math.max(4, maxChars);
    if (text.length() <= max) {
      return text;
    }

    return text.substring(0, max - 3) + "...";
  }

  private record MetricLine(String samplerId, String label, int decimals,
                            String unitSuffix, double maxValue) {
  }
}
