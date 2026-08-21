package art.arcane.react.api.web.dto;

public class EnvironmentDto {
    public CpuDto cpu;
    public MemoryDto memory;
    public JvmDto jvm;
    public ServerDto server;

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
        public String[] disks;
        public String[] interfaces;
        public String[] sensors;
        public String[] powerSources;
    }
}
