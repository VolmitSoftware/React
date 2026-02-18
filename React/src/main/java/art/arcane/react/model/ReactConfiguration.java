/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.model;

import art.arcane.react.React;
import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.api.monitor.configuration.MonitorConfiguration;
import art.arcane.react.api.monitor.configuration.MonitorGroup;
import art.arcane.react.content.sampler.*;
import art.arcane.react.util.config.ConfigDescription;
import art.arcane.react.util.config.ConfigDoc;
import art.arcane.react.util.config.ConfigFileSupport;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import lombok.Data;
import lombok.Getter;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Data
@ConfigDescription("Global React configuration. Controls default monitoring layout, value model tuning, and diagnostics behavior.")
public class ReactConfiguration {
  private static final Object CONFIG_LOCK = new Object();
  private static ReactConfiguration configuration;
  @ConfigDoc(value = "Entity priority model used by multiple React subsystems.", impact = "Changing these weights can alter culling, queueing, and visibility behavior.")
  private EntityPriority priority = new EntityPriority();

  @ConfigDoc(value = "Material value tuning used for recipe/value analysis.", impact = "Higher values can increase the influence of expensive items in calculations.")
  private ValueConfig value = new ValueConfig();

  @ConfigDoc(value = "Enables custom color styling in monitor views.", impact = "Set to false to keep output closer to vanilla/default color behavior.")
  private boolean customColors = true;

  @ConfigDoc(value = "Enables verbose console output for React internals.", impact = "Useful for troubleshooting but can create a lot of log noise.")
  private boolean verbose = false;

  @ConfigDoc(value = "Enables debug logging and additional diagnostics.", impact = "Use during debugging sessions; disable for normal production runtime.")
  private boolean debug = false;

  @ConfigDoc(
      value = "Controls how slow tick warnings are logged. Options: BLAME, SHORT, DETAILED.",
      impact = "BLAME highlights likely plugin/workload responsibility. SHORT logs minimal timing only. DETAILED logs the full diagnostic payload."
  )
  private SlowTickLogMode slowTickLogMode = SlowTickLogMode.BLAME;

  @ConfigDoc(value = "Enables plugin-gated secret integration features.", impact = "Disabled by default. Enable to allow Iris/Adapt secret bundles when dependencies are present.")
  private boolean integrationSecretsEnabled = false;

  @ConfigDoc(
      value = "Selects which Adapt ability ops metric React uses for displays and thresholds. Options: SUCCESSFUL_CHECKS, ALL_CHECKS.",
      impact = "SUCCESSFUL_CHECKS tracks only allowed checks. ALL_CHECKS is kept only for legacy config compatibility and is normalized to SUCCESSFUL_CHECKS at runtime."
  )
  private AdaptAbilityOpsMetricMode adaptAbilityOpsMetricMode = AdaptAbilityOpsMetricMode.SUCCESSFUL_CHECKS;

  @ConfigDoc(value = "Default monitor layout shown to players.", impact = "Changes affect the baseline monitoring dashboard composition and sampler grouping.")
  private Monitoring monitoring = new Monitoring();

  public static ReactConfiguration get() {
    synchronized (CONFIG_LOCK) {
      if (configuration == null) {
        try {
          configuration = loadConfig(new ReactConfiguration(), true);
          configuration.normalize();
        } catch (Throwable e) {
          e.printStackTrace();
          configuration = new ReactConfiguration();
        }
      }

      return configuration;
    }
  }

  public static boolean reload() {
    synchronized (CONFIG_LOCK) {
      try {
        configuration = loadConfig(configuration == null ? new ReactConfiguration() : configuration, false);
        configuration.normalize();
        return true;
      } catch (Throwable e) {
        React.warn("Failed to reload config.toml: " + e.getMessage());
        return false;
      }
    }
  }

  private static ReactConfiguration loadConfig(ReactConfiguration fallback, boolean overwriteOnFailure) throws IOException {
    File canonicalFile = React.instance.getDataFile("config.toml");
    File legacyFile = React.instance.getDataFile("config.json");
    return ConfigFileSupport.load(
        canonicalFile,
        legacyFile,
        ReactConfiguration.class,
        fallback,
        overwriteOnFailure,
        "main-config",
        "Created missing config [config.toml] from defaults."
    );
  }

  public static String adaptAbilityOpsMetricKey() {
    return get().getAdaptAbilityOpsMetricMode().metricKey();
  }

  public static String adaptAbilityOpsMetricLabel() {
    return get().getAdaptAbilityOpsMetricMode().displayLabel();
  }

  private void normalize() {
    if (adaptAbilityOpsMetricMode == null || adaptAbilityOpsMetricMode == AdaptAbilityOpsMetricMode.ALL_CHECKS) {
      adaptAbilityOpsMetricMode = AdaptAbilityOpsMetricMode.SUCCESSFUL_CHECKS;
    }
  }

  public enum AdaptAbilityOpsMetricMode {
    SUCCESSFUL_CHECKS(IntegrationMetricSchema.ADAPT_ABILITY_OPS, "successful"),
    ALL_CHECKS(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS, "all (legacy)");

    private final String metricKey;
    private final String displayLabel;

    AdaptAbilityOpsMetricMode(String metricKey, String displayLabel) {
      this.metricKey = metricKey;
      this.displayLabel = displayLabel;
    }

    public String metricKey() {
      return metricKey;
    }

    public String displayLabel() {
      return displayLabel;
    }
  }

  public enum SlowTickLogMode {
    BLAME,
    SHORT,
    DETAILED
  }

  @Getter
  @ConfigDescription("Material value configuration for React's value and weighting calculations.")
  public static class ValueConfig {
    @ConfigDoc(value = "Base value for materials before recipe and override adjustments.", impact = "Higher values raise baseline valuation globally.")
    private double baseValue = 100;

    @ConfigDoc(value = "Safety cap to prevent deep or cyclic recipe traversal from spiraling.", impact = "Higher values allow deeper recipe exploration but can increase processing cost.")
    private int maxRecipeListPrecaution = 50;

    @ConfigDoc(value = "Per-material multipliers overriding default valuation behavior.", impact = "Higher multipliers make specific materials more influential in value calculations.")
    private Map<String, Double> valueMutlipliers = defaultValueMultipliersOverrides();

    private Map<String, Double> defaultValueMultipliersOverrides() {
      Map<String, Double> f = new HashMap<>();
      f.put(Material.BLAZE_ROD.name(), 50D);
      f.put(Material.ENDER_PEARL.name(), 75D);
      f.put(Material.GHAST_TEAR.name(), 100D);
      f.put(Material.LEATHER.name(), 1.5D);
      f.put(Material.BEEF.name(), 1.125D);
      f.put(Material.PORKCHOP.name(), 1.125D);
      f.put(Material.EGG.name(), 1.335D);
      f.put(Material.CHICKEN.name(), 1.13D);
      f.put(Material.MUTTON.name(), 1.125D);
      f.put(Material.WHEAT.name(), 1.25D);
      f.put(Material.BEETROOT.name(), 1.25D);
      f.put(Material.CARROT.name(), 1.25D);
      f.put(Material.FLINT.name(), 1.35D);
      f.put(Material.IRON_ORE.name(), 1.75D);
      f.put(Material.DIAMOND_ORE.name(), 7D);
      f.put(Material.DIAMOND.name(), 25D);
      f.put(Material.NETHER_STAR.name(), 125D);
      f.put(Material.NETHERITE_INGOT.name(), 75D);
      f.put(Material.GOLD_ORE.name(), 4D);
      f.put(Material.LAPIS_ORE.name(), 3.5D);
      f.put(Material.COAL_ORE.name(), 1.35D);
      f.put(Material.REDSTONE_ORE.name(), 4.5D);
      f.put(Material.NETHER_GOLD_ORE.name(), 4.5D);
      f.put(Material.NETHER_QUARTZ_ORE.name(), 1.11D);
      f.put(Material.OAK_LOG.name(), 0.25D);
      f.put(Material.DARK_OAK_LOG.name(), 0.1D);
      f.put(Material.ACACIA_LOG.name(), 0.1D);
      f.put(Material.BIRCH_LOG.name(), 0.1D);
      f.put(Material.JUNGLE_LOG.name(), 0.1D);
      f.put(Material.SPRUCE_LOG.name(), 0.1D);
      f.put(Material.MANGROVE_LOG.name(), 0.1D);
      f.put(Material.COBBLESTONE.name(), 0.1D);
      f.put(Material.DIRT.name(), 0.01D);

      return f;
    }
  }

  @Data
  @ConfigDescription("Default player monitor dashboard configuration.")
  public static class Monitoring {
    @ConfigDoc(value = "Maximum number of monitor groups shown in the action bar at once.", impact = "Higher values reduce horizontal paging, but can make entries harder to read if labels are long.")
    private int actionBarHeaderSlots = 6;

    @ConfigDoc(value = "Maximum number of samplers shown in the focused action bar row at once.", impact = "Higher values reduce paging inside a group, but can compress readability on small displays.")
    private int actionBarSamplerSlots = 6;

    @ConfigDoc(value = "Default monitor groups and samplers shown to players.", impact = "Changing this alters the baseline monitor panel structure for new settings.")
    private MonitorConfiguration monitorConfiguration = MonitorConfiguration.builder()
        .group(MonitorGroup.builder()
            .name("CPU")
            .color("#00ff73")
            .sampler(SamplerTicksPerSecond.ID)
            .sampler(SamplerTickTime.ID)
            .sampler(SamplerProcessorProcessLoad.ID)
            .sampler(SamplerProcessorSystemLoad.ID)
            .sampler(SamplerProcessorOutsideLoad.ID)
            .sampler(SamplerReactAsyncTickTime.ID)
            .sampler(SamplerReactJobsQueue.ID)
            .build())
        .group(MonitorGroup.builder()
            .name("Memory")
            .color("#ee00ff")
            .sampler(SamplerMemoryUsedAfterGC.ID)
            .sampler(SamplerMemoryPressure.ID)
            .build())
        .group(MonitorGroup.builder()
            .name("World")
            .color("#42cbf5")
            .sampler(SamplerChunks.ID)
            .sampler(SamplerEntities.ID)
            .sampler(SamplerPlayers.ID)
            .build())
        .group(MonitorGroup.builder()
            .name("Physics")
            .color("#f25a02")
            .head(SamplerPhysicsTickTime.ID)
            .sampler(SamplerPhysicsTickTime.ID)
            .sampler(SamplerRedstoneTickTime.ID)
            .sampler(SamplerFluidTickTime.ID)
            .sampler(SamplerHopperTickTime.ID)
            .build())
        .group(MonitorGroup.builder()
            .name("Iris")
            .color("#35d68f")
            .head(SamplerIrisChunkStreamMS.ID)
            .sampler(SamplerIrisChunkStreamMS.ID)
            .sampler(SamplerIrisPregenQueue.ID)
            .sampler(SamplerIrisBiomeCacheHitRate.ID)
            .build())
        .group(MonitorGroup.builder()
            .name("Adapt")
            .color("#f8b84f")
            .head(SamplerAdaptSessionLoad.ID)
            .sampler(SamplerAdaptSessionLoad.ID)
            .sampler(SamplerAdaptAbilityOps.ID)
            .sampler(SamplerAdaptAbilityChecksPerTick.ID)
            .sampler(SamplerAdaptWorldPolicyLatency.ID)
            .build())
        .build();
  }
}
