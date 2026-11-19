package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.content.feature.FeatureCropFastForward;
import art.arcane.react.content.feature.FeatureExplosionPacketBatching;
import art.arcane.react.content.feature.FeatureHopperChainCoalescing;
import art.arcane.react.content.feature.FeatureLazyGravity;
import art.arcane.react.content.feature.FeatureSpawnerLightCache;
import art.arcane.react.core.controller.EventController;
import art.arcane.react.core.controller.JobController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.PdcWriteBatcher;
import art.arcane.react.core.controller.TelemetryController;
import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import art.arcane.react.model.CostSnapshot;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.RuntimeStatsHost;

import java.util.Optional;
import java.util.OptionalDouble;

final class BukkitRuntimeStatsHost implements RuntimeStatsHost {
  @Override
  public double reactAsyncTickTimeMs() {
    return React.ticker.getTickTime();
  }

  @Override
  public double reactSyncTickTimeMs() {
    return React.controller(JobController.class).getUsage().getAverage();
  }

  @Override
  public double jobOverBudgetMs() {
    return React.controller(JobController.class).getOverBudget();
  }

  @Override
  public double jobQueuedComputeTimeMs() {
    return React.controller(JobController.class).getQueuedComputeTime();
  }

  @Override
  public int jobQueueSize() {
    return React.controller(JobController.class).getQueueSize();
  }

  @Override
  public HostTelemetrySnapshot hostTelemetry() {
    return TelemetryController.currentHostSnapshot();
  }

  @Override
  public Optional<EventMeasurement> eventMeasurement() {
    return Optional.ofNullable(React.controller(EventController.class));
  }

  @Override
  public Optional<CostShares> costShares() {
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null) {
      return Optional.empty();
    }
    CostSnapshot snapshot = observer.costSnapshot();
    return Optional.of(new CostShares(snapshot.total(), snapshot.maxChunk(), snapshot.maxWorld()));
  }

  @Override
  public OptionalDouble readAndResetFeatureCounter(FeatureCounter counter) {
    return switch (counter) {
      case CROP_BLOCKS_ADVANCED -> {
        FeatureCropFastForward feature = React.feature(FeatureCropFastForward.class);
        yield feature == null ? OptionalDouble.empty() : OptionalDouble.of(feature.readAndResetBlocksAdvanced());
      }
      case EXPLOSIONS -> {
        FeatureExplosionPacketBatching feature = React.feature(FeatureExplosionPacketBatching.class);
        yield feature == null ? OptionalDouble.empty() : OptionalDouble.of(feature.readAndResetExplosions());
      }
      case EXPLOSION_CLUSTERS -> {
        FeatureExplosionPacketBatching feature = React.feature(FeatureExplosionPacketBatching.class);
        yield feature == null ? OptionalDouble.empty() : OptionalDouble.of(feature.readAndResetClusters());
      }
      case HOPPER_CHAIN_TICKS_SAVED -> {
        FeatureHopperChainCoalescing feature = React.feature(FeatureHopperChainCoalescing.class);
        yield feature == null ? OptionalDouble.empty() : OptionalDouble.of(feature.readAndResetTicksSaved());
      }
      case LAZY_GRAVITY_PROJECTED_SKIPPED_TICKS -> {
        FeatureLazyGravity feature = React.feature(FeatureLazyGravity.class);
        yield feature == null ? OptionalDouble.empty() : OptionalDouble.of(feature.readAndResetProjectedSkippedTicks());
      }
      case SPAWNER_LIGHT_CACHE_SKIPPED -> {
        FeatureSpawnerLightCache feature = React.feature(FeatureSpawnerLightCache.class);
        yield feature == null ? OptionalDouble.empty() : OptionalDouble.of(feature.readAndResetSkipped());
      }
      case PDC_WRITES_DEFERRED -> {
        PdcWriteBatcher batcher = React.controller(PdcWriteBatcher.class);
        yield batcher == null ? OptionalDouble.empty() : OptionalDouble.of(batcher.readAndResetDeferred());
      }
    };
  }
}
