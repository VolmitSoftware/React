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
        set(0, 0, width(), 11, accentColor());
        text(4, 2, title());

        IntegrationController controller = React.controller(IntegrationController.class);
        if (controller == null || controller.getRemoteSamplerBridge() == null) {
            text(4, 14, "Status: OFFLINE");
            text(4, 24, "Bridge unavailable");
            return;
        }

        IntegrationController.IntegrationStatus status = controller.statusFor(pluginId());
        String health = status == null ? "MISSING" : status.health().name();
        text(4, 14, "Status: " + health);

        int y = 24;
        long newestSampleMs = 0L;
        for (MetricLine metric : metricLines()) {
            IntegrationMetricSample sample = controller.getRemoteSamplerBridge().getSample(pluginId(), metric.key());
            newestSampleMs = Math.max(newestSampleMs, sample == null ? 0L : sample.sampledAtMs());
            text(4, y, metric.label() + ": " + formatSample(metric, sample));
            y += 10;
            if (y > 116) {
                return;
            }
        }

        if (status != null && status.message() != null && !status.message().isBlank()) {
            text(4, y, trim("Msg: " + status.message(), 24));
            y += 10;
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

    protected record MetricLine(String key, String label, int decimals, String unitSuffix) {
    }

    private String formatSample(MetricLine metric, IntegrationMetricSample sample) {
        if (sample == null || !sample.available()) {
            return "n/a";
        }

        double value = sample.valueOr(0D);
        String suffix = metric.unitSuffix() == null ? "" : metric.unitSuffix();
        return Form.f(value, Math.max(0, metric.decimals())) + suffix;
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
}
