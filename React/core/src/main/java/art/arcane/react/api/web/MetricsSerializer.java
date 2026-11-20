package art.arcane.react.api.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.dto.MetricDiagnosticsDto;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.platform.WebMetricsSource;

public class MetricsSerializer {

    public WebMetricsSource.Value toValue(Sampler s) {
        s.markDemand();
        Sampler.Reading reading = s.captureReading();
        double value = reading.value();
        boolean available = reading.available();
        return toValue(new MetricSnapshotValue(
            s.getId(),
            s.getName(),
            s.formattedSuffix(value),
            Double.isFinite(value) ? value : 0D,
            s.formattedValue(value),
            available,
            reading.sampledAtMs(),
            reading.metadata(),
            reading.unavailableReason(),
            s.diagnostics()
        ));
    }

    public WebMetricsSource.Value toValue(MetricSnapshotValue value) {
        return new WebMetricsSource.Value(
            value.id(),
            value.name(),
            value.suffix(),
            value.value(),
            value.display(),
            value.available(),
            value.sampledAtMs(),
            value.metadata().intervalMs(),
            value.metadata().windowMs(),
            value.metadata().quality().name(),
            value.metadata().unit(),
            value.unavailableReason(),
            diagnostics(value.diagnostics())
        );
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
