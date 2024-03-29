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

package com.volmit.react.content.feature;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.content.sampler.SamplerTickTime;
import com.volmit.react.model.MinMax;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RollingSequence;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeatureDynamicViewDistance extends ReactFeature implements Listener {
    public static final String ID = "dynamic-view-distance";
    public int updateCooldownSeconds = 120;
    private MinMax viewDistance = new MinMax(2, 16);
    private MinMax simulationDistance = new MinMax(2, 10);
    private MinMax lerpTickTime = new MinMax(10, 100);
    private MinMax lerpPlayersOnline = new MinMax(3, 100);
    private transient RollingSequence ttAvg;
    private Map<World, Long> lastUpdate;

    public FeatureDynamicViewDistance() {
        super(ID);
    }

    public boolean updateWorld(World world) {
        int vd = world.getViewDistance();
        int sd = world.getSimulationDistance();
        int players = Bukkit.getOnlinePlayers().size();
        double gps = ttAvg.getAverage();

        int newVD = M.min(lerp(lerpTickTime, viewDistance, gps),
                lerp(lerpPlayersOnline, viewDistance, players)).intValue();
        int newSD = M.min(lerp(lerpTickTime, simulationDistance, gps),
                lerp(lerpPlayersOnline, simulationDistance, players)).intValue();
        newSD = Math.min(newSD, newVD);

        List<String> m = new ArrayList<>();
        if (vd != newVD) {
            m.add("View Distance: " + vd + " -> " + newVD);
            Curse.on(world).method("setViewDistance", int.class).invoke(newVD);
        }

        if (sd != newSD) {
            m.add("Simulation Distance: " + sd + " -> " + newSD);
            Curse.on(world).method("setSimulationDistance", int.class).invoke(newSD);
        }

        if (!m.isEmpty()) {
            React.verbose(world.getName() + ": " + String.join(" ", m));
            return true;
        }

        return false;
    }

    public double lerp(MinMax range, MinMax output, double inRange) {
        return Math.max(Math.min(output.getMax(),
                        M.lerp(output.getMax(), output.getMin(), M.lerpInverse(range.getMin(), range.getMax(), inRange))),
                output.getMin());
    }

    @Override
    public void onActivate() {
        viewDistance.setMax(Math.min(viewDistance.getMax(), Bukkit.getServer().getViewDistance()));
        simulationDistance.setMax(Math.min(simulationDistance.getMax(), Bukkit.getServer().getSimulationDistance()));
        ttAvg = new RollingSequence(10);
        ttAvg.put(0);
        lastUpdate = new HashMap<>();
    }

    @Override
    public void onDeactivate() {
    }

    @Override
    public int getTickInterval() {
        return 1000;
    }

    @Override
    public void onTick() {
        ttAvg.put(React.sampler(SamplerTickTime.ID).sample());
        for (World i : Bukkit.getWorlds()) {
            if (lastUpdate.getOrDefault(i, 0L) < System.currentTimeMillis() - (updateCooldownSeconds * 1000L)) {
                if (updateWorld(i)) {
                    lastUpdate.put(i, System.currentTimeMillis());
                }
            }
        }
    }
}
