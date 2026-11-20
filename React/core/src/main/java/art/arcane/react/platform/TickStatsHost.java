package art.arcane.react.platform;

import art.arcane.react.content.sampler.TickTimeSource;
import art.arcane.react.platform.timing.SaveTimings;
import art.arcane.react.platform.timing.WorldTickTimes;

import java.util.Optional;

/**
 * Server tick timing. Tick cadence itself comes from {@link SamplerEventCounters.Counter#SERVER_TICK}.
 */
public interface TickStatsHost {
  /**
   * Per-tick work time in nanoseconds, oldest first. Empty when this server exposes no tick times.
   */
  Optional<TickTimeSource> tickTimeSource();

  /**
   * Tasks queued on the platform scheduler.
   *
   * @throws UnsupportedOperationException when the scheduler cannot list pending tasks
   */
  int pendingSchedulerTasks();

  /**
   * Tick time measured per world. Empty when this server can only estimate it.
   */
  default Optional<WorldTickTimes> worldTickTimes() {
    return Optional.empty();
  }

  /**
   * Full world save durations. Empty when this server cannot time its saves.
   */
  default Optional<SaveTimings> saveTimings() {
    return Optional.empty();
  }
}
