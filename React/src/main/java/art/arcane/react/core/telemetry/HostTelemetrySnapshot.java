package art.arcane.react.core.telemetry;

import art.arcane.react.api.web.dto.EnvironmentDto;

import java.util.Map;

public record HostTelemetrySnapshot(EnvironmentDto environment, Map<HostMetric, HostReading> readings) {
  public HostTelemetrySnapshot {
    readings = Map.copyOf(readings);
  }

  public HostReading reading(HostMetric metric) {
    return readings.getOrDefault(metric, HostReading.EMPTY);
  }

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
    return new HostTelemetrySnapshot(environment, Map.of());
  }
}
