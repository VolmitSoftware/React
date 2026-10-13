package art.arcane.react.core.telemetry;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

final class AllocationMeter {
  private final ThreadMXBean bean;
  private long previousBytes = -1L;
  private long previousNanos;

  AllocationMeter(ThreadMXBean bean) {
    this.bean = bean;
  }

  static AllocationMeter platform() {
    return new AllocationMeter(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean ? bean : null);
  }

  double sample(long nowNanos) {
    if (bean == null || !bean.isThreadAllocatedMemorySupported() || !bean.isThreadAllocatedMemoryEnabled()) {
      previousBytes = -1L;
      return Double.NaN;
    }
    long bytes = bean.getTotalThreadAllocatedBytes();
    long elapsed = nowNanos - previousNanos;
    double value = bytes >= previousBytes && previousBytes >= 0L && elapsed > 0L
        ? (bytes - previousBytes) * 1_000_000_000D / elapsed : Double.NaN;
    previousBytes = bytes;
    previousNanos = nowNanos;
    return value;
  }
}
