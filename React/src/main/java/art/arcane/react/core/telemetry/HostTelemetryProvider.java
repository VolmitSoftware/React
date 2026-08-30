package art.arcane.react.core.telemetry;

import art.arcane.react.api.web.dto.EnvironmentDto;
import art.arcane.volmlib.util.format.Form;
import com.sun.management.OperatingSystemMXBean;
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
import oshi.software.os.OperatingSystem;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class HostTelemetryProvider {
  private static final long HARDWARE_DETAIL_REFRESH_MS = 10_000L;

  private final Path dataPath;
  private final HardwareAbstractionLayer hardware;
  private final OperatingSystem operatingSystem;
  private final CentralProcessor processor;
  private final MemoryMXBean memoryBean;
  private final ClassLoadingMXBean classLoadingBean;
  private final RuntimeMXBean runtimeBean;
  private final List<GarbageCollectorMXBean> garbageCollectorBeans;
  private final List<BufferPoolMXBean> bufferPoolBeans;
  private final OperatingSystemMXBean operatingSystemBean;
  private final boolean sensorQueriesEnabled;
  private final String cpuModel;
  private final String[] graphicsCards;
  private long[] processorTicks;
  private long previousCapturedAtMs;
  private long previousDiskReadBytes;
  private long previousDiskWriteBytes;
  private long previousNetworkReceiveBytes;
  private long previousNetworkSendBytes;
  private long previousGcCollections;
  private long nextHardwareDetailRefreshMs;
  private String[] sensors;
  private String[] powerSources;

  public HostTelemetryProvider(Path dataPath) {
    this.dataPath = dataPath;
    WindowsSensorWmiQueryHandler.installIfWindows();
    SystemInfo systemInfo = new SystemInfo();
    this.hardware = systemInfo.getHardware();
    this.operatingSystem = systemInfo.getOperatingSystem();
    this.processor = hardware.getProcessor();
    this.memoryBean = ManagementFactory.getMemoryMXBean();
    this.classLoadingBean = ManagementFactory.getClassLoadingMXBean();
    this.runtimeBean = ManagementFactory.getRuntimeMXBean();
    this.garbageCollectorBeans = ManagementFactory.getGarbageCollectorMXBeans();
    this.bufferPoolBeans = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
    java.lang.management.OperatingSystemMXBean platformBean = ManagementFactory.getOperatingSystemMXBean();
    this.operatingSystemBean = platformBean instanceof OperatingSystemMXBean bean ? bean : null;
    this.sensorQueriesEnabled = WindowsSensorWmiQueryHandler.shouldQuerySensors();
    this.cpuModel = processor.getProcessorIdentifier().getName();
    this.graphicsCards = buildGraphicsCards();
    this.processorTicks = processor.getSystemCpuLoadTicks();
    this.sensors = new String[0];
    this.powerSources = new String[0];
  }

  public HostTelemetrySnapshot capture() throws Exception {
    long nowMs = System.currentTimeMillis();
    if (nowMs >= nextHardwareDetailRefreshMs) {
      sensors = buildSensors();
      powerSources = buildPowerSources();
      nextHardwareDetailRefreshMs = nowMs + HARDWARE_DETAIL_REFRESH_MS;
    }

    GlobalMemory physicalMemory = hardware.getMemory();
    long physicalTotal = physicalMemory.getTotal();
    long physicalFree = physicalMemory.getAvailable();
    long physicalUsed = Math.max(0L, physicalTotal - physicalFree);
    VirtualMemory virtualMemory = physicalMemory.getVirtualMemory();

    DiskCapture diskCapture = captureDisks();
    NetworkCapture networkCapture = captureNetwork();
    long elapsedMs = previousCapturedAtMs == 0L ? 0L : Math.max(0L, nowMs - previousCapturedAtMs);
    double diskReadRate = rate(diskCapture.readBytes(), previousDiskReadBytes, elapsedMs);
    double diskWriteRate = rate(diskCapture.writeBytes(), previousDiskWriteBytes, elapsedMs);
    double networkReceiveRate = rate(networkCapture.receiveBytes(), previousNetworkReceiveBytes, elapsedMs);
    double networkSendRate = rate(networkCapture.sendBytes(), previousNetworkSendBytes, elapsedMs);

    long gcCollections = totalGcCollections();
    double gcCollectionsPerMinute = perMinute(gcCollections, previousGcCollections, elapsedMs);
    MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
    MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
    long heapMax = Math.max(0L, heapUsage.getMax());
    long heapCommitted = Math.max(0L, heapUsage.getCommitted());
    long heapUsed = Math.max(0L, heapUsage.getUsed());
    double heapUtilization = heapMax == 0L ? 0D : Math.min(100D, heapUsed * 100D / heapMax);
    BufferCapture bufferCapture = captureDirectBuffers();

    EnvironmentDto environment = new EnvironmentDto();
    environment.cpu = buildCpu();
    environment.memory = buildMemory(physicalTotal, physicalFree, physicalUsed, virtualMemory);
    environment.jvm = buildJvm(heapUsed, heapMax);
    environment.server = buildServer();
    environment.disks = diskCapture.disks();
    environment.mounts = captureMounts();
    environment.network = networkCapture.interfaces();

    previousCapturedAtMs = nowMs;
    previousDiskReadBytes = diskCapture.readBytes();
    previousDiskWriteBytes = diskCapture.writeBytes();
    previousNetworkReceiveBytes = networkCapture.receiveBytes();
    previousNetworkSendBytes = networkCapture.sendBytes();
    previousGcCollections = gcCollections;

    return new HostTelemetrySnapshot(
        environment,
        true,
        nowMs,
        physicalUsed,
        physicalFree,
        diskCapture.usableBytes(),
        diskReadRate,
        diskWriteRate,
        networkReceiveRate,
        networkSendRate,
        networkCapture.receiveDrops(),
        networkCapture.receiveErrors(),
        networkCapture.sendErrors(),
        heapMax,
        heapCommitted,
        heapUtilization,
        Math.max(0L, nonHeapUsage.getUsed()),
        bufferCapture.bytes(),
        bufferCapture.count(),
        gcCollectionsPerMinute,
        classLoadingBean.getLoadedClassCount(),
        runtimeBean.getUptime()
    );
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

  private EnvironmentDto.CpuDto buildCpu() {
    EnvironmentDto.CpuDto cpu = new EnvironmentDto.CpuDto();
    cpu.model = cpuModel;
    cpu.architecture = System.getProperty("os.arch", "unknown");
    cpu.cores = processor.getLogicalProcessorCount();
    cpu.systemLoad = processor.getSystemCpuLoadBetweenTicks(processorTicks);
    processorTicks = processor.getSystemCpuLoadTicks();
    cpu.processLoad = operatingSystemBean == null ? 0D : Math.max(0D, operatingSystemBean.getProcessCpuLoad());
    cpu.graphicsCards = graphicsCards;
    return cpu;
  }

  private EnvironmentDto.MemoryDto buildMemory(
      long physicalTotal,
      long physicalFree,
      long physicalUsed,
      VirtualMemory virtualMemory
  ) {
    EnvironmentDto.MemoryDto memory = new EnvironmentDto.MemoryDto();
    memory.physicalTotal = physicalTotal;
    memory.physicalFree = physicalFree;
    memory.physicalUsed = physicalUsed;
    memory.virtualTotal = Math.max(0L, virtualMemory.getVirtualMax());
    memory.virtualUsed = Math.max(0L, virtualMemory.getVirtualInUse());
    memory.virtualFree = Math.max(0L, memory.virtualTotal - memory.virtualUsed);
    return memory;
  }

  private EnvironmentDto.JvmDto buildJvm(long heapUsed, long heapMax) {
    EnvironmentDto.JvmDto jvm = new EnvironmentDto.JvmDto();
    jvm.javaVendor = System.getProperty("java.vendor", "unknown");
    jvm.javaVersion = System.getProperty("java.version", "unknown");
    jvm.heapUsed = heapUsed;
    jvm.heapMax = heapMax;
    return jvm;
  }

  private EnvironmentDto.ServerDto buildServer() {
    EnvironmentDto.ServerDto server = new EnvironmentDto.ServerDto();
    server.uptimeSeconds = operatingSystem.getSystemUptime();
    server.sensors = sensors;
    server.powerSources = powerSources;
    return server;
  }

  private DiskCapture captureDisks() throws Exception {
    List<HWDiskStore> stores = hardware.getDiskStores();
    List<EnvironmentDto.DiskDto> disks = new ArrayList<>(stores.size());
    long readBytes = 0L;
    long writeBytes = 0L;
    for (HWDiskStore store : stores) {
      store.updateAttributes();
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
      disks.add(disk);
      readBytes = saturatingAdd(readBytes, Math.max(0L, disk.readBytes));
      writeBytes = saturatingAdd(writeBytes, Math.max(0L, disk.writeBytes));
    }
    FileStore dataStore = Files.getFileStore(dataPath);
    return new DiskCapture(
        disks.toArray(new EnvironmentDto.DiskDto[0]),
        readBytes,
        writeBytes,
        Math.max(0L, dataStore.getUsableSpace())
    );
  }

  private EnvironmentDto.MountDto[] captureMounts() {
    List<OSFileStore> stores = operatingSystem.getFileSystem().getFileStores();
    List<EnvironmentDto.MountDto> mounts = new ArrayList<>(stores.size());
    for (OSFileStore store : stores) {
      store.updateAttributes();
      EnvironmentDto.MountDto mount = new EnvironmentDto.MountDto();
      mount.name = store.getName();
      mount.mount = store.getMount();
      mount.description = store.getDescription();
      mount.type = store.getType();
      mount.totalBytes = store.getTotalSpace();
      mount.freeBytes = store.getFreeSpace();
      mount.usableBytes = store.getUsableSpace();
      mounts.add(mount);
    }
    return mounts.toArray(new EnvironmentDto.MountDto[0]);
  }

  private NetworkCapture captureNetwork() {
    List<NetworkIF> interfaces = hardware.getNetworkIFs();
    List<EnvironmentDto.NetworkInterfaceDto> network = new ArrayList<>(interfaces.size());
    long receiveBytes = 0L;
    long sendBytes = 0L;
    long receiveDrops = 0L;
    long receiveErrors = 0L;
    long sendErrors = 0L;
    for (NetworkIF networkInterface : interfaces) {
      networkInterface.updateAttributes();
      EnvironmentDto.NetworkInterfaceDto item = new EnvironmentDto.NetworkInterfaceDto();
      item.name = networkInterface.getName();
      item.displayName = networkInterface.getDisplayName();
      item.mtu = Math.toIntExact(networkInterface.getMTU());
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
      network.add(item);
      receiveBytes = saturatingAdd(receiveBytes, Math.max(0L, item.receivedBytes));
      sendBytes = saturatingAdd(sendBytes, Math.max(0L, item.sentBytes));
      receiveDrops = saturatingAdd(receiveDrops, Math.max(0L, item.receiveDrops));
      receiveErrors = saturatingAdd(receiveErrors, Math.max(0L, item.receiveErrors));
      sendErrors = saturatingAdd(sendErrors, Math.max(0L, item.sendErrors));
    }
    return new NetworkCapture(
        network.toArray(new EnvironmentDto.NetworkInterfaceDto[0]),
        receiveBytes,
        sendBytes,
        receiveDrops,
        receiveErrors,
        sendErrors
    );
  }

  private BufferCapture captureDirectBuffers() {
    long bytes = 0L;
    long count = 0L;
    for (BufferPoolMXBean pool : bufferPoolBeans) {
      if (!"direct".equalsIgnoreCase(pool.getName())) {
        continue;
      }
      bytes = saturatingAdd(bytes, Math.max(0L, pool.getMemoryUsed()));
      count = saturatingAdd(count, Math.max(0L, pool.getCount()));
    }
    return new BufferCapture(bytes, count);
  }

  private long totalGcCollections() {
    long collections = 0L;
    for (GarbageCollectorMXBean bean : garbageCollectorBeans) {
      long count = bean.getCollectionCount();
      if (count >= 0L) {
        collections = saturatingAdd(collections, count);
      }
    }
    return collections;
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

  private static long saturatingAdd(long left, long right) {
    if (right > 0L && left > Long.MAX_VALUE - right) {
      return Long.MAX_VALUE;
    }
    return left + right;
  }

  private record DiskCapture(
      EnvironmentDto.DiskDto[] disks,
      long readBytes,
      long writeBytes,
      long usableBytes
  ) {
  }

  private record NetworkCapture(
      EnvironmentDto.NetworkInterfaceDto[] interfaces,
      long receiveBytes,
      long sendBytes,
      long receiveDrops,
      long receiveErrors,
      long sendErrors
  ) {
  }

  private record BufferCapture(long bytes, long count) {
  }
}
