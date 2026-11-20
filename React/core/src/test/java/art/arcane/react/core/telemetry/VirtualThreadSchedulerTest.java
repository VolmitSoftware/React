package art.arcane.react.core.telemetry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VirtualThreadSchedulerTest {
  @Test
  void platformBeanReportsSchedulerOnCurrentRuntime() {
    VirtualThreadScheduler scheduler = VirtualThreadScheduler.platform();
    Assertions.assertTrue(scheduler.parallelism() >= 1D);
    Assertions.assertTrue(scheduler.poolSize() >= 0D);
    Assertions.assertTrue(scheduler.mounted() >= 0D);
    Assertions.assertTrue(scheduler.queued() >= 0D);
  }

  @Test
  void missingBeanReportsUnavailableReadings() {
    VirtualThreadScheduler scheduler = VirtualThreadScheduler.unavailable();
    Assertions.assertEquals(-1D, scheduler.parallelism());
    Assertions.assertEquals(-1D, scheduler.poolSize());
    Assertions.assertEquals(-1D, scheduler.mounted());
    Assertions.assertEquals(-1D, scheduler.queued());
  }
}
