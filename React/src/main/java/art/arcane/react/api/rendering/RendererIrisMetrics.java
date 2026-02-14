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

public class RendererIrisMetrics extends RendererIntegrationMetricsBase {
    public static final String ID = "iris-metrics";

    private static final List<MetricLine> METRICS = List.of(
            new MetricLine(IntegrationMetricSchema.IRIS_PREGEN_QUEUE, "Queue", 0, " ch"),
            new MetricLine(IntegrationMetricSchema.IRIS_CHUNK_STREAM_MS, "Stream", 2, " ms"),
            new MetricLine(IntegrationMetricSchema.IRIS_BIOME_CACHE_HIT_RATE, "BiomeHit", 3, "")
    );

    @Override
    public String getId() {
        return ID;
    }

    @Override
    protected String pluginId() {
        return "iris";
    }

    @Override
    protected String title() {
        return "Iris Metrics";
    }

    @Override
    protected TinyColor backgroundColor() {
        return new TinyColor(10, 22, 18);
    }

    @Override
    protected TinyColor accentColor() {
        return new TinyColor(55, 145, 100);
    }

    @Override
    protected List<MetricLine> metricLines() {
        return METRICS;
    }
}
