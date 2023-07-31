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

package com.volmit.react.content.sampler;

import art.arcane.chrono.PrecisionStopwatch;
import com.volmit.react.api.event.layer.ServerTickEvent;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.RollingSequence;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

public class SamplerHopperTickTime extends ReactCachedSampler implements Listener {
    public static final String ID = "hopper-tick-time";
    private int tickAverage = 15;
    private transient double maxDuration;
    private transient PrecisionStopwatch stopwatch;
    private transient RollingSequence average;
    private transient boolean running;

    public SamplerHopperTickTime() {
        super(ID, 50);
    }

    @Override
    public void start() {
        super.start();
        average = new RollingSequence(tickAverage);
        stopwatch = new PrecisionStopwatch();
        running = false;
        maxDuration = 0;
    }

    @Override
    public Material getIcon() {
        return Material.HOPPER;
    }

    @EventHandler
    public void on(ServerTickEvent e) {
        average.put(maxDuration);
        running = false;
        maxDuration = 0;
    }

    @EventHandler
    public void on(InventoryMoveItemEvent e) {
        if ((e.getSource().getHolder() instanceof Hopper) || (e.getDestination().getHolder() instanceof Hopper)) {
            if (!running) {
                stopwatch.resetAndBegin();
                running = true;
            } else {
                maxDuration = stopwatch.getMilliseconds();
            }
        }
    }

    @Override
    public double onSample() {
        return average.getAverage();
    }

    @Override
    public String format(double t) {
        return formattedValue(t) + formattedSuffix(t);
    }

    @Override
    public Component format(Component value, Component suffix) {
        return Component.empty().append(value).append(suffix);
    }

    @Override
    public String formattedValue(double t) {
        return Form.durationSplit(t, 2)[0];
    }

    @Override
    public String formattedSuffix(double t) {
        return Form.durationSplit(t, 2)[1] + " HOP";
    }
}
