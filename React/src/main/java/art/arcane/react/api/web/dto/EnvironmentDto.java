package art.arcane.react.api.web.dto;

public class EnvironmentDto {
    public CpuDto cpu;
    public MemoryDto memory;
    public JvmDto jvm;
    public ServerDto server;
    public DiskDto[] disks;
    public MountDto[] mounts;
    public NetworkInterfaceDto[] network;

    public static class CpuDto {
        public String model;
        public String architecture;
        public int cores;
        public double systemLoad;
        public double processLoad;
        public String[] graphicsCards;
    }

    public static class MemoryDto {
        public long physicalTotal;
        public long physicalFree;
        public long physicalUsed;
        public long virtualTotal;
        public long virtualFree;
        public long virtualUsed;
    }

    public static class JvmDto {
        public String javaVendor;
        public String javaVersion;
        public long heapUsed;
        public long heapMax;
    }

    public static class ServerDto {
        public String brand;
        public String version;
        public boolean folia;
        public long uptimeSeconds;
        public String[] sensors;
        public String[] powerSources;
    }

    public static class DiskDto {
        public String name;
        public String model;
        public long sizeBytes;
        public long readBytes;
        public long writeBytes;
        public long reads;
        public long writes;
        public long queueLength;
        public long transferTimeMillis;
        public long timestampMillis;
    }

    public static class MountDto {
        public String name;
        public String mount;
        public String description;
        public String type;
        public long totalBytes;
        public long freeBytes;
        public long usableBytes;
    }

    public static class NetworkInterfaceDto {
        public String name;
        public String displayName;
        public int mtu;
        public String macAddress;
        public String[] ipv4Addresses;
        public String[] ipv6Addresses;
        public long speedBitsPerSecond;
        public long receivedBytes;
        public long sentBytes;
        public long receivedPackets;
        public long sentPackets;
        public long receiveErrors;
        public long sendErrors;
        public long receiveDrops;
        public long collisions;
        public long timestampMillis;
    }
}
