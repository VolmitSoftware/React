package art.arcane.react.util.project.config;

import art.arcane.react.api.action.ReactAction;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.api.monitor.MonitoringSettings;
import art.arcane.react.api.rendering.MapSettings;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.content.action.ActionCaptureProfile;
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
import art.arcane.react.content.feature.FeatureAnimusCostSharePieMap;
import art.arcane.react.content.feature.FeatureAnimusNetworkListMap;
import art.arcane.react.content.feature.FeatureAnimusRuntimePressureOverlay;
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
import art.arcane.react.core.controller.EventController;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.HotloadController;
import art.arcane.react.core.controller.IncidentController;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.RuntimeTelemetryController;
import art.arcane.react.model.ReactConfiguration;

import java.util.ArrayList;
import java.util.List;

/** The Bukkit config types whose annotated docs React adds to the language catalog. */
public final class ConfigLocalizationTypes {
  private static final List<Class<?>> TYPES = createTypes();

  private ConfigLocalizationTypes() {
  }

  public static List<Class<?>> types() {
    return TYPES;
  }

  private static List<Class<?>> createTypes() {
    List<Class<?>> types = new ArrayList<>(List.of(
        ReactAction.class,
        ReactFeature.class,
        ReactTweak.class,
        WebConfiguration.class,
        RuntimeTelemetryController.class,
        ActionCaptureProfile.class,
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
        FeatureAnimusCostSharePieMap.class,
        FeatureAnimusNetworkListMap.class,
        FeatureAnimusRuntimePressureOverlay.class,
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
        EventController.class,
        HistoryController.class,
        HotloadController.class,
        IncidentController.class,
        MapController.class,
        MapSettings.class,
        MonitoringSettings.class,
        ReactConfiguration.class
    ));
    types.addAll(FeatureConfigLocalizationTypes.types());
    return List.copyOf(types);
  }
}
