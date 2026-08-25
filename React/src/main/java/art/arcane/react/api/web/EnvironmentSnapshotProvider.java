package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.EnvironmentDto;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.core.telemetry.HostTelemetrySnapshot;

import java.util.function.Supplier;

public final class EnvironmentSnapshotProvider {
    private final Supplier<IdentityDto> identitySupplier;
    private final Supplier<HostTelemetrySnapshot> telemetrySupplier;

    public EnvironmentSnapshotProvider(
        Supplier<IdentityDto> identitySupplier,
        Supplier<HostTelemetrySnapshot> telemetrySupplier
    ) {
        this.identitySupplier = identitySupplier;
        this.telemetrySupplier = telemetrySupplier;
    }

    public EnvironmentDto snapshot() {
        HostTelemetrySnapshot telemetry = telemetrySupplier.get();
        if (telemetry == null) {
            telemetry = HostTelemetrySnapshot.empty();
        }
        EnvironmentDto source = telemetry.environment();
        EnvironmentDto dto = new EnvironmentDto();
        dto.cpu = source.cpu;
        dto.memory = source.memory;
        dto.jvm = source.jvm;
        dto.server = buildServer(source.server);
        dto.disks = source.disks;
        dto.mounts = source.mounts;
        dto.network = source.network;
        return dto;
    }

    private EnvironmentDto.ServerDto buildServer(EnvironmentDto.ServerDto source) {
        EnvironmentDto.ServerDto server = new EnvironmentDto.ServerDto();
        IdentityDto identity = identitySupplier.get();
        if (identity != null) {
            server.brand = identity.serverName;
            server.version = identity.version;
            server.folia = identity.folia;
        }
        if (source != null) {
            server.uptimeSeconds = source.uptimeSeconds;
            server.sensors = source.sensors;
            server.powerSources = source.powerSources;
        } else {
            server.sensors = new String[0];
            server.powerSources = new String[0];
        }
        return server;
    }
}
