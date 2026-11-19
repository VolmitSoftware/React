package art.arcane.react.api.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.dto.MetricHistoryDescriptorDto;
import art.arcane.react.api.web.dto.MetricRangeDto;
import art.arcane.react.api.web.dto.MetricRangeSeriesDto;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.history.ChartHistory;
import art.arcane.react.core.history.ChartRange;
import art.arcane.react.core.history.HistoryQueryResult;
import art.arcane.react.core.history.MetricDescriptor;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.platform.WebMetricHistory;
import art.arcane.react.platform.WebMetricsSource;
import art.arcane.react.util.project.registry.Registry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Sampler values from {@link HistoryController}'s latest capture, or from the sampler registry before the first one. */
public final class BukkitWebMetricsSource implements WebMetricsSource {
    private final Supplier<SampleController> samplers;
    private final Supplier<HistoryController> history;
    private final MetricsSerializer serializer;
    private volatile Converted converted;

    public BukkitWebMetricsSource(Supplier<SampleController> samplers, Supplier<HistoryController> history, MetricsSerializer serializer) {
        this.samplers = samplers;
        this.history = history;
        this.serializer = serializer;
    }

    @Override
    public Frame currentFrame() {
        HistoryController controller = history.get();
        MetricSnapshot snapshot = controller == null ? MetricSnapshot.empty() : controller.latestObserved();
        if (snapshot.values().isEmpty()) {
            return registryFrame();
        }
        Converted current = converted;
        if (current != null && current.source() == snapshot) {
            return current.frame();
        }
        List<Value> values = new ArrayList<>(snapshot.values().size());
        for (MetricSnapshotValue value : snapshot.values()) {
            values.add(serializer.toValue(value));
        }
        Frame frame = new Frame(snapshot.sequence(), snapshot.capturedAtMs(), values);
        converted = new Converted(snapshot, frame);
        return frame;
    }

    private Frame registryFrame() {
        SampleController controller = samplers.get();
        Registry<Sampler> registry = controller == null ? null : controller.getSamplers();
        if (registry == null) {
            return new Frame(0L, System.currentTimeMillis(), List.of());
        }
        Collection<Sampler> all = registry.all();
        List<Value> values = new ArrayList<>(all.size());
        for (Sampler sampler : all) {
            values.add(serializer.toValue(sampler));
        }
        return new Frame(0L, System.currentTimeMillis(), values);
    }

    @Override
    public Optional<WebMetricHistory> history() {
        HistoryController controller = history.get();
        return controller == null ? Optional.empty() : Optional.of(new History(controller));
    }

    private record Converted(MetricSnapshot source, Frame frame) {
    }

    private record History(HistoryController controller) implements WebMetricHistory {
        @Override
        public List<MetricHistoryDescriptorDto> descriptors() {
            List<MetricDescriptor> descriptors = controller.descriptors();
            MetricSnapshot latest = controller.latest();
            List<MetricHistoryDescriptorDto> data = new ArrayList<>(descriptors.size());
            for (MetricDescriptor descriptor : descriptors) {
                MetricSnapshotValue live = latest.value(descriptor.id());
                data.add(new MetricHistoryDescriptorDto(
                    descriptor.id(),
                    descriptor.name(),
                    descriptor.suffix(),
                    descriptor.firstTimestampMs(),
                    descriptor.lastTimestampMs(),
                    descriptor.active(),
                    descriptor.metadata().intervalMs(),
                    descriptor.metadata().windowMs(),
                    descriptor.metadata().quality().name(),
                    descriptor.metadata().unit(),
                    live != null && live.available(),
                    live == null || live.available() ? null : live.unavailableReason()
                ));
            }
            return data;
        }

        @Override
        public boolean knowsMetric(String id) {
            return controller.knowsMetric(id);
        }

        @Override
        public int maxQueryPoints() {
            return controller.effectiveMaxQueryPoints();
        }

        @Override
        public int queryPagePoints() {
            return controller.effectiveQueryPagePoints();
        }

        @Override
        public int maxQuerySeries() {
            return controller.effectiveMaxQuerySeries();
        }

        @Override
        public long selectResolution(long fromMs, long toMs, int maxPoints) {
            return controller.selectResolution(fromMs, toMs, maxPoints);
        }

        @Override
        public Capture latestCapture() {
            MetricSnapshot latest = controller.latest();
            return new Capture(latest.sequence(), latest.capturedAtMs());
        }

        @Override
        public HistoryQueryResult query(List<String> ids, long fromMs, long toMs, long resolutionMs, long throughSequence,
                                        long throughMs) throws IOException {
            return controller.query(ids, fromMs, toMs, resolutionMs, throughSequence, throughMs);
        }

        @Override
        public MetricRangeDto range(ChartRange range, List<String> ids, long nowMs) {
            ChartHistory.Window window = controller.chartRange(range, ids, nowMs);
            MetricRangeSeriesDto[] series = new MetricRangeSeriesDto[window.series().size()];
            for (int index = 0; index < series.length; index++) {
                ChartHistory.Series value = window.series().get(index);
                series[index] = new MetricRangeSeriesDto(value.id(), value.values());
            }
            return new MetricRangeDto(range.id(), range.stepMs(), window.fromMs(), window.toMs(), series);
        }
    }
}
