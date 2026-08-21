package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.EnvironmentDto;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.util.project.hardware.getHardware;
import art.arcane.react.util.reflect.Platform;
import art.arcane.volmlib.util.collection.KList;
import oshi.SystemInfo;

import java.util.ArrayList;
import java.util.function.Supplier;

public class EnvironmentSnapshotProvider {
    private final Supplier<IdentityDto> identitySupplier;

    public EnvironmentSnapshotProvider(Supplier<IdentityDto> identitySupplier) {
        this.identitySupplier = identitySupplier;
    }

    public EnvironmentDto snapshot() {
        EnvironmentDto dto = new EnvironmentDto();
        dto.cpu = buildCpu();
        dto.memory = buildMemory();
        dto.jvm = buildJvm();
        dto.server = buildServer();
        return dto;
    }

    private EnvironmentDto.CpuDto buildCpu() {
        EnvironmentDto.CpuDto cpu = new EnvironmentDto.CpuDto();
        cpu.model = getHardware.getCPUModel();
        cpu.architecture = Platform.CPU.getArchitecture();
        cpu.cores = Platform.CPU.getAvailableProcessors();
        cpu.systemLoad = Platform.CPU.getCPULoad();
        cpu.processLoad = Platform.CPU.getLiveProcessCPULoad();
        cpu.graphicsCards = klistToArray(getHardware.getGraphicsCards());
        return cpu;
    }

    private EnvironmentDto.MemoryDto buildMemory() {
        EnvironmentDto.MemoryDto memory = new EnvironmentDto.MemoryDto();
        memory.physicalTotal = Platform.MEMORY.PHYSICAL.getTotalMemory();
        memory.physicalFree = Platform.MEMORY.PHYSICAL.getFreeMemory();
        memory.physicalUsed = Platform.MEMORY.PHYSICAL.getUsedMemory();
        memory.virtualTotal = Platform.MEMORY.VIRTUAL.getTotalMemory();
        memory.virtualFree = Platform.MEMORY.VIRTUAL.getFreeMemory();
        memory.virtualUsed = Platform.MEMORY.VIRTUAL.getUsedMemory();
        return memory;
    }

    private EnvironmentDto.JvmDto buildJvm() {
        EnvironmentDto.JvmDto jvm = new EnvironmentDto.JvmDto();
        jvm.javaVendor = Platform.ENVIRONMENT.getJavaVendor();
        jvm.javaVersion = Platform.ENVIRONMENT.getJavaVersion();
        Runtime runtime = Runtime.getRuntime();
        jvm.heapUsed = runtime.totalMemory() - runtime.freeMemory();
        jvm.heapMax = runtime.maxMemory();
        return jvm;
    }

    private EnvironmentDto.ServerDto buildServer() {
        EnvironmentDto.ServerDto server = new EnvironmentDto.ServerDto();
        IdentityDto identity = identitySupplier.get();
        server.brand = identity.serverName;
        server.version = identity.version;
        server.folia = identity.folia;
        server.uptimeSeconds = new SystemInfo().getOperatingSystem().getSystemUptime();
        server.disks = klistToArray(getHardware.getDisk());
        server.interfaces = klistToArray(getHardware.getInterfaces());
        server.sensors = klistToArray(getHardware.getSensors());
        server.powerSources = klistToArray(getHardware.getPowerSources());
        return server;
    }

    private static String[] klistToArray(KList<String> klist) {
        if (klist == null) {
            return new String[0];
        }
        return new ArrayList<>(klist).toArray(new String[0]);
    }
}
