package art.arcane.react.core.telemetry;

import art.arcane.react.api.sampler.Sampler;

public enum HostMetric {
  PHYSICAL_USED(new Definition("physical-memory-used", "Physical Memory Used", Source.MEMORY, TelemetrySampler.Format.BYTES, "bytes", 0L, Sampler.Quality.MEASURED, true)),
  PHYSICAL_FREE(new Definition("physical-memory-free", "Physical Memory Available", Source.MEMORY, TelemetrySampler.Format.BYTES, "bytes", 0L, Sampler.Quality.MEASURED, true)),
  DISK_USABLE(new Definition("disk-usable", "Disk Usable", Source.STORAGE, TelemetrySampler.Format.BYTES, "bytes", 0L, Sampler.Quality.MEASURED, true)),
  DISK_READ(new Definition("disk-read-rate", "Disk Read Rate", Source.STORAGE, TelemetrySampler.Format.BYTES_PER_SECOND, "bytes/s", 30000L, Sampler.Quality.MEASURED, true)),
  DISK_WRITE(new Definition("disk-write-rate", "Disk Write Rate", Source.STORAGE, TelemetrySampler.Format.BYTES_PER_SECOND, "bytes/s", 30000L, Sampler.Quality.MEASURED, true)),
  DISK_READ_OPS(new Definition("disk-read-operations-rate", "Disk Read Operations", Source.STORAGE, TelemetrySampler.Format.DECIMAL, "operations/s", 30000L, Sampler.Quality.MEASURED, true)),
  DISK_WRITE_OPS(new Definition("disk-write-operations-rate", "Disk Write Operations", Source.STORAGE, TelemetrySampler.Format.DECIMAL, "operations/s", 30000L, Sampler.Quality.MEASURED, true)),
  DISK_QUEUE(new Definition("disk-queue-length", "Disk Queue Length", Source.STORAGE, TelemetrySampler.Format.COUNT, "operations", 0L, Sampler.Quality.MEASURED, true)),
  DISK_BUSY(new Definition("disk-transfer-time-rate", "Disk Transfer Time Rate", Source.STORAGE, TelemetrySampler.Format.DECIMAL, "ms/s", 30000L, Sampler.Quality.MEASURED, true)),
  NETWORK_RECEIVE(new Definition("network-receive-rate", "Network Receive Rate", Source.NETWORK, TelemetrySampler.Format.BYTES_PER_SECOND, "bytes/s", 10000L, Sampler.Quality.MEASURED, true)),
  NETWORK_SEND(new Definition("network-send-rate", "Network Send Rate", Source.NETWORK, TelemetrySampler.Format.BYTES_PER_SECOND, "bytes/s", 10000L, Sampler.Quality.MEASURED, true)),
  NETWORK_DROPS(new Definition("network-receive-drops", "Network Receive Drops", Source.NETWORK, TelemetrySampler.Format.COUNT, "drops", 0L, Sampler.Quality.MEASURED, true)),
  NETWORK_IN_ERRORS(new Definition("network-receive-errors", "Network Receive Errors", Source.NETWORK, TelemetrySampler.Format.COUNT, "errors", 0L, Sampler.Quality.MEASURED, true)),
  NETWORK_OUT_ERRORS(new Definition("network-send-errors", "Network Send Errors", Source.NETWORK, TelemetrySampler.Format.COUNT, "errors", 0L, Sampler.Quality.MEASURED, true)),
  NETWORK_RECEIVE_PACKETS(new Definition("network-receive-packets-rate", "Network Receive Packets", Source.NETWORK, TelemetrySampler.Format.DECIMAL, "packets/s", 10000L, Sampler.Quality.MEASURED, true)),
  NETWORK_SEND_PACKETS(new Definition("network-send-packets-rate", "Network Send Packets", Source.NETWORK, TelemetrySampler.Format.DECIMAL, "packets/s", 10000L, Sampler.Quality.MEASURED, true)),
  NETWORK_COLLISIONS(new Definition("network-collisions", "Network Collisions", Source.NETWORK, TelemetrySampler.Format.COUNT, "collisions", 0L, Sampler.Quality.MEASURED, true)),
  HEAP_MAX(new Definition("jvm-heap-max", "JVM Heap Max", Source.JVM, TelemetrySampler.Format.BYTES, "bytes", 0L, Sampler.Quality.MEASURED, true)),
  HEAP_COMMITTED(new Definition("jvm-heap-committed", "JVM Heap Committed", Source.JVM, TelemetrySampler.Format.BYTES, "bytes", 0L, Sampler.Quality.MEASURED, true)),
  HEAP_UTILIZATION(new Definition("jvm-heap-utilization", "JVM Heap Utilization", Source.JVM, TelemetrySampler.Format.PERCENT, "%", 0L, Sampler.Quality.MEASURED, true)),
  NONHEAP_USED(new Definition("jvm-nonheap-used", "JVM Non-Heap Used", Source.JVM, TelemetrySampler.Format.BYTES, "bytes", 0L, Sampler.Quality.MEASURED, true)),
  DIRECT_BYTES(new Definition("jvm-direct-buffer-bytes", "JVM Direct Buffer Memory", Source.JVM, TelemetrySampler.Format.BYTES, "bytes", 0L, Sampler.Quality.ESTIMATED, true)),
  DIRECT_COUNT(new Definition("jvm-direct-buffer-count", "JVM Direct Buffer Count", Source.JVM, TelemetrySampler.Format.COUNT, "buffers", 0L, Sampler.Quality.MEASURED, true)),
  GC_RATE(new Definition("jvm-gc-collections-rate", "JVM GC Collections", Source.JVM, TelemetrySampler.Format.DECIMAL, "collections/min", 60000L, Sampler.Quality.MEASURED, true)),
  LOADED_CLASSES(new Definition("jvm-loaded-classes", "JVM Loaded Classes", Source.JVM, TelemetrySampler.Format.COUNT, "classes", 0L, Sampler.Quality.MEASURED, true)),
  UPTIME(new Definition("jvm-process-uptime", "JVM Process Uptime", Source.JVM, TelemetrySampler.Format.COUNT, "ms", 0L, Sampler.Quality.MEASURED, true)),
  ALLOCATION(new Definition("memory-pressure", "JVM Allocation Rate", Source.ALLOCATION, TelemetrySampler.Format.BYTES_PER_SECOND, "bytes/s", 1000L, Sampler.Quality.ESTIMATED, false)),
  CPU_SYSTEM(new Definition("processor-system-load", "System CPU Load", Source.CPU, TelemetrySampler.Format.DECIMAL, "ratio", 1000L, Sampler.Quality.MEASURED, false)),
  CPU_PROCESS(new Definition("processor-process-load", "Process CPU Load", Source.CPU, TelemetrySampler.Format.DECIMAL, "ratio", 1000L, Sampler.Quality.MEASURED, false)),
  CPU_IOWAIT(new Definition("processor-io-wait", "CPU I/O Wait", Source.CPU, TelemetrySampler.Format.PERCENT, "%", 1000L, Sampler.Quality.MEASURED, true)),
  CPU_STEAL(new Definition("processor-steal", "CPU Steal", Source.CPU, TelemetrySampler.Format.PERCENT, "%", 1000L, Sampler.Quality.MEASURED, true)),
  CPU_AVAILABLE(new Definition("jvm-available-processors", "JVM Available Processors", Source.CPU, TelemetrySampler.Format.COUNT, "processors", 0L, Sampler.Quality.MEASURED, true)),
  CPU_EFFECTIVE(new Definition("processor-effective-load", "Process CPU Capacity Load", Source.CPU, TelemetrySampler.Format.PERCENT, "%", 1000L, Sampler.Quality.MEASURED, true)),
  RSS(new Definition("process-resident-memory", "Process Resident Memory", Source.PROCESS, TelemetrySampler.Format.BYTES, "bytes", 0L, Sampler.Quality.ESTIMATED, true)),
  PROCESS_READ(new Definition("process-io-read-rate", "Process I/O Read Rate", Source.PROCESS, TelemetrySampler.Format.BYTES_PER_SECOND, "bytes/s", 5000L, Sampler.Quality.MEASURED, true)),
  PROCESS_WRITE(new Definition("process-io-write-rate", "Process I/O Write Rate", Source.PROCESS, TelemetrySampler.Format.BYTES_PER_SECOND, "bytes/s", 5000L, Sampler.Quality.MEASURED, true)),
  MINOR_FAULTS(new Definition("process-minor-faults-rate", "Process Minor Fault Rate", Source.PROCESS, TelemetrySampler.Format.DECIMAL, "faults/s", 5000L, Sampler.Quality.MEASURED, true)),
  MAJOR_FAULTS(new Definition("process-major-faults-rate", "Process Major Fault Rate", Source.PROCESS, TelemetrySampler.Format.DECIMAL, "faults/s", 5000L, Sampler.Quality.MEASURED, true)),
  TOTAL_FAULTS(new Definition("process-page-faults-rate", "Process Page Fault Rate", Source.PROCESS, TelemetrySampler.Format.DECIMAL, "faults/s", 5000L, Sampler.Quality.MEASURED, true)),
  OPEN_FILES(new Definition("process-open-files", "Process Open Files", Source.DESCRIPTORS, TelemetrySampler.Format.COUNT, "files", 0L, Sampler.Quality.MEASURED, true)),
  FILE_LIMIT(new Definition("process-file-limit", "Process File Limit", Source.DESCRIPTORS, TelemetrySampler.Format.COUNT, "files", 0L, Sampler.Quality.MEASURED, true)),
  FILE_HEADROOM(new Definition("process-file-headroom", "Process File Headroom", Source.DESCRIPTORS, TelemetrySampler.Format.COUNT, "files", 0L, Sampler.Quality.MEASURED, true)),
  FILE_UTILIZATION(new Definition("process-file-utilization", "Process File Utilization", Source.DESCRIPTORS, TelemetrySampler.Format.PERCENT, "%", 0L, Sampler.Quality.MEASURED, true)),
  SWAP_TOTAL(new Definition("swap-total", "Swap Total", Source.MEMORY, TelemetrySampler.Format.BYTES, "bytes", 0L, Sampler.Quality.MEASURED, true)),
  SWAP_USED(new Definition("swap-used", "Swap Used", Source.MEMORY, TelemetrySampler.Format.BYTES, "bytes", 0L, Sampler.Quality.MEASURED, true)),
  SWAP_IN(new Definition("swap-pages-in-rate", "Swap Pages In", Source.MEMORY, TelemetrySampler.Format.DECIMAL, "pages/s", 5000L, Sampler.Quality.MEASURED, true)),
  SWAP_OUT(new Definition("swap-pages-out-rate", "Swap Pages Out", Source.MEMORY, TelemetrySampler.Format.DECIMAL, "pages/s", 5000L, Sampler.Quality.MEASURED, true)),
  THREAD_LIVE(new Definition("jvm-threads", "JVM Platform Threads", Source.THREADS, TelemetrySampler.Format.COUNT, "threads", 0L, Sampler.Quality.MEASURED, false)),
  THREAD_DAEMON(new Definition("jvm-threads-daemon", "JVM Daemon Threads", Source.THREADS, TelemetrySampler.Format.COUNT, "threads", 0L, Sampler.Quality.MEASURED, true)),
  THREAD_PEAK(new Definition("jvm-threads-peak", "JVM Peak Threads", Source.THREADS, TelemetrySampler.Format.COUNT, "threads", 0L, Sampler.Quality.MEASURED, true)),
  THREAD_STARTED(new Definition("jvm-thread-start-rate", "JVM Thread Starts", Source.THREADS, TelemetrySampler.Format.DECIMAL, "threads/s", 5000L, Sampler.Quality.MEASURED, true)),
  THREAD_RUNNABLE(new Definition("jvm-threads-runnable", "JVM Runnable Threads", Source.THREADS, TelemetrySampler.Format.COUNT, "threads", 0L, Sampler.Quality.MEASURED, true)),
  THREAD_BLOCKED(new Definition("jvm-threads-blocked", "JVM Blocked Threads", Source.THREADS, TelemetrySampler.Format.COUNT, "threads", 0L, Sampler.Quality.MEASURED, true)),
  THREAD_WAITING(new Definition("jvm-threads-waiting", "JVM Waiting Threads", Source.THREADS, TelemetrySampler.Format.COUNT, "threads", 0L, Sampler.Quality.MEASURED, true)),
  VIRTUAL_PARALLELISM(new Definition("jvm-virtual-parallelism", "Virtual Thread Parallelism", Source.THREADS, TelemetrySampler.Format.COUNT, "threads", 0L, Sampler.Quality.MEASURED, true)),
  VIRTUAL_CARRIERS(new Definition("jvm-virtual-carriers", "Virtual Thread Carriers", Source.THREADS, TelemetrySampler.Format.COUNT, "threads", 0L, Sampler.Quality.MEASURED, true)),
  VIRTUAL_MOUNTED(new Definition("jvm-virtual-mounted", "Mounted Virtual Threads", Source.THREADS, TelemetrySampler.Format.COUNT, "threads", 0L, Sampler.Quality.ESTIMATED, true)),
  VIRTUAL_QUEUED(new Definition("jvm-virtual-queued", "Queued Virtual Threads", Source.THREADS, TelemetrySampler.Format.COUNT, "threads", 0L, Sampler.Quality.ESTIMATED, true)),
  GC_AFTER(new Definition("memory-used-after-gc", "Memory Used After GC", Source.GC, TelemetrySampler.Format.BYTES, "bytes", 0L, Sampler.Quality.MEASURED, false)),
  GC_RECLAIMED(new Definition("gc-reclaimed-bytes", "Latest GC Reclaimed Heap", Source.GC, TelemetrySampler.Format.BYTES, "bytes", 0L, Sampler.Quality.MEASURED, true)),
  GC_LATEST(new Definition("gc-pause-latest", "Latest GC Pause", Source.GC, TelemetrySampler.Format.DECIMAL, "ms", 0L, Sampler.Quality.MEASURED, true)),
  GC_MAX(new Definition("gc-pause-max", "Maximum GC Pause", Source.GC, TelemetrySampler.Format.DECIMAL, "ms", 300000L, Sampler.Quality.MEASURED, true));

  private final Definition definition;

  HostMetric(Definition definition) {
    this.definition = definition;
  }

  public Definition definition() {
    return definition;
  }

  public Sampler.Metadata metadata() {
    long interval = definition.source() == Source.GC && this != GC_MAX ? 0L : definition.source().intervalMs();
    return new Sampler.Metadata(interval, definition.windowMs(), definition.quality(), definition.unit());
  }

  public enum Source {
    JVM(1_000L), ALLOCATION(1_000L), CPU(1_000L), MEMORY(5_000L), PROCESS(5_000L),
    THREADS(5_000L), STORAGE(30_000L), NETWORK(10_000L), DESCRIPTORS(30_000L), GC(1_000L), DETAILS(10_000L);

    private final long intervalMs;

    Source(long intervalMs) {
      this.intervalMs = intervalMs;
    }

    public long intervalMs() {
      return intervalMs;
    }
  }

  public record Definition(String id, String name, Source source, TelemetrySampler.Format format,
                           String unit, long windowMs, Sampler.Quality quality, boolean registered) {
  }
}
