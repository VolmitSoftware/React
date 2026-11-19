package art.arcane.react.core.telemetry;

import oshi.hardware.CentralProcessor;

final class CpuLoadMeter {
  static final long MIN_INTERVAL_NANOS = 1_000_000_000L;
  private static final int IDLE = CentralProcessor.TickType.IDLE.getIndex();
  private static final int STEAL = CentralProcessor.TickType.STEAL.getIndex();
  private static final int IOWAIT = CentralProcessor.TickType.IOWAIT.getIndex();
  private static final long[] NO_TICKS = new long[0];

  private final int logicalProcessors;
  private long[] previousTicks;
  private long previousProcessCpuNanos;
  private long previousAtNanos;
  private boolean hasBaseline;
  private double systemLoad = Double.NaN;
  private double processLoad = Double.NaN;
  private double ioWaitLoad = Double.NaN;
  private double stealLoad = Double.NaN;

  CpuLoadMeter(int logicalProcessors) {
    this.logicalProcessors = Math.max(1, logicalProcessors);
  }

  void update(long atNanos, long[] ticks, long processCpuNanos) {
    long[] currentTicks = ticks == null ? NO_TICKS : ticks;
    if (!hasBaseline) {
      baseline(atNanos, currentTicks, processCpuNanos);
      return;
    }

    long elapsedNanos = atNanos - previousAtNanos;
    if (elapsedNanos < MIN_INTERVAL_NANOS) {
      return;
    }

    systemLoad = systemLoadBetween(previousTicks, currentTicks);
    ioWaitLoad = categoryLoad(previousTicks, currentTicks, IOWAIT);
    stealLoad = categoryLoad(previousTicks, currentTicks, STEAL);
    processLoad = processLoadBetween(previousProcessCpuNanos, processCpuNanos, elapsedNanos);
    baseline(atNanos, currentTicks, processCpuNanos);
  }

  double systemLoad() {
    return systemLoad;
  }

  double processLoad() {
    return processLoad;
  }

  double ioWaitLoad() {
    return ioWaitLoad;
  }

  double stealLoad() {
    return stealLoad;
  }

  private static double categoryLoad(long[] previous, long[] current, int category) {
    if (previous.length != current.length || current.length <= category) {
      return Double.NaN;
    }
    long total = 0L;
    for (int index = 0; index < current.length; index++) {
      if (current[index] < previous[index]) {
        return Double.NaN;
      }
      total += current[index] - previous[index];
    }
    return total > 0L ? (current[category] - previous[category]) / (double) total : Double.NaN;
  }

  private void baseline(long atNanos, long[] ticks, long processCpuNanos) {
    previousTicks = ticks.clone();
    previousProcessCpuNanos = processCpuNanos;
    previousAtNanos = atNanos;
    hasBaseline = true;
  }

  private static double systemLoadBetween(long[] previous, long[] current) {
    if (previous.length != current.length || current.length <= Math.max(IDLE, IOWAIT)) {
      return Double.NaN;
    }

    long total = 0L;
    for (int i = 0; i < current.length; i++) {
      if (current[i] < previous[i]) {
        return Double.NaN;
      }
      total += current[i] - previous[i];
    }
    long idle = current[IDLE] - previous[IDLE] + current[IOWAIT] - previous[IOWAIT];
    if (total <= 0L) {
      return Double.NaN;
    }

    return clampLoad((total - idle) / (double) total);
  }

  private double processLoadBetween(long previousNanos, long currentNanos, long elapsedNanos) {
    if (previousNanos < 0L || currentNanos < previousNanos) {
      return Double.NaN;
    }

    return clampLoad((currentNanos - previousNanos) / ((double) elapsedNanos * logicalProcessors));
  }

  private static double clampLoad(double load) {
    return Math.max(0D, Math.min(1D, load));
  }
}
