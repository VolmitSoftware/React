package art.arcane.react.core.telemetry;

import art.arcane.react.api.web.dto.EnvironmentDto;

public record HostTelemetrySnapshot(
    EnvironmentDto environment,
    boolean available,
    long capturedAtMs,
    long physicalMemoryUsed,
    long physicalMemoryFree,
    long diskUsable,
    double diskReadBytesPerSecond,
    double diskWriteBytesPerSecond,
    double networkReceiveBytesPerSecond,
    double networkSendBytesPerSecond,
    long networkReceiveDrops,
    long networkReceiveErrors,
    long networkSendErrors,
    long heapMax,
    long heapCommitted,
    double heapUtilization,
    long nonHeapUsed,
    long directBufferBytes,
    long directBufferCount,
    double gcCollectionsPerMinute,
    long loadedClasses,
    long processUptimeMs
) {
  public static HostTelemetrySnapshot empty() {
    EnvironmentDto environment = new EnvironmentDto();
    environment.cpu = new EnvironmentDto.CpuDto();
    environment.cpu.graphicsCards = new String[0];
    environment.memory = new EnvironmentDto.MemoryDto();
    environment.jvm = new EnvironmentDto.JvmDto();
    environment.server = new EnvironmentDto.ServerDto();
    environment.server.sensors = new String[0];
    environment.server.powerSources = new String[0];
    environment.disks = new EnvironmentDto.DiskDto[0];
    environment.mounts = new EnvironmentDto.MountDto[0];
    environment.network = new EnvironmentDto.NetworkInterfaceDto[0];
    return new HostTelemetrySnapshot(
        environment,
        false,
        0L,
        0L,
        0L,
        0L,
        0D,
        0D,
        0D,
        0D,
        0L,
        0L,
        0L,
        0L,
        0L,
        0D,
        0L,
        0L,
        0L,
        0D,
        0L,
        0L
    );
  }
}
