package art.arcane.react.core.telemetry;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;

final class AllocationMeter {
  private static final Method TOTAL_ALLOCATED = totalAllocated();

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
    long bytes = totalBytes();
    long elapsed = nowNanos - previousNanos;
    double value = bytes >= previousBytes && previousBytes >= 0L && elapsed > 0L
        ? (bytes - previousBytes) * 1_000_000_000D / elapsed : Double.NaN;
    previousBytes = bytes;
    previousNanos = nowNanos;
    return value;
  }

  // getTotalThreadAllocatedBytes arrived in Java 21; on Java 17 the live threads' counters are summed instead.
  private long totalBytes() {
    if (TOTAL_ALLOCATED != null) {
      try {
        return (long) TOTAL_ALLOCATED.invoke(bean);
      } catch (ReflectiveOperationException | RuntimeException failure) {
        return -1L;
      }
    }
    long total = 0L;
    for (long bytes : bean.getThreadAllocatedBytes(bean.getAllThreadIds())) {
      if (bytes > 0L) {
        total += bytes;
      }
    }
    return total;
  }

  private static Method totalAllocated() {
    try {
      return ThreadMXBean.class.getMethod("getTotalThreadAllocatedBytes");
    } catch (NoSuchMethodException failure) {
      return null;
    }
  }
}
