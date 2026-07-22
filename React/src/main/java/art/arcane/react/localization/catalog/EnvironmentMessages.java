package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class EnvironmentMessages {
  public static final TextKey REACT_HEADER = TextKey.of("environment.header.react", "<bold><dark_aqua> -- == React Info == -- </dark_aqua></bold>");
  public static final TextKey REACT_VERSION = TextKey.of("environment.react_version", "<aqua>React version: {version}</aqua>");
  public static final TextKey SERVER_TYPE = TextKey.of("environment.server_type", "<aqua>Server type: {server}</aqua>");
  public static final TextKey SERVER_UPTIME = TextKey.of("environment.server_uptime", "<aqua>Server uptime: {uptime}</aqua>");
  public static final TextKey PLATFORM_HEADER = TextKey.of("environment.header.platform", "<bold><dark_aqua> -- == Platform Overview == -- </dark_aqua></bold>");
  public static final TextKey PLATFORM = TextKey.of("environment.platform", "<aqua>Version: {version} - Platform: {platform}</aqua>");
  public static final TextKey JAVA = TextKey.of("environment.java", "<aqua>Java vendor: {vendor} - Java version: {version}</aqua>");
  public static final TextKey PROCESSOR_HEADER = TextKey.of("environment.header.processor", "<bold><dark_aqua> -- == Processor Overview == -- </dark_aqua></bold>");
  public static final TextKey CPU_MODEL = TextKey.of("environment.cpu_model", "<aqua>CPU model: {model}</aqua>");
  public static final TextKey CPU_ARCHITECTURE = TextKey.of("environment.cpu_architecture", "<aqua>CPU architecture: {architecture} - Available processors: {processors}</aqua>");
  public static final TextKey CPU_LOAD = TextKey.of("environment.cpu_load", "<aqua>CPU load: {system_load} - CPU live process load: {process_load}</aqua>");
  public static final TextKey GRAPHICS_HEADER = TextKey.of("environment.header.graphics", "<dark_gray>-=<blue> Graphics </blue>=-</dark_gray>");
  public static final TextKey GRAPHICS_ENTRY = TextKey.of("environment.graphics_entry", "<blue> {value}</blue>");
  public static final TextKey MEMORY_HEADER = TextKey.of("environment.header.memory", "<bold><dark_aqua> -- == Memory Information == -- </dark_aqua></bold>");
  public static final TextKey MEMORY_PHYSICAL = TextKey.of("environment.memory_physical", "<aqua>Physical memory - Total: {total} Free: {free} Used: {used}</aqua>");
  public static final TextKey MEMORY_VIRTUAL = TextKey.of("environment.memory_virtual", "<aqua>Virtual memory - Total: {total} Free: {free} Used: {used}</aqua>");
  public static final TextKey STORAGE_HEADER = TextKey.of("environment.header.storage", "<bold><dark_aqua> -- == Storage Information == -- </dark_aqua></bold>");
  public static final TextKey STORAGE_ENTRY = TextKey.of("environment.storage_entry", "<aqua> {value}</aqua>");
  public static final TextKey INTERFACE_HEADER = TextKey.of("environment.header.interface", "<bold><dark_aqua> -- == Interface Information == -- </dark_aqua></bold>");
  public static final TextKey INTERFACE_ENTRY = TextKey.of("environment.interface_entry", "<aqua> {value}</aqua>");
  public static final TextKey DISPLAY_HEADER = TextKey.of("environment.header.display", "<bold><dark_aqua> -- == Display Information == -- </dark_aqua></bold>");
  public static final TextKey DISPLAY_ENTRY = TextKey.of("environment.display_entry", "<aqua> {value}</aqua>");
  public static final TextKey SENSOR_HEADER = TextKey.of("environment.header.sensor", "<bold><dark_aqua> -- == Sensor Information == -- </dark_aqua></bold>");
  public static final TextKey SENSOR_ENTRY = TextKey.of("environment.sensor_entry", "<aqua> {value}</aqua>");
  public static final TextKey POWER_HEADER = TextKey.of("environment.header.power", "<bold><dark_aqua> -- == Power Information == -- </dark_aqua></bold>");
  public static final TextKey POWER_ENTRY = TextKey.of("environment.power_entry", "<aqua> {value}</aqua>");
  public static final TextKey HASTEBIN_LINK = TextKey.of("environment.hastebin_link", "<aqua>[Open environment report]</aqua>");
  public static final TextKey HASTEBIN_FAILED = TextKey.of("environment.hastebin_failed", "<dark_red>Failed to upload server information.</dark_red>");

  private EnvironmentMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(REACT_HEADER);
    builder.add(REACT_VERSION);
    builder.add(SERVER_TYPE);
    builder.add(SERVER_UPTIME);
    builder.add(PLATFORM_HEADER);
    builder.add(PLATFORM);
    builder.add(JAVA);
    builder.add(PROCESSOR_HEADER);
    builder.add(CPU_MODEL);
    builder.add(CPU_ARCHITECTURE);
    builder.add(CPU_LOAD);
    builder.add(GRAPHICS_HEADER);
    builder.add(GRAPHICS_ENTRY);
    builder.add(MEMORY_HEADER);
    builder.add(MEMORY_PHYSICAL);
    builder.add(MEMORY_VIRTUAL);
    builder.add(STORAGE_HEADER);
    builder.add(STORAGE_ENTRY);
    builder.add(INTERFACE_HEADER);
    builder.add(INTERFACE_ENTRY);
    builder.add(DISPLAY_HEADER);
    builder.add(DISPLAY_ENTRY);
    builder.add(SENSOR_HEADER);
    builder.add(SENSOR_ENTRY);
    builder.add(POWER_HEADER);
    builder.add(POWER_ENTRY);
    builder.add(HASTEBIN_LINK);
    builder.add(HASTEBIN_FAILED);
  }
}
