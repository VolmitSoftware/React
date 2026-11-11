package art.arcane.react.util.common.scheduling;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

/** Tick-time and task-rate averages the ticker loop feeds into its RollingSequences. */
class TickerRollingMetricsTest {

  @Test
  void metricsAreZeroBeforeTheLoopRuns() {
    Ticker ticker = new Ticker(Runnable::run);
    try {
      ticker.tick();

      Assertions.assertEquals(0D, ticker.getTickTime());
      Assertions.assertEquals(0D, ticker.getTasksPerSecond());
    } finally {
      ticker.close();
    }
  }

  @Test
  void loopAveragesTickTimeAndTaskRateThenDrainsToZeroWhenIdle() {
    ClockTicked ticked = new ClockTicked(System::currentTimeMillis, "rolling-metrics", 50L, () -> pause(4L));
    Ticker ticker = new Ticker(Runnable::run);
    try {
      ticker.register(ticked);
      ticker.start();

      Assertions.assertTrue(await(() -> ticker.getTasksPerSecond() > 0D, 5_000L), "task rate never published");
      double rate = ticker.getTasksPerSecond();
      double tickTime = ticker.getTickTime();
      Assertions.assertTrue(rate >= 5D && rate <= 25D, "tasks per second " + rate);
      Assertions.assertTrue(tickTime >= 1D && tickTime < 50D, "tick time " + tickTime);

      ticker.unregister(ticked);
      Assertions.assertTrue(await(() -> Math.abs(ticker.getTickTime()) < 1.0E-9D, 5_000L),
          "idle tick time stayed at " + ticker.getTickTime());
    } finally {
      ticker.close();
    }
  }

  private static boolean await(BooleanSupplier condition, long timeoutMs) {
    long deadline = System.nanoTime() + (timeoutMs * 1_000_000L);
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return true;
      }
      pause(10L);
    }
    return condition.getAsBoolean();
  }

  private static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
