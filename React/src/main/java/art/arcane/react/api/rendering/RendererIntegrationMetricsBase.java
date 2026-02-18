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
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

abstract class RendererIntegrationMetricsBase implements ReactRenderer {
  @Override
  public void render() {
    clear(backgroundColor());
    set(0, 0, width(), 12, accentColor());
    text(4, 2, title());

    IntegrationController controller = React.controller(IntegrationController.class);
    if (controller == null || controller.getRemoteSamplerBridge() == null) {
      text(4, 18, "Status: OFFLINE");
      text(4, 30, "Bridge unavailable");
      return;
    }

    IntegrationController.IntegrationStatus status = controller.statusFor(pluginId());
    String health = status == null ? "MISSING" : status.health().name();
    set(2, 14, 124, 9, healthColor(health));
    text(4, 15, "Status: " + health);

    int y = 28;
    long newestSampleMs = 0L;
    for (MetricLine metric : metricLines()) {
      IntegrationMetricSample sample = controller.getRemoteSamplerBridge().getSample(pluginId(), metric.key());
      newestSampleMs = Math.max(newestSampleMs, sample == null ? 0L : sample.sampledAtMs());
      drawMetricRow(metric, sample, y);
      y += 14;
      if (y > 116) {
        return;
      }
    }

    if (status != null && status.message() != null && !status.message().isBlank()) {
      text(4, y, trim("Msg: " + status.message(), 24));
      y += 12;
      if (y > 116) {
        return;
      }
    }

    if (newestSampleMs > 0L) {
      double ageSeconds = Math.max(0D, (System.currentTimeMillis() - newestSampleMs) / 1000D);
      text(4, y, "Age: " + Form.f(ageSeconds, 1) + "s");
    }
  }

  protected abstract String pluginId();

  protected abstract String title();

  protected abstract TinyColor backgroundColor();

  protected abstract TinyColor accentColor();

  protected abstract List<MetricLine> metricLines();

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

  private void drawMetricRow(MetricLine metric, IntegrationMetricSample sample, int y) {
    String line = metric.label() + ": " + formatSample(metric, sample);
    text(4, y, trim(line, 24));

    if (sample == null || !sample.available()) {
      set(4, y + 8, 118, 3, new TinyColor(42, 42, 42));
      return;
    }

    double value = Math.abs(scaledValue(metric, sample));
    double normalized = 1D - (1D / (1D + value));
    int width = (int) Math.round(118D * Math.max(0D, Math.min(1D, normalized)));

    set(4, y + 8, 118, 3, new TinyColor(22, 28, 34));
    if (width <= 0) {
      return;
    }

    TinyColor color = gradient(normalized, new TinyColor(58, 170, 214), new TinyColor(255, 110, 58));
    set(4, y + 8, width, 3, color);
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
    double n = Math.max(0D, Math.min(1D, normalized));
    int r = (int) Math.round((low.getColor().getRed() * (1D - n)) + (high.getColor().getRed() * n));
    int g = (int) Math.round((low.getColor().getGreen() * (1D - n)) + (high.getColor().getGreen() * n));
    int b = (int) Math.round((low.getColor().getBlue() * (1D - n)) + (high.getColor().getBlue() * n));
    return new TinyColor(r, g, b);
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

  protected record MetricLine(String key, String label, int decimals,
                              String unitSuffix, double scale) {
    protected MetricLine(String key, String label, int decimals, String unitSuffix) {
      this(key, label, decimals, unitSuffix, 1D);
    }
  }
}
