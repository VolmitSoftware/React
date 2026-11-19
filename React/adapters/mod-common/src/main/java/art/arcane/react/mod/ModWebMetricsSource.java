package art.arcane.react.mod;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.dto.MetricDiagnosticsDto;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.platform.WebMetricsSource;
import art.arcane.react.util.project.registry.Registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Captures every sampler once a second, numbering each capture so {@code /ws/metrics} pushes it. No history is kept on
 * mod servers yet, so the history routes answer as unavailable.
 */
final class ModWebMetricsSource implements WebMetricsSource {
    private final Supplier<SampleController> samplers;
    private final AtomicBoolean capturing = new AtomicBoolean();
    private volatile Frame latest;
    private long sequence;

    ModWebMetricsSource(Supplier<SampleController> samplers) {
        this.samplers = samplers;
    }

    @Override
    public Frame currentFrame() {
        Frame frame = latest;
        return frame != null ? frame : new Frame(0L, System.currentTimeMillis(), readAll());
    }

    /** Runs one capture unless the previous one is still reading; call off the server thread. */
    void capture() {
        if (!capturing.compareAndSet(false, true)) {
            return;
        }
        try {
            List<Value> values = readAll();
            if (!values.isEmpty()) {
                latest = new Frame(++sequence, System.currentTimeMillis(), values);
            }
        } finally {
            capturing.set(false);
        }
    }

    private List<Value> readAll() {
        SampleController controller = samplers.get();
        Registry<Sampler> registry = controller == null ? null : controller.getSamplers();
        if (registry == null) {
            return List.of();
        }
        Collection<Sampler> all = registry.all();
        List<Value> values = new ArrayList<>(all.size());
        for (Sampler sampler : all) {
            values.add(value(sampler));
        }
        return values;
    }

    static Value value(Sampler sampler) {
        sampler.markDemand();
        Sampler.Reading reading = sampler.captureReading();
        double value = reading.value();
        Sampler.Metadata metadata = reading.metadata();
        return new Value(sampler.getId(), sampler.getName(), sampler.formattedSuffix(value), Double.isFinite(value) ? value : 0D,
                sampler.formattedValue(value), reading.available(), reading.sampledAtMs(), metadata.intervalMs(), metadata.windowMs(),
                metadata.quality().name(), metadata.unit(), reading.unavailableReason(), diagnostics(sampler.diagnostics()));
    }

    private static MetricDiagnosticsDto diagnostics(Sampler.Diagnostics d) {
        if (d == null || d == Sampler.Diagnostics.EMPTY) {
            return null;
        }
        return new MetricDiagnosticsDto(d.attempts(), d.successes(), d.failures(), d.cacheHits(), d.inFlightSkips(),
                d.lastDurationNanos(), d.maxDurationNanos(), d.lastQueueNanos(), d.maxQueueNanos(), d.lastSuccessAtMs(),
                d.totalDurationNanos(), d.totalQueueNanos());
    }
}
