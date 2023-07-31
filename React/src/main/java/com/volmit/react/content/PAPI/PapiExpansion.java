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

package com.volmit.react.content.PAPI;

import com.google.common.collect.Maps;
import com.volmit.react.React;
import com.volmit.react.api.feature.Feature;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.api.tweak.Tweak;
import com.volmit.react.core.controller.FeatureController;
import com.volmit.react.core.controller.SampleController;
import com.volmit.react.core.controller.TweakController;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.reflect.Platform;
import com.volmit.react.util.registry.Registry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

public class PapiExpansion extends PlaceholderExpansion {
    private final Map<String, Function<Feature, String>> featureMap = Maps.newHashMap();
    private final Map<String, Function<Tweak, String>> tweakMap = Maps.newHashMap();
    private final Map<String, Function<String, String>> statMap = Maps.newHashMap();
    private final Map<String, Function<Sampler, String>> samplerMap = Maps.newHashMap();


    public PapiExpansion() {
        // STATS
        // this should be %react_stat_<ID>% where ID is the id of the stat eg: %react_stat_tps%
        statMap.put("tps", stat -> "print the tos here lol");
        statMap.put("version", stat -> React.instance.getDescription().getVersion());
        statMap.put("server-version", stat -> Platform.getVersion());
        statMap.put("server-type", stat -> Bukkit.getVersion());
        statMap.put("java-vendor", stat -> Platform.ENVIRONMENT.getJavaVendor());
        statMap.put("total-storage", stat -> Form.memSize(Platform.STORAGE.getTotalSpace()));
        statMap.put("free-storage", stat -> Form.memSize(Platform.STORAGE.getFreeSpace()));
        statMap.put("used-storage", stat -> Form.memSize(Platform.STORAGE.getUsedSpace()));
        statMap.put("total-memory", stat -> Form.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory()));
        statMap.put("free-memory", stat -> Form.memSize(Platform.MEMORY.PHYSICAL.getFreeMemory()));
        statMap.put("used-memory", stat -> Form.memSize(Platform.MEMORY.PHYSICAL.getUsedMemory()));
        statMap.put("total-memory-v", stat -> Form.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory()));
        statMap.put("free-memory-v", stat -> Form.memSize(Platform.MEMORY.VIRTUAL.getFreeMemory()));
        statMap.put("used-memory-v", stat -> Form.memSize(Platform.MEMORY.VIRTUAL.getUsedMemory()));
        statMap.put("cpu-architecture", stat -> Platform.CPU.getArchitecture());
        statMap.put("cpu-processors-available", stat -> String.valueOf(Platform.CPU.getAvailableProcessors()));
        statMap.put("cpu-load-machine", stat -> Form.pc(Platform.CPU.getCPULoad()));
        statMap.put("cpu-load-server", stat -> Form.pc(Platform.CPU.getLiveProcessCPULoad()));


        // TWEAKS
        // this should be %react_tweak_<Category>_<ID>% where ID is the id of the tweak eg: %react_tweak_enabled_entity-bubbler% -> {true/false}
        tweakMap.put("enabled", tweak -> tweak.isEnabled() ? "true" : "false");
        tweakMap.put("name", tweak -> tweak.getName() != null ? tweak.getName() : "null");
        tweakMap.put("interval", tweak -> String.valueOf(tweak.getTickInterval()));


        // FEATURES
        // this should be %react_feature_<Category>_<ID>% where ID is the id of the feature eg: %react_feature_enabled_fast-leaf-decay% -> {true/false}
        featureMap.put("enabled", feature -> feature.isEnabled() ? "true" : "false");
        featureMap.put("name", feature -> feature.getName() != null ? feature.getName() : "null");
        featureMap.put("interval", feature -> String.valueOf(feature.getTickInterval()));

        // SAMPLERS
        samplerMap.put("chunks", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("chunks-generated", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("chunks-loaded", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("entities", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("entities-spawns", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("event-handles-per-tick", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("events-listeners", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("event-time", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("fluid-tick-time", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("fluid", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("hopper-tick-time", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("hopper", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("memory-free", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("memory-garbage", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("memory-pressure", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("memory-used", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("memory-used-after-gc", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("physics-tick-time", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("physics", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("players", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("processor-outside", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("processor-process-load", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("processor-system-load", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("react-async-tick-time", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("react-job-budget", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("react-job-queue-time", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("react-jobs-queue", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("react-sync-tick-time", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("redstone-tick-time", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("redstone", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("ticks-per-second", sampler -> sampler.formattedValue(sampler.sample()));
        samplerMap.put("tick-time", sampler -> sampler.formattedValue(sampler.sample()));
    }


    @Override
    public @NotNull String getIdentifier() {
        return React.instance.getDescription().getName().toLowerCase();
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", React.instance.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return React.instance.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        //arg0: stat, tweak, feature | arg1: enabled, ... | arg2: id
        Registry<Tweak> tweaks = React.controller(TweakController.class).getTweaks();
        Registry<Feature> features = React.controller(FeatureController.class).getFeatures();
        Registry<Sampler> samplers = React.controller(SampleController.class).getSamplers();

        String[] args = params.split("_");
        Player p = player.getPlayer();
        String key = args[0];
        React.info("PAPI - Params: " + params);

        // Handle tweaks and their attributes %react_tweak_<Category>_<ID>%
        // IE: %react_tweak_enabled_entity-bubbler% Should return true/false
        if (key.equals("tweak")) {
            React.info("PAPI - Tweak: " + args[2] + " | " + args[1] + " | " + args[0]);
            String tweakAttribute = args[1];
            String tweakId = args[2];
            for (Tweak tweak : tweaks.all()) {
                React.info("PAPI - Tweak: " + tweak.getId());
            }

            if (tweakMap.containsKey(tweakAttribute)) {
                return tweakMap.get(tweakAttribute).apply(tweaks.get(tweakId));
            }
        }

        // Handle features and their attributes %react_feature_<Category>_<ID>%
        // IE: %react_feature_enabled_entity-bubbler% Should return true/false
        if (key.equals("feature")) {
            React.info("PAPI - Feature: " + args[2] + " | " + args[1] + " | " + args[0]);
            String featureAttribute = args[1];
            String featureId = args[2];
            for (Feature feature : features.all()) {
                React.info("PAPI - Feature: " + feature.getId());
            }

            if (featureMap.containsKey(featureAttribute)) {
                return featureMap.get(featureAttribute).apply(features.get(featureId));
            }
        }


        // Handle statistics and their attributes %react_stat_<ID>%
        // IE: %react_stat_free-memory% Should return the free memory
        if (key.equals("stat")) {
            React.info("PAPI - Stat: " + args[1] + " | " + args[0]);
            String statAttribute = args[1];
            String statId = args[2];
            for (String stat : statMap.keySet()) {
                React.info("PAPI - Stat: " + stat);
            }

            if (statMap.containsKey(statAttribute)) {
                return statMap.get(statAttribute).apply(statId);
            }
        }

        // Handle samplers and their attributes %react_sampler_<ID>%
        // IE: %react_sampler_chunks% Should return the number of chunks loaded
        if (key.equals("sampler")) {
            String samplerId = args[1];
            for (Sampler sampler : samplers.all()) {
                React.info("PAPI - Sampler: " + sampler.getId());
                if (samplerId.equals(sampler.getId())) {
                    React.info("PAPI - Sampler LOCATED: " + sampler.getId() + " | " + samplerId);
                }
            }

            if (samplerMap.containsKey(samplerId)) {
                return samplerMap.get(samplerId).apply(samplers.get(samplerId));
            }
        }
        return null;
    }

}

