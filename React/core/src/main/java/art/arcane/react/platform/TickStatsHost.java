package art.arcane.react.platform;

import art.arcane.react.content.sampler.TickTimeSource;

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
}
