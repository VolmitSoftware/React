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
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

abstract class RendererIntegrationMetricsBase implements ReactRenderer {
  @Override
  public boolean rendersNativeMegamap() {
    return true;
  }

  @Override
  public void render() {
    int w = width();
    int h = height();
    int s = uiScale();
    clear(backgroundColor());
    dashHeader(title(), headerValue(), accentColor());

    IntegrationController controller = React.controller(IntegrationController.class);
    if (controller == null || controller.getRemoteSamplerBridge() == null) {
      text(4 * s, 18 * s, "Status: OFFLINE", TEXT_BRIGHT);
      text(4 * s, 30 * s, "Bridge unavailable", TEXT_DIM);
      return;
    }

    RemoteSamplerBridge bridge = controller.getRemoteSamplerBridge();
    IntegrationController.IntegrationStatus status = controller.statusFor(pluginId());
    String health = status == null ? "MISSING" : status.health().name();
    set(2 * s, 15 * s, w - (4 * s), 9 * s, healthColor(health));
    text(4 * s, 16 * s, "Status: " + health, TEXT_BRIGHT);

    int y = 28 * s;
    int cutoffY = h - (12 * s);
    long newestSampleMs = 0L;
    for (MetricLine metric : metricLines()) {
      IntegrationMetricSample sample = metricSample(bridge, metric);
      newestSampleMs = Math.max(newestSampleMs, sample == null ? 0L : sample.sampledAtMs());
      drawMetricRow(metric, sample, y, w, s);
      y += 14 * s;
      if (y > cutoffY) {
        return;
      }
    }

    if (status != null && status.message() != null && !status.message().isBlank()) {
      text(4 * s, y, fitText("Msg: " + status.message(), w - (8 * s)), TEXT_DIM);
      y += 12 * s;
      if (y > cutoffY) {
        return;
      }
    }

    if (newestSampleMs > 0L) {
      double ageSeconds = Math.max(0D, (System.currentTimeMillis() - newestSampleMs) / 1000D);
      text(4 * s, y, "Age: " + Form.f(ageSeconds, 1) + "s", TEXT_DIM);
    }
  }

  protected abstract String pluginId();

  protected abstract String title();

  protected abstract TinyColor backgroundColor();

  protected abstract TinyColor accentColor();

  protected abstract List<MetricLine> metricLines();

  protected String headerValue() {
    return null;
  }

  protected IntegrationMetricSample metricSample(RemoteSamplerBridge bridge, MetricLine metric) {
    return bridge.getSample(pluginId(), metric.key());
  }

  private String formatSample(MetricLine metric, IntegrationMetricSample sample) {
    if (sample == null || !sample.available()) {
      return "n/a";
    }

    double value = scaledValue(metric, sample);
    String suffix = metric.unitSuffix() == null ? "" : metric.unitSuffix();
    return Form.f(value, Math.max(0, metric.decimals())) + suffix;
  }

  private double scaledValue(MetricLine metric, IntegrationMetricSample sample) {
    if (sample == null) {
      return 0D;
    }
    return sample.valueOr(0D) * metric.scale();
  }

  private void drawMetricRow(MetricLine metric, IntegrationMetricSample sample, int y, int w, int s) {
    String line = metric.label() + ": " + formatSample(metric, sample);
    text(4 * s, y, fitText(line, w - (10 * s)), TEXT_BRIGHT);

    int barWidth = w - (10 * s);
    if (sample == null || !sample.available()) {
      set(4 * s, y + (8 * s), barWidth, 3 * s, new TinyColor(42, 42, 42));
      return;
    }

    double value = Math.abs(scaledValue(metric, sample));
    double normalized = 1D - (1D / (1D + value));
    int fillWidth = (int) Math.round(barWidth * Math.max(0D, Math.min(1D, normalized)));

    set(4 * s, y + (8 * s), barWidth, 3 * s, new TinyColor(22, 28, 34));
    if (fillWidth <= 0) {
      return;
    }

    TinyColor color = gradient(normalized, new TinyColor(58, 170, 214), new TinyColor(255, 110, 58));
    set(4 * s, y + (8 * s), fillWidth, 3 * s, color);
  }

  private TinyColor healthColor(String health) {
    if (health == null) {
      return new TinyColor(90, 90, 90);
    }

    return switch (health.toUpperCase()) {
      case "HEALTHY", "ONLINE", "OK" -> new TinyColor(46, 136, 88);
      case "DEGRADED", "WARN" -> new TinyColor(158, 116, 48);
      case "UNAVAILABLE", "MISSING", "OFFLINE", "ERROR" ->
          new TinyColor(148, 62, 52);
      default -> new TinyColor(90, 90, 90);
    };
  }

  private TinyColor gradient(double normalized, TinyColor low, TinyColor high) {
    return new TinyColor(gradientRgb(normalized, low, high));
  }

  protected record MetricLine(String key, String label, int decimals,
                              String unitSuffix, double scale) {
    protected MetricLine(String key, String label, int decimals, String unitSuffix) {
      this(key, label, decimals, unitSuffix, 1D);
    }
  }
}
