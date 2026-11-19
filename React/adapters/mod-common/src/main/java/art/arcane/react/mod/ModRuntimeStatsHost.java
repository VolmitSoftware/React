package art.arcane.react.mod;

import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.RuntimeStatsHost;

import java.util.Optional;
import java.util.OptionalDouble;

/** Event time is per-mod cost; React's job queue, ticker, chunk cost observer and features do not run on mod servers yet. */
final class ModRuntimeStatsHost implements RuntimeStatsHost {
    private final ModTicks ticks;
    private final EventMeasurement ownerCost;

    ModRuntimeStatsHost(ModTicks ticks, EventMeasurement ownerCost) {
        this.ticks = ticks;
        this.ownerCost = ownerCost;
    }

    @Override
    public double reactAsyncTickTimeMs() {
        return 0D;
    }

    @Override
    public double reactSyncTickTimeMs() {
        return 0D;
    }

    @Override
    public double jobOverBudgetMs() {
        return 0D;
    }

    @Override
    public double jobQueuedComputeTimeMs() {
        return 0D;
    }

    @Override
    public int jobQueueSize() {
        return ticks.pending();
    }

    @Override
    public HostTelemetrySnapshot hostTelemetry() {
        return HostTelemetrySnapshot.empty();
    }

    @Override
    public Optional<EventMeasurement> eventMeasurement() {
        return Optional.of(ownerCost);
    }

    @Override
    public Optional<CostShares> costShares() {
        return Optional.empty();
    }

    @Override
    public OptionalDouble readAndResetFeatureCounter(FeatureCounter counter) {
        return OptionalDouble.empty();
    }
}
