package art.arcane.react.api.monitor;

import art.arcane.react.api.monitor.configuration.MonitorConfiguration;
import art.arcane.react.api.monitor.configuration.MonitorGroup;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import lombok.Data;

import java.util.function.Supplier;

/**
 * The {@code monitoring} section of {@code react.toml}: how much of the action bar monitor fits on screen and the
 * dashboard new players start with.
 */
@Data
@ConfigDescription("Default player monitor dashboard configuration.")
public class MonitoringSettings {
  private static volatile Supplier<MonitoringSettings> active = MonitoringSettings::new;

  @ConfigDoc(value = "Maximum number of monitor groups shown in the action bar at once.", impact = "Higher values reduce horizontal paging, but can make entries harder to read if labels are long.")
  private int actionBarHeaderSlots = 6;

  @ConfigDoc(value = "Maximum number of samplers shown in the focused action bar row at once.", impact = "Higher values reduce paging inside a group, but can compress readability on small displays.")
  private int actionBarSamplerSlots = 6;

  @ConfigDoc(value = "Default monitor groups and samplers shown to players.", impact = "Changing this alters the baseline monitor panel structure for new settings.")
  private MonitorConfiguration monitorConfiguration = defaultConfiguration();

  /**
   * The platform's live settings, read from {@code react.toml}.
   */
  public static MonitoringSettings active() {
    MonitoringSettings settings = active.get();
    return settings == null ? new MonitoringSettings() : settings;
  }

  public static void bind(Supplier<MonitoringSettings> source) {
    active = source == null ? MonitoringSettings::new : source;
  }

  static MonitorConfiguration defaultConfiguration() {
    return MonitorConfiguration.builder()
        .group(MonitorGroup.builder()
            .name("CPU")
            .color("#00ff73")
            .sampler("ticks-per-second")
            .sampler("tick-time")
            .sampler("processor-process-load")
            .sampler("processor-system-load")
            .sampler("processor-outside")
            .sampler("react-async-tick-time")
            .sampler("react-jobs-queue")
            .build())
        .group(MonitorGroup.builder()
            .name("Memory")
            .color("#ee00ff")
            .sampler("memory-used-after-gc")
            .sampler("memory-pressure")
            .build())
        .group(MonitorGroup.builder()
            .name("World")
            .color("#42cbf5")
            .sampler("chunks")
            .sampler("entities")
            .sampler("players")
            .build())
        .group(MonitorGroup.builder()
            .name("Physics")
            .color("#f25a02")
            .head("physics-event-span")
            .sampler("physics-event-span")
            .sampler("redstone-event-span")
            .sampler("fluid-event-span")
            .sampler("hopper-event-span")
            .build())
        .group(MonitorGroup.builder()
            .name("Iris")
            .color("#35d68f")
            .head("iris-generation-total-ms")
            .sampler("iris-generation-total-ms")
            .sampler("iris-chunks-per-second")
            .sampler("iris-pregen-queue")
            .sampler("iris-pregen-throughput")
            .build())
        .group(MonitorGroup.builder()
            .name("Adapt")
            .color("#f8b84f")
            .head("adapt-session-load")
            .sampler("adapt-session-load")
            .sampler("adapt-ability-ops")
            .sampler("adapt-ability-checks-per-tick")
            .sampler("adapt-world-policy-latency")
            .build())
        .build();
  }
}
