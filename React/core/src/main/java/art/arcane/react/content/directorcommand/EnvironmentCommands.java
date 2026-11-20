package art.arcane.react.content.directorcommand;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.EnvironmentMessages;
import art.arcane.react.util.project.hardware.getHardware;
import art.arcane.react.util.reflect.Platform;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import oshi.SystemInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * The host report of {@code /react environment info} and the summary uploaded after it.
 */
public final class EnvironmentCommands {
  private EnvironmentCommands() {
  }

  public static void info(Object sender, String reactVersion, String serverVersion) {
    SystemInfo systemInfo = new SystemInfo();
    List<String> disks = new ArrayList<>(getHardware.getDisk());
    List<String> interfaces = new ArrayList<>(getHardware.getInterfaces());
    List<String> displays = new ArrayList<>(getHardware.getEDID());
    List<String> sensors = new ArrayList<>(getHardware.getSensors());
    List<String> gpus = new ArrayList<>(getHardware.getGraphicsCards());
    List<String> powersources = new ArrayList<>(getHardware.getPowerSources());

    ReactLanguage.send(sender, EnvironmentMessages.REACT_HEADER);
    ReactLanguage.send(sender, EnvironmentMessages.REACT_VERSION, MessageArgument.untrusted("version", reactVersion));
    ReactLanguage.send(sender, EnvironmentMessages.SERVER_TYPE, MessageArgument.untrusted("server", serverVersion));
    ReactLanguage.send(sender, EnvironmentMessages.SERVER_UPTIME, MessageArgument.untrusted("uptime", Form.stampTime(systemInfo.getOperatingSystem().getSystemUptime())));
    ReactLanguage.send(sender, EnvironmentMessages.PLATFORM_HEADER);
    ReactLanguage.send(
        sender,
        EnvironmentMessages.PLATFORM,
        MessageArgument.untrusted("version", Platform.getVersion()),
        MessageArgument.untrusted("platform", Platform.getName())
    );
    ReactLanguage.send(
        sender,
        EnvironmentMessages.JAVA,
        MessageArgument.untrusted("vendor", Platform.ENVIRONMENT.getJavaVendor()),
        MessageArgument.untrusted("version", Platform.ENVIRONMENT.getJavaVersion())
    );
    ReactLanguage.send(sender, EnvironmentMessages.PROCESSOR_HEADER);
    ReactLanguage.send(sender, EnvironmentMessages.CPU_MODEL, MessageArgument.untrusted("model", getHardware.getCPUModel()));
    ReactLanguage.send(
        sender,
        EnvironmentMessages.CPU_ARCHITECTURE,
        MessageArgument.untrusted("architecture", Platform.CPU.getArchitecture()),
        MessageArgument.untrusted("processors", Platform.CPU.getAvailableProcessors())
    );
    ReactLanguage.send(
        sender,
        EnvironmentMessages.CPU_LOAD,
        MessageArgument.untrusted("system_load", Form.pc(Platform.CPU.getCPULoad())),
        MessageArgument.untrusted("process_load", Form.pc(Platform.CPU.getLiveProcessCPULoad()))
    );
    ReactLanguage.send(sender, EnvironmentMessages.GRAPHICS_HEADER);
    for (String gpu : gpus) {
      ReactLanguage.send(sender, EnvironmentMessages.GRAPHICS_ENTRY, MessageArgument.untrusted("value", gpu));
    }
    ReactLanguage.send(sender, EnvironmentMessages.MEMORY_HEADER);
    ReactLanguage.send(
        sender,
        EnvironmentMessages.MEMORY_PHYSICAL,
        MessageArgument.untrusted("total", Form.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory())),
        MessageArgument.untrusted("free", Form.memSize(Platform.MEMORY.PHYSICAL.getFreeMemory())),
        MessageArgument.untrusted("used", Form.memSize(Platform.MEMORY.PHYSICAL.getUsedMemory()))
    );
    ReactLanguage.send(
        sender,
        EnvironmentMessages.MEMORY_VIRTUAL,
        MessageArgument.untrusted("total", Form.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory())),
        MessageArgument.untrusted("free", Form.memSize(Platform.MEMORY.VIRTUAL.getFreeMemory())),
        MessageArgument.untrusted("used", Form.memSize(Platform.MEMORY.VIRTUAL.getUsedMemory()))
    );
    ReactLanguage.send(sender, EnvironmentMessages.STORAGE_HEADER);
    for (String disk : disks) {
      ReactLanguage.send(sender, EnvironmentMessages.STORAGE_ENTRY, MessageArgument.untrusted("value", disk));
    }
    ReactLanguage.send(sender, EnvironmentMessages.INTERFACE_HEADER);
    for (String inter : interfaces) {
      ReactLanguage.send(sender, EnvironmentMessages.INTERFACE_ENTRY, MessageArgument.untrusted("value", inter));
    }
    ReactLanguage.send(sender, EnvironmentMessages.DISPLAY_HEADER);
    for (String display : displays) {
      ReactLanguage.send(sender, EnvironmentMessages.DISPLAY_ENTRY, MessageArgument.untrusted("value", display));
    }
    ReactLanguage.send(sender, EnvironmentMessages.SENSOR_HEADER);
    for (String sensor : sensors) {
      ReactLanguage.send(sender, EnvironmentMessages.SENSOR_ENTRY, MessageArgument.untrusted("value", sensor));
    }
    ReactLanguage.send(sender, EnvironmentMessages.POWER_HEADER);
    for (String power : powersources) {
      ReactLanguage.send(sender, EnvironmentMessages.POWER_ENTRY, MessageArgument.untrusted("value", power));
    }
  }

  public static String summary(String version, String serverVersion) {
    StringBuilder sb = new StringBuilder();
    sb.append(" -- == React Info == -- \n");
    sb.append("React Version: ").append(version).append("\n");
    sb.append("Server Type: ").append(serverVersion).append("\n");
    sb.append(" -- == Platform Overview == -- \n");
    sb.append("Version: ").append(Platform.getVersion()).append(" - Platform: ").append(Platform.getName()).append("\n");
    sb.append("Java Vendor: ").append(Platform.ENVIRONMENT.getJavaVendor()).append(" - Java Version: ").append(Platform.ENVIRONMENT.getJavaVersion()).append("\n");
    sb.append(" -- == Storage Information == -- \n");
    sb.append("Total Space: ").append(Form.memSize(Platform.STORAGE.getTotalSpace())).append("\n");
    sb.append("Free Space: ").append(Form.memSize(Platform.STORAGE.getFreeSpace())).append("\n");
    sb.append("Used Space: ").append(Form.memSize(Platform.STORAGE.getUsedSpace())).append("\n");
    sb.append(" -- == Memory Information == -- \n");
    sb.append("Physical Memory - Total: ").append(Form.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory())).append(" Free: ").append(Form.memSize(Platform.MEMORY.PHYSICAL.getFreeMemory())).append(" Used: ").append(Form.memSize(Platform.MEMORY.PHYSICAL.getUsedMemory())).append("\n");
    sb.append("Virtual Memory - Total: ").append(Form.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory())).append(" Free: ").append(Form.memSize(Platform.MEMORY.VIRTUAL.getFreeMemory())).append(" Used: ").append(Form.memSize(Platform.MEMORY.VIRTUAL.getUsedMemory())).append("\n");
    sb.append(" -- == CPU Overview == -- \n");
    sb.append("CPU Architecture: ").append(Platform.CPU.getArchitecture()).append(" Available Processors: ").append(Platform.CPU.getAvailableProcessors()).append("\n");
    sb.append("CPU Load: ").append(Form.pc(Platform.CPU.getCPULoad())).append(" CPU Live Process Load: ").append(Form.pc(Platform.CPU.getLiveProcessCPULoad())).append("\n");

    return sb.toString();
  }
}
