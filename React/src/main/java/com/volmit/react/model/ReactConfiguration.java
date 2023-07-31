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

package com.volmit.react.model;

import com.google.gson.Gson;
import com.volmit.react.React;
import com.volmit.react.api.entity.EntityPriority;
import com.volmit.react.api.monitor.configuration.MonitorConfiguration;
import com.volmit.react.api.monitor.configuration.MonitorGroup;
import com.volmit.react.content.sampler.*;
import com.volmit.react.util.io.IO;
import com.volmit.react.util.json.JSONObject;
import lombok.Data;
import lombok.Getter;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Data
public class ReactConfiguration {
    private static ReactConfiguration configuration;
    private EntityPriority priority = new EntityPriority();
    private ValueConfig value = new ValueConfig();
    private boolean customColors = true;
    private boolean verbose = false;
    private Monitoring monitoring = new Monitoring();

    public static ReactConfiguration get() {
        if (configuration == null) {
            ReactConfiguration dummy = new ReactConfiguration();
            File l = React.instance.getDataFile("config.json");

            if (!l.exists()) {
                try {
                    IO.writeAll(l, new JSONObject(new Gson().toJson(dummy)).toString(4));
                } catch (IOException e) {
                    e.printStackTrace();
                    configuration = dummy;
                    return dummy;
                }
            }

            try {
                configuration = new Gson().fromJson(IO.readAll(l), ReactConfiguration.class);
                IO.writeAll(l, new JSONObject(new Gson().toJson(configuration)).toString(4));
            } catch (IOException e) {
                e.printStackTrace();
                configuration = new ReactConfiguration();
            }
        }

        return configuration;
    }

    @Getter
    public static class ValueConfig {
        private double baseValue = 100;
        private int maxRecipeListPrecaution = 50;
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
    public static class Monitoring {
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
                .build();
    }
}
