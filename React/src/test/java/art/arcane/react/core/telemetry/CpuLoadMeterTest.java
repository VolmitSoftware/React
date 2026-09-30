package art.arcane.react.core.telemetry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import oshi.hardware.CentralProcessor;

class CpuLoadMeterTest {
  private static final long SECOND = 1_000_000_000L;
  private static final int IDLE = CentralProcessor.TickType.IDLE.getIndex();
  private static final int USER = CentralProcessor.TickType.USER.getIndex();
  private static final int IOWAIT = CentralProcessor.TickType.IOWAIT.getIndex();

  @Test
  void loadIsUnavailableUntilTheFirstFullInterval() {
    CpuLoadMeter meter = new CpuLoadMeter(4);
    meter.update(0L, ticks(0L, 0L, 0L), 0L);
    Assertions.assertTrue(Double.isNaN(meter.systemLoad()));
    Assertions.assertTrue(Double.isNaN(meter.processLoad()));

    meter.update(SECOND / 100L, ticks(1L, 1L, 0L), 1_000L);
    Assertions.assertTrue(Double.isNaN(meter.systemLoad()));
    Assertions.assertTrue(Double.isNaN(meter.processLoad()));
  }

  @Test
  void saturatedHostNeverReadsZeroWhenPolledRapidly() {
    CpuLoadMeter meter = new CpuLoadMeter(4);
    long user = 0L;
    long idle = 0L;
    long processNanos = 0L;
    meter.update(0L, ticks(user, idle, 0L), processNanos);
    for (long at = SECOND / 10L; at <= 30L * SECOND; at += SECOND / 10L) {
      user += 36L;
      idle += 4L;
      processNanos += SECOND / 10L;
      meter.update(at, ticks(user, idle, 0L), processNanos);
      if (at >= SECOND) {
        Assertions.assertEquals(0.9D, meter.systemLoad(), 1.0E-9D);
        Assertions.assertEquals(0.25D, meter.processLoad(), 1.0E-9D);
      }
    }
  }

  @Test
  void callsSoonerThanTheMinimumIntervalReuseTheLastReading() {
    CpuLoadMeter meter = new CpuLoadMeter(2);
    meter.update(0L, ticks(0L, 0L, 0L), 0L);
    meter.update(SECOND, ticks(50L, 50L, 0L), SECOND / 2L);
    Assertions.assertEquals(0.5D, meter.systemLoad(), 1.0E-9D);
    Assertions.assertEquals(0.25D, meter.processLoad(), 1.0E-9D);

    meter.update(SECOND + 1L, ticks(50L, 50L, 0L), SECOND / 2L);
    Assertions.assertEquals(0.5D, meter.systemLoad(), 1.0E-9D);
    Assertions.assertEquals(0.25D, meter.processLoad(), 1.0E-9D);

    meter.update(2L * SECOND, ticks(60L, 140L, 0L), SECOND / 2L);
    Assertions.assertEquals(0.1D, meter.systemLoad(), 1.0E-9D);
    Assertions.assertEquals(0D, meter.processLoad(), 1.0E-9D);
  }

  @Test
  void ioWaitCountsAsIdleTime() {
    CpuLoadMeter meter = new CpuLoadMeter(1);
    meter.update(0L, ticks(0L, 0L, 0L), 0L);
    meter.update(SECOND, ticks(25L, 50L, 25L), SECOND / 4L);

    Assertions.assertEquals(0.25D, meter.systemLoad(), 1.0E-9D);
    Assertions.assertEquals(0.25D, meter.processLoad(), 1.0E-9D);
  }

  @Test
  void unsupportedCountersReportUnavailable() {
    CpuLoadMeter meter = new CpuLoadMeter(4);
    meter.update(0L, ticks(0L, 0L, 0L), -1L);
    meter.update(SECOND, ticks(0L, 0L, 0L), -1L);

    Assertions.assertTrue(Double.isNaN(meter.systemLoad()));
    Assertions.assertTrue(Double.isNaN(meter.processLoad()));
  }

  private static long[] ticks(long user, long idle, long ioWait) {
    long[] ticks = new long[CentralProcessor.TickType.values().length];
    ticks[USER] = user;
    ticks[IDLE] = idle;
    ticks[IOWAIT] = ioWait;
    return ticks;
  }
}
