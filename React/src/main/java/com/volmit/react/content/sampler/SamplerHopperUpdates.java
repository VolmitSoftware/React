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

import com.volmit.react.api.sampler.ReactCachedRateSampler;
import com.volmit.react.util.format.Form;
import org.bukkit.Material;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

public class SamplerHopperUpdates extends ReactCachedRateSampler implements Listener {
    public static final String ID = "hopper";

    public SamplerHopperUpdates() {
        super(ID, 1000);
    }

    @Override
    public Material getIcon() {
        return Material.HOPPER;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.HOPPER) {
            increment();
            getChunkCounter(e.getBlock()).addAndGet(1D);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(InventoryMoveItemEvent e) {
        if (e.getSource().getHolder() instanceof Hopper) {
            increment();
            getChunkCounter(e.getSource().getLocation().getBlock()).addAndGet(1D);
        } else if (e.getDestination().getHolder() instanceof Hopper) {
            increment();
            getChunkCounter(e.getDestination().getLocation().getBlock()).addAndGet(1D);
        }
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.round(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return "HOP/s";
    }
}
