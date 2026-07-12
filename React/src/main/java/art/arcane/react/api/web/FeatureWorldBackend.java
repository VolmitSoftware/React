package art.arcane.react.api.web;

import art.arcane.react.React;
import art.arcane.react.api.web.dto.WorldDto;
import art.arcane.react.content.feature.FeaturePerWorldTickBudget;
import art.arcane.react.content.feature.perworld.PerWorldPressure;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.TomlCodec;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.io.IO;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FeatureWorldBackend implements WorldBackend {

    @Override
    public List<WorldDto> list() {
        return J.sResult(() -> {
            FeaturePerWorldTickBudget feature = React.feature(FeaturePerWorldTickBudget.ID);
            List<World> worlds = Bukkit.getWorlds();
            List<WorldDto> result = new ArrayList<>(worlds.size());
            for (World world : worlds) {
                WorldDto dto = new WorldDto();
                dto.key = WorldIdentity.serialize(world);
                dto.name = world.getName();
                if (feature != null && feature.isEnabled()) {
                    Map<UUID, PerWorldPressure.Mode> modes = feature.snapshotModes();
                    PerWorldPressure.Mode mode = modes.getOrDefault(world.getUID(), PerWorldPressure.Mode.NORMAL);
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
                result.add(dto);
            }
            return result;
        });
    }

    @Override
    public WorldDto update(String worldKey, Double budgetMs, Double panicMs, Double releaseMs) {
        NamespacedKey parsedWorldKey;
        try {
            parsedWorldKey = WorldIdentity.parse(worldKey);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        String canonicalWorldKey = parsedWorldKey.toString();
        return J.sResult(() -> {
            World world = WorldIdentity.resolve(parsedWorldKey).orElse(null);
            if (world == null) {
                return null;
            }
            FeaturePerWorldTickBudget feature = React.feature(FeaturePerWorldTickBudget.ID);
            if (feature != null) {
                feature.setWorldOverride(canonicalWorldKey, budgetMs, panicMs, releaseMs);
                try {
                    IO.writeAll(
                        React.instance.getDataFile("feature", FeaturePerWorldTickBudget.ID + ".toml"),
                        TomlCodec.toToml(feature, "feature:" + FeaturePerWorldTickBudget.ID)
                    );
                } catch (Throwable t) {
                    React.warn("Failed to persist world override for " + canonicalWorldKey + ": " + t.getMessage());
                }
                FeatureController featureController = React.controller(FeatureController.class);
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
            }
            WorldDto dto = new WorldDto();
            dto.key = WorldIdentity.serialize(world);
            dto.name = world.getName();
            if (feature != null) {
                Map<UUID, PerWorldPressure.Mode> modes = feature.snapshotModes();
                PerWorldPressure.Mode mode = modes.getOrDefault(world.getUID(), PerWorldPressure.Mode.NORMAL);
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
            return dto;
        });
    }
}
