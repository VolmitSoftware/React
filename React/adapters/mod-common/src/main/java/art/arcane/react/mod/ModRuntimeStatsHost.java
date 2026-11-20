package art.arcane.react.mod;

import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import art.arcane.react.core.telemetry.RuntimeTelemetry;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.RuntimeStatsHost;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Event time is per-mod cost and chunk cost comes from the chunk samples. React's async ticker has no work on mod servers
 * and Bukkit's optimisation features do not run here.
 */
final class ModRuntimeStatsHost implements RuntimeStatsHost {
    private final RuntimeTelemetry telemetry;
    private final ModChunkSamples chunks;
    private final EventMeasurement ownerCost;

    ModRuntimeStatsHost(RuntimeTelemetry telemetry, ModChunkSamples chunks, EventMeasurement ownerCost) {
        this.telemetry = telemetry;
        this.chunks = chunks;
        this.ownerCost = ownerCost;
    }

    @Override
    public double reactAsyncTickTimeMs() {
        return 0D;
    }

    @Override
    public double reactSyncTickTimeMs() {
        return telemetry.jobs().getUsage().getAverage();
    }

    @Override
    public double jobOverBudgetMs() {
        return telemetry.jobs().getOverBudget();
    }

    @Override
    public double jobQueuedComputeTimeMs() {
        return telemetry.jobs().getQueuedComputeTime();
    }

    @Override
    public int jobQueueSize() {
        return telemetry.jobs().getQueueSize();
    }

    @Override
    public HostTelemetrySnapshot hostTelemetry() {
        return telemetry.host().snapshot();
    }

    @Override
    public Optional<EventMeasurement> eventMeasurement() {
        return Optional.of(ownerCost);
    }

    @Override
    public Optional<CostShares> costShares() {
        return Optional.of(chunks.costShares());
    }

    @Override
    public OptionalDouble readAndResetFeatureCounter(FeatureCounter counter) {
        return OptionalDouble.empty();
    }
}
