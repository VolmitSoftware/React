package art.arcane.react.util.project.hardware;

import art.arcane.react.React;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.Display;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HWPartition;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PowerSource;
import oshi.hardware.Sensors;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.EdidUtil;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class getHardware {
  public static String getServerOS() {
    SystemInfo systemInfo = new SystemInfo();
    OperatingSystem os = systemInfo.getOperatingSystem();
    return os.toString();
  }

  public static long getProcessMemory() {
    long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
    return maxMemory;
  }

  public static long getProcessUsedMemory() {
    Runtime runtime = Runtime.getRuntime();

    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    long usedMemory = totalMemory - freeMemory;

    return usedMemory / (1024 * 1024);
  }

  public static long getAvailableProcessMemory() {
    long availableMemory = getHardware.getProcessMemory() - getHardware.getProcessUsedMemory();
    return availableMemory;
  }

  public static String getCPUModel() {
    try {
      SystemInfo systemInfo = new SystemInfo();
      CentralProcessor processor = systemInfo.getHardware().getProcessor();
      String cpuModel = processor.getProcessorIdentifier().getName();
      return cpuModel.isEmpty() ? "Unknown CPU Model" : cpuModel;
    } catch (Exception e) {
      React.verbose("Hardware CPU model query failed", e);
      return "Unknown CPU Model";
    }
  }

  public static KList<String> getSensors() {
    KList<String> sensors = new KList<>();
    try {
      SystemInfo systemInfo = new SystemInfo();
      Sensors hardwareSensors = systemInfo.getHardware().getSensors();
      double temperature = hardwareSensors.getCpuTemperature();
      double voltage = hardwareSensors.getCpuVoltage();
      int[] fanSpeeds = hardwareSensors.getFanSpeeds();
      sensors.add("CPU temperature: " + (temperature > 0.0 ? Form.f(temperature, 1) + " C" : "Unavailable"));
      sensors.add("CPU voltage: " + (voltage > 0.0 ? Form.f(voltage, 2) + " V" : "Unavailable"));
      sensors.add("Fan speeds: " + (fanSpeeds.length == 0 ? "Unavailable" : Arrays.toString(fanSpeeds)));
    } catch (Exception e) {
      React.verbose("Hardware sensor query failed", e);
    }
    if (sensors.isEmpty()) {
      sensors.add("No sensors reported.");
    }
    return sensors;
  }

  public static KList<String> getGraphicsCards() {
    KList<String> gpus = new KList<>();
    try {
      SystemInfo systemInfo = new SystemInfo();
      for (GraphicsCard gpu : systemInfo.getHardware().getGraphicsCards()) {
        gpus.add("Model: " + gpu.getName());
        gpus.add("- Vendor: " + gpu.getVendor());
        gpus.add("- Video memory: " + (gpu.getVRam() > 0L ? Form.memSize(gpu.getVRam()) : "Unavailable"));
      }
    } catch (Exception e) {
      React.verbose("Graphics-card query failed", e);
    }
    if (gpus.isEmpty()) {
      gpus.add("No graphics cards reported.");
    }
    return gpus;
  }

  public static KList<String> getDisk() {
    KList<String> systemDisks = new KList<>();
    try {
      SystemInfo systemInfo = new SystemInfo();
      List<HWDiskStore> diskStores = systemInfo.getHardware().getDiskStores();
      OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
      List<OSFileStore> fileStores = operatingSystem.getFileSystem().getFileStores();

      for (HWDiskStore disk : diskStores) {
        systemDisks.add("Disk: " + disk.getName());
        systemDisks.add("- Model: " + disk.getModel());
        systemDisks.add("- Size: " + Form.memSize(disk.getSize()));
        systemDisks.add("- Partitions: " + disk.getPartitions().size());
        systemDisks.add("- Reads since boot: " + Form.memSize(disk.getReadBytes()));
        systemDisks.add("- Writes since boot: " + Form.memSize(disk.getWriteBytes()));
        for (HWPartition partition : disk.getPartitions()) {
          systemDisks.add("- Partition: " + partition.getName() + " (" + Form.memSize(partition.getSize()) + ")");
        }
      }

      for (OSFileStore store : fileStores) {
        systemDisks.add("Mount: " + store.getMount());
        systemDisks.add("- Name: " + store.getName());
        systemDisks.add("- Description: " + store.getDescription());
        systemDisks.add("- Type: " + store.getType());
        systemDisks.add("- Total space: " + Form.memSize(store.getTotalSpace()));
        systemDisks.add("- Free space: " + Form.memSize(store.getFreeSpace()));
      }
    } catch (Exception e) {
      React.verbose("Disk query failed", e);
    }
    if (systemDisks.isEmpty()) {
      systemDisks.add("No disks reported.");
    }
    return systemDisks;
  }

  public static KList<String> getPowerSources() {
    KList<String> systemPowerSources = new KList<>();
    try {
      SystemInfo systemInfo = new SystemInfo();
      List<PowerSource> powerSources = systemInfo.getHardware().getPowerSources();
      for (PowerSource powersource : powerSources) {
        systemPowerSources.add("Name: " + powersource.getName());
        systemPowerSources.add("- Remaining capacity: " + Form.pc(powersource.getRemainingCapacityPercent()));
        systemPowerSources.add("- Power usage rate: " + Form.f(powersource.getPowerUsageRate(), 1) + " mW");
        systemPowerSources.add("- Online: " + powersource.isPowerOnLine());
        systemPowerSources.add("- Capacity units: " + powersource.getCapacityUnits());
        systemPowerSources.add("- Cycle count: " + powersource.getCycleCount());
      }
    } catch (Exception e) {
      React.verbose("Power-source query failed", e);
    }
    if (systemPowerSources.isEmpty()) {
      systemPowerSources.add("No power sources reported.");
    }
    return systemPowerSources;
  }

  public static KList<String> getEDID() {
    KList<String> systemEDID = new KList<>();
    try {
      SystemInfo systemInfo = new SystemInfo();
      HardwareAbstractionLayer hardware = systemInfo.getHardware();
      List<Display> displays = hardware.getDisplays();
      for (int index = 0; index < displays.size(); index++) {
        systemEDID.add("Display " + (index + 1) + ":");
        for (String line : EdidUtil.toString(displays.get(index).getEdid()).split("\\R")) {
          String trimmed = line.trim();
          if (!trimmed.isEmpty()) {
            systemEDID.add("- " + trimmed);
          }
        }
      }
    } catch (Exception e) {
      React.verbose("Display query failed", e);
    }
    if (systemEDID.isEmpty()) {
      systemEDID.add("No displays reported.");
    }
    return systemEDID;
  }

  public static KList<String> getInterfaces() {
    KList<String> interfaces = new KList<>();
    try {
      Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
      for (NetworkInterface ni : Collections.list(networkInterfaces)) {
        if (!ni.isUp()) {
          continue;
        }
        interfaces.add("Interface: " + ni.getDisplayName());
        interfaces.add("- Name: " + ni.getName());
        interfaces.add("- MTU: " + ni.getMTU());
        interfaces.add("- Loopback: " + ni.isLoopback());
        for (InetAddress ia : Collections.list(ni.getInetAddresses())) {
          interfaces.add("- Address: " + ia.getHostAddress());
        }
      }
    } catch (Exception e) {
      React.verbose("Network-interface query failed", e);
    }
    if (interfaces.isEmpty()) {
      interfaces.add("No network interfaces reported.");
    }
    return interfaces;
  }
}
