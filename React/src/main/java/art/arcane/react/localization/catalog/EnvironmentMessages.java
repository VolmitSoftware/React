package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class EnvironmentMessages {
  public static final TextKey REACT_HEADER = TextKey.of("environment.header.react", "&3&l -- == React Info == -- &r");
  public static final TextKey REACT_VERSION = TextKey.of("environment.react_version", "&bReact version: {version}&r");
  public static final TextKey SERVER_TYPE = TextKey.of("environment.server_type", "&bServer type: {server}&r");
  public static final TextKey SERVER_UPTIME = TextKey.of("environment.server_uptime", "&bServer uptime: {uptime}&r");
  public static final TextKey PLATFORM_HEADER = TextKey.of("environment.header.platform", "&3&l -- == Platform Overview == -- &r");
  public static final TextKey PLATFORM = TextKey.of("environment.platform", "&bVersion: {version} - Platform: {platform}&r");
  public static final TextKey JAVA = TextKey.of("environment.java", "&bJava vendor: {vendor} - Java version: {version}&r");
  public static final TextKey PROCESSOR_HEADER = TextKey.of("environment.header.processor", "&3&l -- == Processor Overview == -- &r");
  public static final TextKey CPU_MODEL = TextKey.of("environment.cpu_model", "&bCPU model: {model}&r");
  public static final TextKey CPU_ARCHITECTURE = TextKey.of("environment.cpu_architecture", "&bCPU architecture: {architecture} - Available processors: {processors}&r");
  public static final TextKey CPU_LOAD = TextKey.of("environment.cpu_load", "&bCPU load: {system_load} - CPU live process load: {process_load}&r");
  public static final TextKey GRAPHICS_HEADER = TextKey.of("environment.header.graphics", "&8-=&9 Graphics &8=-&r");
  public static final TextKey GRAPHICS_ENTRY = TextKey.of("environment.graphics_entry", "&9 {value}&r");
  public static final TextKey MEMORY_HEADER = TextKey.of("environment.header.memory", "&3&l -- == Memory Information == -- &r");
  public static final TextKey MEMORY_PHYSICAL = TextKey.of("environment.memory_physical", "&bPhysical memory - Total: {total} Free: {free} Used: {used}&r");
  public static final TextKey MEMORY_VIRTUAL = TextKey.of("environment.memory_virtual", "&bVirtual memory - Total: {total} Free: {free} Used: {used}&r");
  public static final TextKey STORAGE_HEADER = TextKey.of("environment.header.storage", "&3&l -- == Storage Information == -- &r");
  public static final TextKey STORAGE_ENTRY = TextKey.of("environment.storage_entry", "&b {value}&r");
  public static final TextKey INTERFACE_HEADER = TextKey.of("environment.header.interface", "&3&l -- == Interface Information == -- &r");
  public static final TextKey INTERFACE_ENTRY = TextKey.of("environment.interface_entry", "&b {value}&r");
  public static final TextKey DISPLAY_HEADER = TextKey.of("environment.header.display", "&3&l -- == Display Information == -- &r");
  public static final TextKey DISPLAY_ENTRY = TextKey.of("environment.display_entry", "&b {value}&r");
  public static final TextKey SENSOR_HEADER = TextKey.of("environment.header.sensor", "&3&l -- == Sensor Information == -- &r");
  public static final TextKey SENSOR_ENTRY = TextKey.of("environment.sensor_entry", "&b {value}&r");
  public static final TextKey POWER_HEADER = TextKey.of("environment.header.power", "&3&l -- == Power Information == -- &r");
  public static final TextKey POWER_ENTRY = TextKey.of("environment.power_entry", "&b {value}&r");
  public static final TextKey HASTEBIN_LINK = TextKey.of("environment.hastebin_link", "&b[Open environment report]&r");
  public static final TextKey HASTEBIN_FAILED = TextKey.of("environment.hastebin_failed", "&4Failed to upload server information.&r");

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
