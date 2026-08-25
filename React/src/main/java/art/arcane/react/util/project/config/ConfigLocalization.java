package art.arcane.react.util.project.config;

import art.arcane.react.api.action.ReactAction;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.content.action.ActionHopperNetworkNormalize;
import art.arcane.react.content.action.ActionIncidentPlaybook;
import art.arcane.react.content.action.ActionPrewarmCriticalChunks;
import art.arcane.react.content.action.ActionPurgeChunks;
import art.arcane.react.content.action.ActionPurgeEntities;
import art.arcane.react.content.action.ActionQuarantineHotChunks;
import art.arcane.react.content.action.ActionTrimEntitiesByAgePriority;
import art.arcane.react.content.action.ActionUnknown;
import art.arcane.react.content.feature.FeatureActivationRangeGovernor;
import art.arcane.react.content.feature.FeatureAdaptAbilityImpactListMap;
import art.arcane.react.content.feature.FeatureAdaptRuntimePressureOverlay;
import art.arcane.react.content.feature.FeatureAdaptRuntimeSurgeGuard;
import art.arcane.react.content.feature.FeatureAdaptiveEntitySleep;
import art.arcane.react.content.feature.FeatureAfkViewShedding;
import art.arcane.react.content.feature.FeatureChunkLoadGenCostMap;
import art.arcane.react.content.feature.FeatureChunkQuarantine;
import art.arcane.react.content.feature.FeatureChunkSamplerMap;
import art.arcane.react.content.feature.FeatureCircuitManager;
import art.arcane.react.content.feature.FeatureConfigLocalizationTypes;
import art.arcane.react.content.feature.FeatureCropFastForward;
import art.arcane.react.content.feature.FeatureDynamicActivationRange;
import art.arcane.react.content.feature.FeatureDynamicViewDistance;
import art.arcane.react.content.feature.FeatureEntityPressureHeatmap;
import art.arcane.react.content.feature.FeatureEntityTrimmer;
import art.arcane.react.content.feature.FeatureExplosionPacketBatching;
import art.arcane.react.content.feature.FeatureFarmBurstSmoother;
import art.arcane.react.content.feature.FeatureFastExplosions;
import art.arcane.react.content.feature.FeatureFastLeafDecay;
import art.arcane.react.content.feature.FeatureFurnaceBrewBatching;
import art.arcane.react.content.feature.FeatureHopperChainCoalescing;
import art.arcane.react.content.feature.FeatureHopperContainerThroughputMap;
import art.arcane.react.content.feature.FeatureHopperItemIndex;
import art.arcane.react.content.feature.FeatureHopperTokenBucket;
import art.arcane.react.content.feature.FeatureIncidentMode;
import art.arcane.react.content.feature.FeatureIrisBiomeChunkSharePieMap;
import art.arcane.react.content.feature.FeatureIrisGenerationPressureOverlay;
import art.arcane.react.content.feature.FeatureIrisTerrainSurgeGuard;
import art.arcane.react.content.feature.FeatureIrisWorldChunkSharePieMap;
import art.arcane.react.content.feature.FeatureItemBackpressure;
import art.arcane.react.content.feature.FeatureItemSuperStacker;
import art.arcane.react.content.feature.FeatureLazyGravity;
import art.arcane.react.content.feature.FeatureMinecartTether;
import art.arcane.react.content.feature.FeatureMobStacking;
import art.arcane.react.content.feature.FeaturePathfinderBudget;
import art.arcane.react.content.feature.FeaturePerWorldTickBudget;
import art.arcane.react.content.feature.FeaturePlayerImpactOverlay;
import art.arcane.react.content.feature.FeaturePluginEventImpactListMap;
import art.arcane.react.content.feature.FeaturePluginEventImpactPieMap;
import art.arcane.react.content.feature.FeaturePortalTrafficSmoother;
import art.arcane.react.content.feature.FeatureRandomTickGovernor;
import art.arcane.react.content.feature.FeatureRedstoneActivityHeatmap;
import art.arcane.react.content.feature.FeatureRedstoneClockGovernor;
import art.arcane.react.content.feature.FeatureSpawnBurstLimiter;
import art.arcane.react.content.feature.FeatureSpawnerLightCache;
import art.arcane.react.content.feature.FeatureTickSpikeOriginReplayMap;
import art.arcane.react.content.feature.FeatureTrackerRangeGovernor;
import art.arcane.react.content.feature.FeatureTrinityIncidentMode;
import art.arcane.react.content.feature.FeatureUnknown;
import art.arcane.react.content.feature.perworld.WorldBudgetOverride;
import art.arcane.react.content.tweak.TweakEntityBubbler;
import art.arcane.react.content.tweak.TweakEntityCrowdPrevention;
import art.arcane.react.content.tweak.TweakEntityHardstop;
import art.arcane.react.content.tweak.TweakExperienceOrbMerge;
import art.arcane.react.content.tweak.TweakFastColumns;
import art.arcane.react.content.tweak.TweakFastDrops;
import art.arcane.react.content.tweak.TweakFastEntityIncineration;
import art.arcane.react.content.tweak.TweakFastFallingBlocks;
import art.arcane.react.content.tweak.TweakFastFire;
import art.arcane.react.content.tweak.TweakFastFluids;
import art.arcane.react.content.tweak.TweakFastSnow;
import art.arcane.react.content.tweak.TweakHopperIndex;
import art.arcane.react.content.tweak.TweakHopperLimit;
import art.arcane.react.content.tweak.TweakItemDespawnAccelerator;
import art.arcane.react.content.tweak.TweakProjectileLimiter;
import art.arcane.react.content.tweak.TweakServerHibernator;
import art.arcane.react.content.tweak.TweakShorthands;
import art.arcane.react.content.tweak.TweakSpawnerPlayerRadius;
import art.arcane.react.content.tweak.TweakUnknown;
import art.arcane.react.content.tweak.TweakVehicleIdleBrake;
import art.arcane.react.core.controller.ConfigInputController;
import art.arcane.react.core.controller.HotloadController;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.IncidentController;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConfigLocalization {
  private static final String KEY_PREFIX = "config.documentation.annotation.";
  private static final List<Class<?>> CONFIG_TYPES = createConfigTypes();

  private ConfigLocalization() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    Set<Class<?>> visited = new HashSet<>();
    for (Class<?> type : CONFIG_TYPES) {
      addType(builder, type, visited);
    }
  }

  public static String description(Class<?> type) {
    Class<?> cursor = type;
    while (cursor != null && cursor != Object.class) {
      ConfigDescription annotation = cursor.getDeclaredAnnotation(ConfigDescription.class);
      if (annotation != null) {
        String english = annotation.value().strip();
        return ReactLanguage.raw(descriptionId(cursor), english);
      }
      cursor = cursor.getSuperclass();
    }
    return "";
  }

  public static String fieldSummary(Field field) {
    if (field == null) {
      return "";
    }
    ConfigDoc annotation = field.getAnnotation(ConfigDoc.class);
    if (annotation == null) {
      return "";
    }
    String english = annotation.value().strip();
    return ReactLanguage.raw(fieldSummaryId(field), english);
  }

  public static String fieldImpact(Field field) {
    if (field == null) {
      return "";
    }
    ConfigDoc annotation = field.getAnnotation(ConfigDoc.class);
    if (annotation == null || annotation.impact().isBlank()) {
      return "";
    }
    String english = annotation.impact().strip();
    return ReactLanguage.raw(fieldImpactId(field), english);
  }

  public static String descriptionId(Class<?> type) {
    return KEY_PREFIX + typeId(type) + ".description";
  }

  public static String fieldSummaryId(Field field) {
    return KEY_PREFIX + typeId(field.getDeclaringClass()) + ".field." + normalize(field.getName()) + ".summary";
  }

  public static String fieldImpactId(Field field) {
    return KEY_PREFIX + typeId(field.getDeclaringClass()) + ".field." + normalize(field.getName()) + ".impact";
  }

  static List<Class<?>> catalogTypes() {
    return CONFIG_TYPES;
  }

  private static List<Class<?>> createConfigTypes() {
    List<Class<?>> types = new ArrayList<>(List.of(
        ReactAction.class,
        ReactFeature.class,
        ReactTweak.class,
        WebConfiguration.class,
        ActionCollectGarbage.class,
        ActionHopperNetworkNormalize.class,
        ActionIncidentPlaybook.class,
        ActionPrewarmCriticalChunks.class,
        ActionPurgeChunks.class,
        ActionPurgeEntities.class,
        ActionQuarantineHotChunks.class,
        ActionTrimEntitiesByAgePriority.class,
        ActionUnknown.class,
        FeatureActivationRangeGovernor.class,
        FeatureAdaptAbilityImpactListMap.class,
        FeatureAdaptRuntimePressureOverlay.class,
        FeatureAdaptRuntimeSurgeGuard.class,
        FeatureAdaptiveEntitySleep.class,
        FeatureAfkViewShedding.class,
        FeatureChunkLoadGenCostMap.class,
        FeatureChunkQuarantine.class,
        FeatureChunkSamplerMap.class,
        FeatureCircuitManager.class,
        FeatureCropFastForward.class,
        FeatureDynamicActivationRange.class,
        FeatureDynamicViewDistance.class,
        FeatureEntityPressureHeatmap.class,
        FeatureEntityTrimmer.class,
        FeatureExplosionPacketBatching.class,
        FeatureFarmBurstSmoother.class,
        FeatureFastExplosions.class,
        FeatureFastLeafDecay.class,
        FeatureFurnaceBrewBatching.class,
        FeatureHopperChainCoalescing.class,
        FeatureHopperContainerThroughputMap.class,
        FeatureHopperItemIndex.class,
        FeatureHopperTokenBucket.class,
        FeatureIncidentMode.class,
        FeatureIrisBiomeChunkSharePieMap.class,
        FeatureIrisGenerationPressureOverlay.class,
        FeatureIrisTerrainSurgeGuard.class,
        FeatureIrisWorldChunkSharePieMap.class,
        FeatureItemBackpressure.class,
        FeatureItemSuperStacker.class,
        FeatureLazyGravity.class,
        FeatureMinecartTether.class,
        FeatureMobStacking.class,
        FeaturePathfinderBudget.class,
        FeaturePerWorldTickBudget.class,
        FeaturePlayerImpactOverlay.class,
        FeaturePluginEventImpactListMap.class,
        FeaturePluginEventImpactPieMap.class,
        FeaturePortalTrafficSmoother.class,
        FeatureRandomTickGovernor.class,
        FeatureRedstoneActivityHeatmap.class,
        FeatureRedstoneClockGovernor.class,
        FeatureSpawnBurstLimiter.class,
        FeatureSpawnerLightCache.class,
        FeatureTickSpikeOriginReplayMap.class,
        FeatureTrackerRangeGovernor.class,
        FeatureTrinityIncidentMode.class,
        FeatureUnknown.class,
        WorldBudgetOverride.class,
        TweakEntityBubbler.class,
        TweakEntityCrowdPrevention.class,
        TweakEntityHardstop.class,
        TweakExperienceOrbMerge.class,
        TweakFastColumns.class,
        TweakFastDrops.class,
        TweakFastEntityIncineration.class,
        TweakFastFallingBlocks.class,
        TweakFastFire.class,
        TweakFastFluids.class,
        TweakFastSnow.class,
        TweakHopperIndex.class,
        TweakHopperLimit.class,
        TweakItemDespawnAccelerator.class,
        TweakProjectileLimiter.class,
        TweakServerHibernator.class,
        TweakShorthands.class,
        TweakSpawnerPlayerRadius.class,
        TweakUnknown.class,
        TweakVehicleIdleBrake.class,
        ConfigInputController.class,
        HistoryController.class,
        HotloadController.class,
        IncidentController.class,
        MapController.class,
        ReactConfiguration.class
    ));
    types.addAll(FeatureConfigLocalizationTypes.types());
    return List.copyOf(types);
  }

  private static void addType(MessageCatalog.Builder builder, Class<?> type, Set<Class<?>> visited) {
    if (type == null || !visited.add(type)) {
      return;
    }

    ConfigDescription description = type.getDeclaredAnnotation(ConfigDescription.class);
    if (description != null && !description.value().isBlank()) {
      builder.add(TextKey.of(descriptionId(type), description.value().strip()));
    }

    Field[] fields = type.getDeclaredFields();
    Arrays.sort(fields, Comparator.comparing(Field::getName));
    for (Field field : fields) {
      ConfigDoc annotation = field.getAnnotation(ConfigDoc.class);
      if (annotation == null) {
        continue;
      }
      builder.add(TextKey.of(fieldSummaryId(field), annotation.value().strip()));
      if (!annotation.impact().isBlank()) {
        builder.add(TextKey.of(fieldImpactId(field), annotation.impact().strip()));
      }
    }

    Class<?>[] nestedTypes = type.getDeclaredClasses();
    Arrays.sort(nestedTypes, Comparator.comparing(Class::getName));
    for (Class<?> nestedType : nestedTypes) {
      addType(builder, nestedType, visited);
    }
  }

  private static String typeId(Class<?> type) {
    return normalize(type == null ? "unknown" : type.getName());
  }

  private static String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replace('$', '_').replaceAll("[^a-z0-9_.-]", "_");
  }
}
