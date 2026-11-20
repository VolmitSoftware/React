package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.WorldDto;
import art.arcane.react.content.feature.FeaturePerWorldTickBudget;
import art.arcane.react.content.feature.perworld.PerWorldPressure;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.platform.WorldKeys;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.TomlFiles;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FeatureWorldBackend implements WorldBackend {

    @Override
    public List<WorldDto> list() {
        return J.sResult(() -> {
            FeaturePerWorldTickBudget feature = ReactComponents.feature(FeaturePerWorldTickBudget.ID);
            WorldHost host = worlds();
            List<ReactWorld> worlds = host.worlds();
            List<WorldDto> result = new ArrayList<>(worlds.size());
            for (ReactWorld world : worlds) {
                WorldDto dto = new WorldDto();
                dto.key = host.worldKey(world);
                dto.name = world.name();
                describe(dto, world, feature, feature != null && feature.isEnabled());
                result.add(dto);
            }
            return result;
        });
    }

    @Override
    public WorldDto update(String worldKey, Double budgetMs, Double panicMs, Double releaseMs) {
        String canonicalWorldKey;
        try {
            canonicalWorldKey = WorldKeys.parse(worldKey);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return J.sResult(() -> {
            WorldHost host = worlds();
            ReactWorld world = null;
            for (ReactWorld candidate : host.worlds()) {
                if (canonicalWorldKey.equals(host.worldKey(candidate))) {
                    world = candidate;
                    break;
                }
            }
            if (world == null) {
                return null;
            }
            FeaturePerWorldTickBudget feature = ReactComponents.feature(FeaturePerWorldTickBudget.ID);
            if (feature != null) {
                feature.setWorldOverride(canonicalWorldKey, budgetMs, panicMs, releaseMs);
                Throwable persistenceFailure = null;
                try {
                    ReactPlatform platform = ReactPlatforms.get();
                    File file = platform.registryHost().orElseThrow(() -> new IllegalStateException("No registry host on this platform"))
                        .configFile("feature", FeaturePerWorldTickBudget.ID + ".toml");
                    TomlFiles.save(file, feature, "feature:" + FeaturePerWorldTickBudget.ID);
                } catch (Throwable t) {
                    persistenceFailure = t;
                    ReactComponents.reportError("Unexpected React failure", t);
                }
                FeatureController featureController = ReactComponents.features();
                if (featureController != null && featureController.getFeatures() != null) {
                    boolean wasActive = featureController.getActiveFeatures().containsKey(feature.getId());
                    feature.loadConfiguration();
                    boolean nowEnabled = feature.isEnabled();
                    if (wasActive && !nowEnabled) {
                        featureController.deactivateFeature(feature);
                    } else if (!wasActive && nowEnabled) {
                        featureController.activateFeature(feature);
                    } else if (wasActive) {
                        featureController.deactivateFeature(feature);
                        featureController.activateFeature(feature);
                    }
                }
                WebRevisions.bump(WebRevisions.Kind.WORLDS);
                WebRevisions.bump(WebRevisions.Kind.FEATURES);
                if (persistenceFailure != null) {
                    throw new IllegalStateException(
                        "Failed to persist world override for " + canonicalWorldKey,
                        persistenceFailure
                    );
                }
            }
            WorldDto dto = new WorldDto();
            dto.key = host.worldKey(world);
            dto.name = world.name();
            describe(dto, world, feature, feature != null);
            return dto;
        });
    }

    private static void describe(WorldDto dto, ReactWorld world, FeaturePerWorldTickBudget feature, boolean active) {
        if (active) {
            Map<UUID, PerWorldPressure.Mode> modes = feature.snapshotModes();
            PerWorldPressure.Mode mode = modes.getOrDefault(world.id(), PerWorldPressure.Mode.NORMAL);
            dto.pressureMode = mode.name();
            dto.budgetMs = feature.effectiveBudgetMsFor(dto.key);
            dto.panicMs = feature.effectivePanicMsFor(dto.key);
            dto.releaseMs = feature.effectiveReleaseMsFor(dto.key);
        } else {
            dto.pressureMode = PerWorldPressure.Mode.NORMAL.name();
            dto.budgetMs = 35.0;
            dto.panicMs = 50.0;
            dto.releaseMs = 28.0;
        }
    }

    private static WorldHost worlds() {
        return ReactPlatforms.get().worldHost().orElseThrow(() -> new IllegalStateException("No world host on this platform"));
    }
}
