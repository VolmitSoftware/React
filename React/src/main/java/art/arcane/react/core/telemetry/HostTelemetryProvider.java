package art.arcane.react.core.telemetry;

import art.arcane.react.React;
import art.arcane.react.api.web.dto.EnvironmentDto;
import art.arcane.react.content.sampler.GCStatsTracker;
import art.arcane.volmlib.util.format.Form;
import com.sun.management.OperatingSystemMXBean;
import jdk.management.VirtualThreadSchedulerMXBean;
import oshi.PlatformEnum;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.PowerSource;
import oshi.hardware.Sensors;
import oshi.hardware.VirtualMemory;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

public final class HostTelemetryProvider {
  private static final long NETWORK_LIST_REFRESH_MS = 30_000L;
  private static final long FAILURE_LOG_INTERVAL_MS = 10_000L;
  private static final long GC_WINDOW_MS = 60_000L;

  private final Path dataPath;
  private final LongSupplier clock;
  private final HardwareAbstractionLayer hardware;
  private final OperatingSystem operatingSystem;
  private final PlatformEnum platform;
  private final MemoryMXBean memoryBean;
  private final ClassLoadingMXBean classLoadingBean;
  private final RuntimeMXBean runtimeBean;
  private final ThreadMXBean threadBean;
  private final VirtualThreadSchedulerMXBean virtualThreadBean;
  private final List<GarbageCollectorMXBean> garbageCollectorBeans;
  private final List<BufferPoolMXBean> bufferPoolBeans;
  private final OperatingSystemMXBean operatingSystemBean;
  private final AllocationMeter allocationMeter;
  private final boolean sensorQueriesEnabled;
  private final SlidingCounterWindow gcCollections = new SlidingCounterWindow(GC_WINDOW_MS);
  private final EnumMap<HostMetric, HostReading> readings = new EnumMap<>(HostMetric.class);
  private final EnumMap<HostMetric.Source, Long> nextRefresh = new EnumMap<>(HostMetric.Source.class);
  private final EnumMap<HostMetric.Source, Long> lastFailure = new EnumMap<>(HostMetric.Source.class);
  private final EnumMap<HostMetric, CounterSample> counters = new EnumMap<>(HostMetric.class);
  private Map<String, DiskCounters> previousDisks = Map.of();
  private Map<String, NetworkCounters> previousNetwork = Map.of();
  private List<NetworkIF> networkInterfaces = List.of();
  private CentralProcessor processor;
  private CpuLoadMeter cpuLoad;
  private long nextNetworkListRefreshMs;
  private long previousStorageAtMs;
  private long previousNetworkAtMs;
  private EnvironmentDto.CpuDto cpu = new EnvironmentDto.CpuDto();
  private EnvironmentDto.MemoryDto memory = new EnvironmentDto.MemoryDto();
  private EnvironmentDto.JvmDto jvm = new EnvironmentDto.JvmDto();
  private EnvironmentDto.DiskDto[] disks = new EnvironmentDto.DiskDto[0];
  private EnvironmentDto.MountDto[] mounts = new EnvironmentDto.MountDto[0];
  private EnvironmentDto.NetworkInterfaceDto[] network = new EnvironmentDto.NetworkInterfaceDto[0];
  private String[] graphicsCards = new String[0];
  private String[] sensors = new String[0];
  private String[] powerSources = new String[0];
  private long systemUptime;
  private boolean graphicsCaptured;

  public HostTelemetryProvider(Path dataPath) {
    this(dataPath, createSystemInfo(), System::currentTimeMillis);
  }

  HostTelemetryProvider(Path dataPath, SystemInfo systemInfo, LongSupplier clock) {
    this.dataPath = dataPath;
    this.clock = clock;
    this.hardware = systemInfo.getHardware();
    this.operatingSystem = systemInfo.getOperatingSystem();
    this.platform = SystemInfo.getCurrentPlatform();
    this.memoryBean = ManagementFactory.getMemoryMXBean();
    this.classLoadingBean = ManagementFactory.getClassLoadingMXBean();
    this.runtimeBean = ManagementFactory.getRuntimeMXBean();
    this.threadBean = ManagementFactory.getThreadMXBean();
    this.virtualThreadBean = ManagementFactory.getPlatformMXBean(VirtualThreadSchedulerMXBean.class);
    this.garbageCollectorBeans = ManagementFactory.getGarbageCollectorMXBeans();
    this.bufferPoolBeans = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
    this.operatingSystemBean = ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean ? bean : null;
    this.allocationMeter = AllocationMeter.platform();
    this.sensorQueriesEnabled = WindowsSensorWmiQueryHandler.shouldQuerySensors();
    gcCollections.record(0L, 0L);
    cpu.graphicsCards = graphicsCards;
  }

  public HostTelemetrySnapshot capture() {
    long nowMs = clock.getAsLong();
    refresh(HostMetric.Source.JVM, nowMs, () -> captureJvm(nowMs));
    refresh(HostMetric.Source.ALLOCATION, nowMs, () -> publish(HostMetric.ALLOCATION, allocationMeter.sample(System.nanoTime()), nowMs));
    refresh(HostMetric.Source.CPU, nowMs, () -> captureCpu(nowMs));
    refresh(HostMetric.Source.MEMORY, nowMs, () -> captureMemory(nowMs));
    refresh(HostMetric.Source.PROCESS, nowMs, () -> captureProcess(nowMs));
    refresh(HostMetric.Source.DESCRIPTORS, nowMs, () -> captureDescriptors(nowMs));
    refresh(HostMetric.Source.THREADS, nowMs, () -> captureThreads(nowMs));
    refresh(HostMetric.Source.STORAGE, nowMs, () -> captureStorage(nowMs));
    refresh(HostMetric.Source.NETWORK, nowMs, () -> captureNetwork(nowMs));
    refresh(HostMetric.Source.GC, nowMs, () -> captureGc(nowMs));
    refresh(HostMetric.Source.DETAILS, nowMs, this::captureDetails);
    EnvironmentDto environment = new EnvironmentDto();
    environment.cpu = cpu;
    environment.memory = memory;
    environment.jvm = jvm;
    environment.disks = disks;
    environment.mounts = mounts;
    environment.network = network;
    environment.server = new EnvironmentDto.ServerDto();
    environment.server.uptimeSeconds = systemUptime;
    environment.server.sensors = sensors;
    environment.server.powerSources = powerSources;
    return new HostTelemetrySnapshot(environment, readings);
  }

  static double rate(long current, long previous, long elapsedMs) {
    if (elapsedMs <= 0L || current < previous) {
      return 0D;
    }
    return (current - previous) * 1000D / elapsedMs;
  }

  static double perMinute(long current, long previous, long elapsedMs) {
    return rate(current, previous, elapsedMs) * 60D;
  }

  private void refresh(HostMetric.Source source, long nowMs, Probe probe) {
    if (nowMs < nextRefresh.getOrDefault(source, 0L)) {
      return;
    }
    nextRefresh.put(source, nowMs + source.intervalMs());
    try {
      probe.capture();
    } catch (IOException | RuntimeException | LinkageError failure) {
      unavailable(source, "sampling-failed");
      if (nowMs - lastFailure.getOrDefault(source, 0L) >= FAILURE_LOG_INTERVAL_MS) {
        lastFailure.put(source, nowMs);
        React.reportError("Failed to refresh React " + source.name().toLowerCase(Locale.ROOT) + " telemetry", failure);
      }
    }
  }

  private void unavailable(HostMetric.Source source, String reason) {
    for (HostMetric metric : HostMetric.values()) {
      if (metric.definition().source() == source) {
        readings.put(metric, readings.getOrDefault(metric, HostReading.EMPTY).unavailable(reason));
      }
    }
  }

  private void publish(HostMetric metric, double value, long nowMs) {
    boolean available = Double.isFinite(value) && value >= 0D && nowMs > 0L;
    readings.put(metric, new HostReading(available ? value : 0D, nowMs, available,
        available ? "" : "source-unavailable"));
  }

  private void publishRate(HostMetric metric, long count, long nowMs) {
    CounterSample previous = counters.put(metric, new CounterSample(count, nowMs));
    if (count < 0L || previous == null || previous.value() < 0L || nowMs <= previous.atMs() || count < previous.value()) {
      readings.put(metric, new HostReading(0D, nowMs, false, count < 0L ? "source-unavailable" : "warming-up"));
      return;
    }
    publish(metric, rate(count, previous.value(), nowMs - previous.atMs()), nowMs);
  }

  private void captureJvm(long nowMs) {
    MemoryUsage heap = memoryBean.getHeapMemoryUsage();
    MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
    publish(HostMetric.HEAP_MAX, heap.getMax(), nowMs);
    publish(HostMetric.HEAP_COMMITTED, heap.getCommitted(), nowMs);
    publish(HostMetric.HEAP_UTILIZATION, heap.getMax() > 0L ? heap.getUsed() * 100D / heap.getMax() : Double.NaN, nowMs);
    publish(HostMetric.NONHEAP_USED, nonHeap.getUsed(), nowMs);
    long directBytes = -1L;
    long directCount = -1L;
    for (BufferPoolMXBean pool : bufferPoolBeans) {
      if ("direct".equals(pool.getName())) {
        directBytes = pool.getMemoryUsed();
        directCount = pool.getCount();
      }
    }
    publish(HostMetric.DIRECT_BYTES, directBytes, nowMs);
    publish(HostMetric.DIRECT_COUNT, directCount, nowMs);
    long collections = 0L;
    boolean collectionCountAvailable = false;
    for (GarbageCollectorMXBean bean : GCStatsTracker.pauseBeans(garbageCollectorBeans)) {
      long count = bean.getCollectionCount();
      if (count >= 0L) {
        collections += count;
        collectionCountAvailable = true;
      }
    }
    gcCollections.record(runtimeBean.getUptime(), collections);
    publish(HostMetric.GC_RATE, collectionCountAvailable ? gcCollections.perMinute() : Double.NaN, nowMs);
    publish(HostMetric.LOADED_CLASSES, classLoadingBean.getLoadedClassCount(), nowMs);
    publish(HostMetric.UPTIME, runtimeBean.getUptime(), nowMs);
    EnvironmentDto.JvmDto captured = new EnvironmentDto.JvmDto();
    captured.javaVendor = System.getProperty("java.vendor", "unknown");
    captured.javaVersion = System.getProperty("java.version", "unknown");
    captured.heapUsed = Math.max(0L, heap.getUsed());
    captured.heapMax = Math.max(0L, heap.getMax());
    jvm = captured;
  }

  private void captureCpu(long nowMs) {
    if (processor == null) {
      processor = hardware.getProcessor();
      cpuLoad = new CpuLoadMeter(processor.getLogicalProcessorCount());
    }
    int availableProcessors = Runtime.getRuntime().availableProcessors();
    cpuLoad.update(System.nanoTime(), processor.getSystemCpuLoadTicks(),
        operatingSystemBean == null ? -1L : operatingSystemBean.getProcessCpuTime());
    publish(HostMetric.CPU_SYSTEM, cpuLoad.systemLoad(), nowMs);
    publish(HostMetric.CPU_PROCESS, cpuLoad.processLoad(), nowMs);
    publish(HostMetric.CPU_AVAILABLE, availableProcessors, nowMs);
    publish(HostMetric.CPU_EFFECTIVE, cpuLoad.processLoad() * Math.max(1, processor.getLogicalProcessorCount())
        * 100D / Math.max(1, availableProcessors), nowMs);
    publish(HostMetric.CPU_IOWAIT, supportsIoWait() ? cpuLoad.ioWaitLoad() * 100D : Double.NaN, nowMs);
    publish(HostMetric.CPU_STEAL, platform == PlatformEnum.LINUX || platform == PlatformEnum.AIX
        ? cpuLoad.stealLoad() * 100D : Double.NaN, nowMs);
    EnvironmentDto.CpuDto captured = new EnvironmentDto.CpuDto();
    captured.model = processor.getProcessorIdentifier().getName();
    captured.architecture = System.getProperty("os.arch", "unknown");
    captured.cores = processor.getLogicalProcessorCount();
    captured.systemLoad = finiteOrZero(cpuLoad.systemLoad());
    captured.processLoad = finiteOrZero(cpuLoad.processLoad());
    captured.graphicsCards = graphicsCards;
    cpu = captured;
  }

  private boolean supportsIoWait() {
    return platform == PlatformEnum.LINUX || platform == PlatformEnum.AIX || platform == PlatformEnum.SOLARIS;
  }

  private void captureMemory(long nowMs) {
    GlobalMemory physical = hardware.getMemory();
    VirtualMemory virtual = physical.getVirtualMemory();
    long total = physical.getTotal();
    long available = physical.getAvailable();
    boolean physicalAvailable = total > 0L && available >= 0L && available <= total;
    publish(HostMetric.PHYSICAL_USED, physicalAvailable ? total - available : Double.NaN, nowMs);
    publish(HostMetric.PHYSICAL_FREE, physicalAvailable ? available : Double.NaN, nowMs);
    publish(HostMetric.SWAP_TOTAL, virtual.getSwapTotal(), nowMs);
    publish(HostMetric.SWAP_USED, virtual.getSwapUsed(), nowMs);
    publishRate(HostMetric.SWAP_IN, virtual.getSwapPagesIn(), nowMs);
    publishRate(HostMetric.SWAP_OUT, virtual.getSwapPagesOut(), nowMs);
    EnvironmentDto.MemoryDto captured = new EnvironmentDto.MemoryDto();
    captured.physicalTotal = Math.max(0L, total);
    captured.physicalFree = Math.max(0L, available);
    captured.physicalUsed = Math.max(0L, total - available);
    captured.virtualTotal = Math.max(0L, virtual.getVirtualMax());
    captured.virtualUsed = Math.max(0L, virtual.getVirtualInUse());
    captured.virtualFree = Math.max(0L, captured.virtualTotal - captured.virtualUsed);
    memory = captured;
  }

  private void captureProcess(long nowMs) {
    OSProcess process = operatingSystem.getProcess(operatingSystem.getProcessId());
    if (process == null || process.getState() == OSProcess.State.INVALID) {
      unavailable(HostMetric.Source.PROCESS, "source-unavailable");
      return;
    }
    publish(HostMetric.RSS, process.getResidentSetSize(), nowMs);
    publishRate(HostMetric.PROCESS_READ, process.getBytesRead(), nowMs);
    publishRate(HostMetric.PROCESS_WRITE, platform == PlatformEnum.SOLARIS ? -1L : process.getBytesWritten(), nowMs);
    boolean faultsAvailable = platform == PlatformEnum.LINUX || platform == PlatformEnum.MACOS
        || platform == PlatformEnum.FREEBSD || platform == PlatformEnum.OPENBSD || platform == PlatformEnum.SOLARIS;
    publishRate(HostMetric.MINOR_FAULTS, faultsAvailable ? process.getMinorFaults() : -1L, nowMs);
    publishRate(HostMetric.MAJOR_FAULTS, faultsAvailable ? process.getMajorFaults() : -1L, nowMs);
    long totalFaults = platform == PlatformEnum.WINDOWS ? process.getMinorFaults()
        : faultsAvailable ? addKnown(process.getMinorFaults(), process.getMajorFaults()) : -1L;
    publishRate(HostMetric.TOTAL_FAULTS, totalFaults, nowMs);
  }

  private void captureDescriptors(long nowMs) {
    OSProcess process = operatingSystem.getProcess(operatingSystem.getProcessId());
    if (process == null || process.getState() == OSProcess.State.INVALID) {
      unavailable(HostMetric.Source.DESCRIPTORS, "source-unavailable");
      return;
    }
    long open = process.getOpenFiles();
    long limit = process.getSoftOpenFileLimit();
    publish(HostMetric.OPEN_FILES, open, nowMs);
    publish(HostMetric.FILE_LIMIT, limit > 0L ? limit : Double.NaN, nowMs);
    publish(HostMetric.FILE_HEADROOM, open >= 0L && limit > 0L ? Math.max(0L, limit - open) : Double.NaN, nowMs);
    publish(HostMetric.FILE_UTILIZATION, open >= 0L && limit > 0L ? open * 100D / limit : Double.NaN, nowMs);
  }

  private void captureThreads(long nowMs) {
    publish(HostMetric.THREAD_LIVE, threadBean.getThreadCount(), nowMs);
    publish(HostMetric.THREAD_DAEMON, threadBean.getDaemonThreadCount(), nowMs);
    publish(HostMetric.THREAD_PEAK, threadBean.getPeakThreadCount(), nowMs);
    publishRate(HostMetric.THREAD_STARTED, threadBean.getTotalStartedThreadCount(), nowMs);
    ThreadInfo[] threads = threadBean.getThreadInfo(threadBean.getAllThreadIds(), 0);
    int runnable = 0;
    int blocked = 0;
    int waiting = 0;
    for (ThreadInfo thread : threads) {
      if (thread == null) {
        continue;
      }
      switch (thread.getThreadState()) {
        case RUNNABLE -> runnable++;
        case BLOCKED -> blocked++;
        case WAITING, TIMED_WAITING -> waiting++;
        default -> { }
      }
    }
    publish(HostMetric.THREAD_RUNNABLE, runnable, nowMs);
    publish(HostMetric.THREAD_BLOCKED, blocked, nowMs);
    publish(HostMetric.THREAD_WAITING, waiting, nowMs);
    publish(HostMetric.VIRTUAL_PARALLELISM, virtualThreadBean == null ? -1D : virtualThreadBean.getParallelism(), nowMs);
    publish(HostMetric.VIRTUAL_CARRIERS, virtualThreadBean == null ? -1D : virtualThreadBean.getPoolSize(), nowMs);
    publish(HostMetric.VIRTUAL_MOUNTED, virtualThreadBean == null ? -1D : virtualThreadBean.getMountedVirtualThreadCount(), nowMs);
    publish(HostMetric.VIRTUAL_QUEUED, virtualThreadBean == null ? -1D : virtualThreadBean.getQueuedVirtualThreadCount(), nowMs);
  }

  private void captureGc(long nowMs) {
    GCStatsTracker.Snapshot snapshot = GCStatsTracker.snapshot(nowMs);
    publish(HostMetric.GC_AFTER, snapshot.heapAfterBytes(), snapshot.memoryAtMs());
    publish(HostMetric.GC_RECLAIMED, snapshot.reclaimedBytes(), snapshot.memoryAtMs());
    publish(HostMetric.GC_LATEST, snapshot.latestPauseMs(), snapshot.pauseAtMs());
    publish(HostMetric.GC_MAX, snapshot.maxPauseMs(), nowMs);
  }

  private void captureStorage(long nowMs) throws IOException {
    List<HWDiskStore> stores = hardware.getDiskStores();
    List<EnvironmentDto.DiskDto> capturedDisks = new ArrayList<>(stores.size());
    Map<String, DiskCounters> current = new HashMap<>();
    long reads = 0L;
    long writes = 0L;
    long readBytes = 0L;
    long writeBytes = 0L;
    long transferTime = 0L;
    long queue = 0L;
    int matched = 0;
    for (HWDiskStore store : stores) {
      EnvironmentDto.DiskDto disk = new EnvironmentDto.DiskDto();
      disk.name = store.getName();
      disk.model = store.getModel();
      disk.sizeBytes = store.getSize();
      disk.readBytes = store.getReadBytes();
      disk.writeBytes = store.getWriteBytes();
      disk.reads = store.getReads();
      disk.writes = store.getWrites();
      disk.queueLength = store.getCurrentQueueLength();
      disk.transferTimeMillis = store.getTransferTime();
      disk.timestampMillis = store.getTimeStamp();
      capturedDisks.add(disk);
      DiskCounters value = new DiskCounters(disk.readBytes, disk.writeBytes, disk.reads, disk.writes, disk.transferTimeMillis);
      current.put(disk.name, value);
      DiskCounters previous = previousDisks.get(disk.name);
      if (previous != null && value.atLeast(previous)) {
        readBytes += value.readBytes() - previous.readBytes();
        writeBytes += value.writeBytes() - previous.writeBytes();
        reads += value.reads() - previous.reads();
        writes += value.writes() - previous.writes();
        transferTime += value.transferMs() - previous.transferMs();
        matched++;
      }
      queue = addKnown(queue, disk.queueLength);
    }
    List<OSFileStore> fileStores = operatingSystem.getFileSystem().getFileStores();
    List<EnvironmentDto.MountDto> capturedMounts = new ArrayList<>(fileStores.size());
    for (OSFileStore store : fileStores) {
      EnvironmentDto.MountDto mount = new EnvironmentDto.MountDto();
      mount.name = store.getName();
      mount.mount = store.getMount();
      mount.description = store.getDescription();
      mount.type = store.getType();
      mount.totalBytes = store.getTotalSpace();
      mount.freeBytes = store.getFreeSpace();
      mount.usableBytes = store.getUsableSpace();
      capturedMounts.add(mount);
    }
    long usable = Files.getFileStore(dataPath).getUsableSpace();
    long elapsed = nowMs - previousStorageAtMs;
    boolean ratesAvailable = previousStorageAtMs > 0L && elapsed > 0L && matched == current.size() && matched > 0;
    publish(HostMetric.DISK_USABLE, usable, nowMs);
    publish(HostMetric.DISK_READ, ratesAvailable ? rate(readBytes, 0L, elapsed) : Double.NaN, nowMs);
    publish(HostMetric.DISK_WRITE, ratesAvailable ? rate(writeBytes, 0L, elapsed) : Double.NaN, nowMs);
    publish(HostMetric.DISK_READ_OPS, ratesAvailable ? rate(reads, 0L, elapsed) : Double.NaN, nowMs);
    publish(HostMetric.DISK_WRITE_OPS, ratesAvailable ? rate(writes, 0L, elapsed) : Double.NaN, nowMs);
    publish(HostMetric.DISK_BUSY, ratesAvailable ? rate(transferTime, 0L, elapsed) : Double.NaN, nowMs);
    publish(HostMetric.DISK_QUEUE, !stores.isEmpty() && platform != PlatformEnum.MACOS
        ? queue : Double.NaN, nowMs);
    disks = capturedDisks.toArray(new EnvironmentDto.DiskDto[0]);
    mounts = capturedMounts.toArray(new EnvironmentDto.MountDto[0]);
    previousDisks = current;
    previousStorageAtMs = nowMs;
  }

  private void captureNetwork(long nowMs) throws IOException {
    if (nowMs >= nextNetworkListRefreshMs) {
      networkInterfaces = hardware.getNetworkIFs();
      nextNetworkListRefreshMs = nowMs + NETWORK_LIST_REFRESH_MS;
    } else {
      for (NetworkIF networkInterface : networkInterfaces) {
        if (!networkInterface.updateAttributes()) {
          throw new IOException("Could not refresh network interface " + networkInterface.getName());
        }
      }
    }
    List<EnvironmentDto.NetworkInterfaceDto> captured = new ArrayList<>(networkInterfaces.size());
    Map<String, NetworkCounters> current = new HashMap<>();
    long receiveBytes = 0L;
    long sendBytes = 0L;
    long receivePackets = 0L;
    long sendPackets = 0L;
    long drops = 0L;
    long inErrors = 0L;
    long outErrors = 0L;
    long collisions = 0L;
    int matched = 0;
    for (NetworkIF networkInterface : networkInterfaces) {
      EnvironmentDto.NetworkInterfaceDto item = new EnvironmentDto.NetworkInterfaceDto();
      item.name = networkInterface.getName();
      item.displayName = networkInterface.getDisplayName();
      long mtu = networkInterface.getMTU();
      item.mtu = mtu < 0L || mtu > Integer.MAX_VALUE ? 0 : (int) mtu;
      item.macAddress = networkInterface.getMacaddr();
      item.ipv4Addresses = networkInterface.getIPv4addr();
      item.ipv6Addresses = networkInterface.getIPv6addr();
      item.speedBitsPerSecond = networkInterface.getSpeed();
      item.receivedBytes = networkInterface.getBytesRecv();
      item.sentBytes = networkInterface.getBytesSent();
      item.receivedPackets = networkInterface.getPacketsRecv();
      item.sentPackets = networkInterface.getPacketsSent();
      item.receiveErrors = networkInterface.getInErrors();
      item.sendErrors = networkInterface.getOutErrors();
      item.receiveDrops = networkInterface.getInDrops();
      item.collisions = networkInterface.getCollisions();
      item.timestampMillis = networkInterface.getTimeStamp();
      captured.add(item);
      NetworkCounters value = new NetworkCounters(item.receivedBytes, item.sentBytes, item.receivedPackets, item.sentPackets);
      current.put(item.name, value);
      NetworkCounters previous = previousNetwork.get(item.name);
      if (previous != null && value.atLeast(previous)) {
        receiveBytes += value.receiveBytes() - previous.receiveBytes();
        sendBytes += value.sendBytes() - previous.sendBytes();
        receivePackets += value.receivePackets() - previous.receivePackets();
        sendPackets += value.sendPackets() - previous.sendPackets();
        matched++;
      }
      drops = addKnown(drops, item.receiveDrops);
      inErrors = addKnown(inErrors, item.receiveErrors);
      outErrors = addKnown(outErrors, item.sendErrors);
      collisions = addKnown(collisions, item.collisions);
    }
    long elapsed = nowMs - previousNetworkAtMs;
    boolean ratesAvailable = previousNetworkAtMs > 0L && elapsed > 0L && matched == current.size() && matched > 0;
    publish(HostMetric.NETWORK_RECEIVE, ratesAvailable ? rate(receiveBytes, 0L, elapsed) : Double.NaN, nowMs);
    publish(HostMetric.NETWORK_SEND, ratesAvailable ? rate(sendBytes, 0L, elapsed) : Double.NaN, nowMs);
    publish(HostMetric.NETWORK_RECEIVE_PACKETS, ratesAvailable ? rate(receivePackets, 0L, elapsed) : Double.NaN, nowMs);
    publish(HostMetric.NETWORK_SEND_PACKETS, ratesAvailable ? rate(sendPackets, 0L, elapsed) : Double.NaN, nowMs);
    publish(HostMetric.NETWORK_DROPS, current.isEmpty() ? Double.NaN : drops, nowMs);
    publish(HostMetric.NETWORK_IN_ERRORS, current.isEmpty() ? Double.NaN : inErrors, nowMs);
    publish(HostMetric.NETWORK_OUT_ERRORS, current.isEmpty() ? Double.NaN : outErrors, nowMs);
    publish(HostMetric.NETWORK_COLLISIONS, current.isEmpty() || platform == PlatformEnum.WINDOWS ? Double.NaN : collisions, nowMs);
    network = captured.toArray(new EnvironmentDto.NetworkInterfaceDto[0]);
    previousNetwork = current;
    previousNetworkAtMs = nowMs;
  }

  private void captureDetails() {
    String[] capturedSensors = buildSensors();
    String[] capturedPower = buildPowerSources();
    if (!graphicsCaptured) {
      graphicsCards = buildGraphicsCards();
      graphicsCaptured = true;
    }
    sensors = capturedSensors;
    powerSources = capturedPower;
    systemUptime = operatingSystem.getSystemUptime();
  }

  private String[] buildGraphicsCards() {
    List<GraphicsCard> cards = hardware.getGraphicsCards();
    List<String> descriptions = new ArrayList<>(cards.size());
    for (GraphicsCard card : cards) {
      descriptions.add(card.getName() + " (" + card.getVendor() + ", " + Form.memSize(card.getVRam()) + ")");
    }
    return descriptions.toArray(new String[0]);
  }

  private String[] buildSensors() {
    if (!sensorQueriesEnabled) {
      return new String[0];
    }
    Sensors hardwareSensors = hardware.getSensors();
    List<String> values = new ArrayList<>(3);
    double temperature = hardwareSensors.getCpuTemperature();
    double voltage = hardwareSensors.getCpuVoltage();
    int[] fanSpeeds = hardwareSensors.getFanSpeeds();
    if (temperature > 0D) {
      values.add("CPU temperature: " + Form.f(temperature, 1) + " C");
    }
    if (voltage > 0D) {
      values.add("CPU voltage: " + Form.f(voltage, 2) + " V");
    }
    if (fanSpeeds.length > 0) {
      values.add("Fan speed: " + fanSpeeds[0] + " RPM");
    }
    return values.toArray(new String[0]);
  }

  private String[] buildPowerSources() {
    List<PowerSource> sources = hardware.getPowerSources();
    List<String> values = new ArrayList<>(sources.size());
    for (PowerSource source : sources) {
      values.add(source.getName() + ": " + Form.pc(source.getRemainingCapacityPercent()));
    }
    return values.toArray(new String[0]);
  }

  private static SystemInfo createSystemInfo() {
    WindowsSensorWmiQueryHandler.installIfWindows();
    return new SystemInfo();
  }

  private static double finiteOrZero(double value) {
    return Double.isFinite(value) ? value : 0D;
  }

  private static long addKnown(long total, long value) {
    if (total < 0L || value < 0L) {
      return -1L;
    }
    return total > Long.MAX_VALUE - value ? Long.MAX_VALUE : total + value;
  }

  private record CounterSample(long value, long atMs) {
  }

  private record DiskCounters(long readBytes, long writeBytes, long reads, long writes, long transferMs) {
    private boolean atLeast(DiskCounters previous) {
      return previous.readBytes >= 0L && previous.writeBytes >= 0L && previous.reads >= 0L
          && previous.writes >= 0L && previous.transferMs >= 0L
          && readBytes >= previous.readBytes && writeBytes >= previous.writeBytes && reads >= previous.reads
          && writes >= previous.writes && transferMs >= previous.transferMs;
    }
  }

  private record NetworkCounters(long receiveBytes, long sendBytes, long receivePackets, long sendPackets) {
    private boolean atLeast(NetworkCounters previous) {
      return previous.receiveBytes >= 0L && previous.sendBytes >= 0L
          && previous.receivePackets >= 0L && previous.sendPackets >= 0L
          && receiveBytes >= previous.receiveBytes && sendBytes >= previous.sendBytes
          && receivePackets >= previous.receivePackets && sendPackets >= previous.sendPackets;
    }
  }

  @FunctionalInterface
  private interface Probe {
    void capture() throws IOException;
  }
}
