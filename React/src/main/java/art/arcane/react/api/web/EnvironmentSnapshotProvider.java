package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.EnvironmentDto;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.util.project.hardware.getHardware;
import art.arcane.react.util.reflect.Platform;
import art.arcane.volmlib.util.collection.KList;
import oshi.SystemInfo;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class EnvironmentSnapshotProvider {
    private final Supplier<IdentityDto> identitySupplier;
    private final SystemInfo systemInfo;
    private final String cpuModel;
    private final String[] graphicsCards;
    private final String[] sensors;
    private final String[] powerSources;

    public EnvironmentSnapshotProvider(Supplier<IdentityDto> identitySupplier) {
        this.identitySupplier = identitySupplier;
        this.systemInfo = new SystemInfo();
        this.cpuModel = getHardware.getCPUModel();
        this.graphicsCards = klistToArray(getHardware.getGraphicsCards());
        this.sensors = klistToArray(getHardware.getSensors());
        this.powerSources = klistToArray(getHardware.getPowerSources());
    }

    public EnvironmentDto snapshot() {
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
        EnvironmentDto dto = new EnvironmentDto();
        dto.cpu = buildCpu();
        dto.memory = buildMemory();
        dto.jvm = buildJvm();
        dto.server = buildServer(operatingSystem);
        dto.disks = buildDisks(hardware);
        dto.mounts = buildMounts(operatingSystem);
        dto.network = buildNetwork(hardware);
        return dto;
    }

    private EnvironmentDto.CpuDto buildCpu() {
        EnvironmentDto.CpuDto cpu = new EnvironmentDto.CpuDto();
        cpu.model = cpuModel;
        cpu.architecture = Platform.CPU.getArchitecture();
        cpu.cores = Platform.CPU.getAvailableProcessors();
        cpu.systemLoad = Platform.CPU.getCPULoad();
        cpu.processLoad = Platform.CPU.getLiveProcessCPULoad();
        cpu.graphicsCards = graphicsCards;
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

    private EnvironmentDto.ServerDto buildServer(OperatingSystem operatingSystem) {
        EnvironmentDto.ServerDto server = new EnvironmentDto.ServerDto();
        IdentityDto identity = identitySupplier.get();
        server.brand = identity.serverName;
        server.version = identity.version;
        server.folia = identity.folia;
        server.uptimeSeconds = operatingSystem.getSystemUptime();
        server.sensors = sensors;
        server.powerSources = powerSources;
        return server;
    }

    private EnvironmentDto.DiskDto[] buildDisks(HardwareAbstractionLayer hardware) {
        List<HWDiskStore> stores = hardware.getDiskStores();
        List<EnvironmentDto.DiskDto> disks = new ArrayList<>(stores.size());
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
        }
        return disks.toArray(new EnvironmentDto.DiskDto[0]);
    }

    private EnvironmentDto.MountDto[] buildMounts(OperatingSystem operatingSystem) {
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

    private EnvironmentDto.NetworkInterfaceDto[] buildNetwork(HardwareAbstractionLayer hardware) {
        List<NetworkIF> interfaces = hardware.getNetworkIFs();
        List<EnvironmentDto.NetworkInterfaceDto> network = new ArrayList<>(interfaces.size());
        for (NetworkIF networkInterface : interfaces) {
            networkInterface.updateAttributes();
            EnvironmentDto.NetworkInterfaceDto item = new EnvironmentDto.NetworkInterfaceDto();
            item.name = networkInterface.getName();
            item.displayName = networkInterface.getDisplayName();
            item.mtu = networkInterface.getMTU();
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
        }
        return network.toArray(new EnvironmentDto.NetworkInterfaceDto[0]);
    }

    private static String[] klistToArray(KList<String> klist) {
        if (klist == null) {
            return new String[0];
        }
        return new ArrayList<>(klist).toArray(new String[0]);
    }
}
