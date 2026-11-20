package art.arcane.react.core.telemetry;

import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.lang.reflect.Method;

// jdk.management.VirtualThreadSchedulerMXBean only exists on Java 24+, and mod servers on 1.21.1 run Java 21.
final class VirtualThreadScheduler {
  private static final String BEAN_CLASS = "jdk.management.VirtualThreadSchedulerMXBean";

  private final Object bean;
  private final Method parallelism;
  private final Method poolSize;
  private final Method mounted;
  private final Method queued;

  VirtualThreadScheduler(Object bean, Method parallelism, Method poolSize, Method mounted, Method queued) {
    this.bean = bean;
    this.parallelism = parallelism;
    this.poolSize = poolSize;
    this.mounted = mounted;
    this.queued = queued;
  }

  static VirtualThreadScheduler platform() {
    try {
      Class<? extends PlatformManagedObject> type = Class.forName(BEAN_CLASS).asSubclass(PlatformManagedObject.class);
      Object bean = ManagementFactory.getPlatformMXBean(type);
      if (bean == null) {
        return unavailable();
      }
      return new VirtualThreadScheduler(bean, type.getMethod("getParallelism"), type.getMethod("getPoolSize"),
          type.getMethod("getMountedVirtualThreadCount"), type.getMethod("getQueuedVirtualThreadCount"));
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      return unavailable();
    }
  }

  static VirtualThreadScheduler unavailable() {
    return new VirtualThreadScheduler(null, null, null, null, null);
  }

  double parallelism() {
    return read(parallelism);
  }

  double poolSize() {
    return read(poolSize);
  }

  double mounted() {
    return read(mounted);
  }

  double queued() {
    return read(queued);
  }

  private double read(Method accessor) {
    if (bean == null) {
      return -1D;
    }
    try {
      Object value = accessor.invoke(bean);
      return value instanceof Number number ? number.doubleValue() : -1D;
    } catch (ReflectiveOperationException | RuntimeException failure) {
      return -1D;
    }
  }
}
