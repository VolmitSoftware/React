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

import com.volmit.react.util.math.BlockPosition;
import lombok.Data;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class CircuitServer {
    public static final AtomicBoolean allowTicking = new AtomicBoolean(false);
    private final Map<World, CircuitWorld> circuitWorlds;

    public CircuitServer() {
        this.circuitWorlds = new HashMap<>();
    }

    public Circuit worst() {
        return circuitWorlds.values().stream().map(CircuitWorld::worst)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(i -> i.getEvents().get()))
                .orElse(null);
    }

    public Circuit event(Block block) {
        CircuitWorld w = getOrCreateWorld(block.getWorld());
        long id = w.getNeighborCircuit(block);
        return w.getOrCreateCircuit(id);
    }

    public void remove(Block block) {
        CircuitWorld w = getWorld(block.getWorld());

        if (w != null) {
            w.remove(new BlockPosition(block));
        }
    }

    public void tick() {
        circuitWorlds.entrySet().removeIf(i -> {
            i.getValue().tick();
            return i.getValue().countCircuits() == 0;
        });
    }

    public Circuit getCircuitAt(Block block) {
        CircuitWorld world = getWorld(block.getWorld());

        if (world != null) {
            return world.getCircuitAt(new BlockPosition(block));
        }

        return null;
    }

    private void writeCircuit(Block block, long id) {
        getOrCreateWorld(block.getWorld()).writeCircuit(block, id);
    }

    public CircuitWorld getOrCreateWorld(World world) {
        CircuitWorld cw = getWorld(world);

        if (cw == null) {
            cw = new CircuitWorld(world);
            getCircuitWorlds().put(world, cw);
        }

        return cw;
    }

    public CircuitWorld getWorld(World world) {
        return circuitWorlds.get(world);
    }

    public int countCircuits() {
        int c = 0;

        for (CircuitWorld i : circuitWorlds.values()) {
            c += i.countCircuits();
        }

        return c;
    }

    public int countBlocks() {
        int c = 0;

        for (CircuitWorld i : circuitWorlds.values()) {
            c += i.countBlocks();
        }

        return c;
    }
}
