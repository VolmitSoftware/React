package art.arcane.react.api.web.dto;

public record MetricDiagnosticsDto(long attempts, long successes, long failures, long cacheHits, long inFlightSkips,
                                   long lastDurationNanos, long maxDurationNanos, long lastQueueNanos, long maxQueueNanos,
                                   long lastSuccessAtMs, long totalDurationNanos, long totalQueueNanos) {
}
