package art.arcane.react.core.telemetry;

/** Lifetime totals of React's server-thread task runner, read by the runtime diagnostics samplers. */
public record TickerDiagnostics(long submitted, long executed, long failures, long skipped, long rejected,
                                long backedOff, long runtimeNanos, long queueNanos, long maxQueueNanos, int active) {
}
