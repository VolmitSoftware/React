package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.api.benchmark.EnvironmentReport;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.EnvironmentMessages;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.project.hardware.getHardware;
import art.arcane.react.util.reflect.Platform;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.web.MclogsClient;
import org.bukkit.Bukkit;
import oshi.SystemInfo;

public final class BukkitCommandEnvironment {
  private final EnvironmentReport report = new EnvironmentReport(new MclogsClient());

  public void info(VolmitSender sender) {
    SystemInfo systemInfo = new SystemInfo();
    KList<String> disks = new KList<>(getHardware.getDisk());
    KList<String> interfaces = new KList<>(getHardware.getInterfaces());
    KList<String> displays = new KList<>(getHardware.getEDID());
    KList<String> sensors = new KList<>(getHardware.getSensors());
    KList<String> gpus = new KList<>(getHardware.getGraphicsCards());
    KList<String> powersources = new KList<>(getHardware.getPowerSources());

    ReactLanguage.send(sender, EnvironmentMessages.REACT_HEADER);
    ReactLanguage.send(sender, EnvironmentMessages.REACT_VERSION, MessageArgument.untrusted("version", React.instance.getDescription().getVersion()));
    ReactLanguage.send(sender, EnvironmentMessages.SERVER_TYPE, MessageArgument.untrusted("server", Bukkit.getVersion()));
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
    report.upload(BukkitCommandSender.bukkit(sender));

  }
}
