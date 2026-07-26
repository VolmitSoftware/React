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
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.Locale;

abstract class RendererIntegrationMetricsBase implements ReactRenderer {
  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.adaptive(4, 4);
  }

  @Override
  public void render() {
    int s = uiScale();
    RendererLayout.backdrop(this, backgroundColor(), null);
    dashHeader(ReactLanguage.raw(title()), headerValue(), accentColor());

    Region body = RendererLayout.body(this);
    IntegrationController controller = React.controller(IntegrationController.class);
    if (controller == null || controller.getRemoteSamplerBridge() == null) {
      RendererLayout.emptyState(
          this,
          body,
          statusLine(ReactLanguage.raw(RendererMessages.STATUS_OFFLINE)),
          ReactLanguage.raw(RendererMessages.BRIDGE_UNAVAILABLE)
      );
      RendererLayout.footer(this, null, null, accentColor());
      return;
    }

    RemoteSamplerBridge bridge = controller.getRemoteSamplerBridge();
    IntegrationController.IntegrationStatus status = controller.statusFor(pluginId());
    String health = status == null ? "MISSING" : status.health().name();

    Region statusRow = body.topBand(MapTheme.lineHeight(s) + (2 * s));
    panel(statusRow, healthColor(health), null);
    textIn(statusRow, MapTheme.pad(s), s, statusLine(RendererMessages.health(health)), MapTheme.TEXT_STRONG);

    Region grid = body.withoutTop(statusRow.height() + MapTheme.gutter(s));
    long newestSampleMs = drawMetricGrid(bridge, grid, s);

    String left = status != null && status.message() != null && !status.message().isBlank()
        ? ReactLanguage.raw(RendererMessages.MESSAGE_LINE, MessageArgument.untrusted("message", status.message()))
        : null;
    String right = newestSampleMs <= 0L
        ? null
        : ReactLanguage.raw(
            RendererMessages.AGE_LINE,
            MessageArgument.untrusted("age", Form.f(Math.max(0D, (System.currentTimeMillis() - newestSampleMs) / 1000D), 1))
        );
    RendererLayout.footer(this, left, right, accentColor());
  }

  private long drawMetricGrid(RemoteSamplerBridge bridge, Region grid, int scale) {
    List<MetricLine> metrics = metricLines();
    if (metrics.isEmpty() || grid.isEmpty()) {
      return 0L;
    }

    int gutter = MapTheme.gutter(scale);
    int cellHeight = MapTheme.lineHeight(scale) + (7 * scale);
    int columns = Math.max(1, Math.min(megamapColumns(), grid.width() / (76 * scale)));
    Region[] cells = grid.splitColumns(gutter, equalWeights(columns));
    int rows = Math.max(1, cells[0].rowCapacity(cellHeight));

    long newestSampleMs = 0L;
    int placed = 0;
    for (MetricLine metric : metrics) {
      if (placed >= columns * rows) {
        break;
      }

      IntegrationMetricSample sample = metricSample(bridge, metric);
      newestSampleMs = Math.max(newestSampleMs, sample == null ? 0L : sample.sampledAtMs());
      Region cell = cells[placed / rows].rowAt(placed % rows, cellHeight);
      placed++;
      if (!visible(cell)) {
        continue;
      }

      drawMetricCell(metric, sample, cell, scale);
    }

    return newestSampleMs;
  }

  private void drawMetricCell(MetricLine metric, IntegrationMetricSample sample, Region cell, int scale) {
    Region line = cell.topBand(MapTheme.lineHeight(scale));
    Region[] halves = line.splitColumns(MapTheme.gutter(scale), 58, 42);
    textIn(halves[0], 0, 0, metricLabel(metric), MapTheme.TEXT);
    textRightIn(halves[1], 0, 0, formatSample(metric, sample), MapTheme.TEXT_SOFT);

    Region track = cell.withoutTop(line.height() + scale).topBand(3 * scale);
    if (sample == null || !sample.available()) {
      fill(track, MapTheme.LINE);
      return;
    }

    double value = Math.abs(scaledValue(metric, sample));
    double normalized = 1D - (1D / (1D + value));
    RendererLayout.bar(this, track, normalized, MapTheme.severity(normalized));
  }

  private int[] equalWeights(int columns) {
    int[] weights = new int[columns];
    for (int i = 0; i < columns; i++) {
      weights[i] = 1;
    }
    return weights;
  }

  protected abstract String pluginId();

  protected abstract TextKey title();

  protected abstract TinyColor backgroundColor();

  protected abstract TinyColor accentColor();

  protected abstract List<MetricLine> metricLines();

  protected String headerValue() {
    return null;
  }

  protected IntegrationMetricSample metricSample(RemoteSamplerBridge bridge, MetricLine metric) {
    return bridge.getSample(pluginId(), metric.key());
  }

  protected String metricLabel(MetricLine metric) {
    return ReactLanguage.raw(metric.labelKey());
  }

  private String formatSample(MetricLine metric, IntegrationMetricSample sample) {
    if (sample == null || !sample.available()) {
      return ReactLanguage.raw(RendererMessages.VALUE_NOT_AVAILABLE);
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

  private TinyColor healthColor(String health) {
    if (health == null) {
      return MapTheme.LINE_STRONG;
    }

    return switch (health.toUpperCase(Locale.ROOT)) {
      case "HEALTHY", "ONLINE", "OK" -> MapTheme.OK;
      case "DEGRADED", "WARN" -> MapTheme.WARN;
      case "UNAVAILABLE", "MISSING", "OFFLINE", "ERROR" -> MapTheme.BAD;
      default -> MapTheme.LINE_STRONG;
    };
  }

  private String statusLine(String status) {
    return ReactLanguage.raw(
        RendererMessages.STATUS_LINE,
        MessageArgument.untrusted("status", status)
    );
  }

  protected record MetricLine(String key, TextKey labelKey, int decimals,
                              String unitSuffix, double scale) {
    protected MetricLine(String key, TextKey labelKey, int decimals, String unitSuffix) {
      this(key, labelKey, decimals, unitSuffix, 1D);
    }
  }
}
