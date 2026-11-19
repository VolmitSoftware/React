package art.arcane.react.platform;

import art.arcane.react.core.telemetry.HostTelemetrySnapshot;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * React's own runtime figures: job scheduler, async ticker, host telemetry, event profiling,
 * chunk cost attribution and optimisation feature counters.
 */
public interface RuntimeStatsHost {
  double reactAsyncTickTimeMs();

  double reactSyncTickTimeMs();

  double jobOverBudgetMs();

  double jobQueuedComputeTimeMs();

  int jobQueueSize();

  HostTelemetrySnapshot hostTelemetry();

  Optional<EventMeasurement> eventMeasurement();

  /**
   * Current chunk cost attribution. Empty when cost observation is not running.
   */
  Optional<CostShares> costShares();

  /**
   * Reads and clears a feature counter. Empty when the feature is not loaded.
   */
  OptionalDouble readAndResetFeatureCounter(FeatureCounter counter);

  /**
   * Total attributed cost and the largest single chunk and world, in the same unit.
   */
  record CostShares(double total, double maxChunk, double maxWorld) {
  }

  enum FeatureCounter {
    CROP_BLOCKS_ADVANCED,
    EXPLOSIONS,
    EXPLOSION_CLUSTERS,
    HOPPER_CHAIN_TICKS_SAVED,
    LAZY_GRAVITY_PROJECTED_SKIPPED_TICKS,
    SPAWNER_LIGHT_CACHE_SKIPPED,
    PDC_WRITES_DEFERRED
  }
}
