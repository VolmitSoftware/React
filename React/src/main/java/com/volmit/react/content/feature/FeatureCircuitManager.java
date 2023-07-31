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

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.content.sampler.SamplerRedstoneTickTime;
import com.volmit.react.model.Circuit;
import com.volmit.react.model.CircuitServer;
import com.volmit.react.util.scheduling.J;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;

public class FeatureCircuitManager extends ReactFeature implements Listener {
    public static final String ID = "circuit-manager";
    /**
     * Stop the biggest circuit when the redstone milliseconds exceeds this value
     */
    private double maxCircuitMS = 15;
    private transient CircuitServer circuitServer;

    public FeatureCircuitManager() {
        super(ID);
    }

    @Override
    public void onActivate() {
        circuitServer = new CircuitServer();

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
        J.s(() -> circuitServer.tick());

        if (React.sampler(SamplerRedstoneTickTime.ID).sample() > maxCircuitMS) {
            Circuit c = circuitServer.worst();

            if (c != null) {
                c.stop();
                React.warn("Stopping Circuit " + c.getId());
            }
        }
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        circuitServer.remove(e.getBlock());
    }

    @EventHandler
    public void on(BlockPistonExtendEvent e) {
        circuitServer.event(e.getBlock());

        for (Block i : e.getBlocks()) {
            circuitServer.event(i);
        }
    }

    @EventHandler
    public void on(BlockPistonRetractEvent e) {
        circuitServer.event(e.getBlock());

        for (Block i : e.getBlocks()) {
            circuitServer.event(i);
        }
    }

    @EventHandler
    public void on(BlockRedstoneEvent e) {
        Circuit c = circuitServer.event(e.getBlock());

        if (c != null && c.getStop().get()) {
            e.setNewCurrent(e.getOldCurrent());
        }
    }
}
