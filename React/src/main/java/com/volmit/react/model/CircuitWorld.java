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
import com.volmit.react.util.math.Direction;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RNG;
import lombok.Data;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Repeater;

import java.util.*;

@Data
public class CircuitWorld {
    private final World world;
    private final Map<Long, Circuit> circuits;
    private final Map<BlockPosition, Long> blocks;

    public CircuitWorld(World world) {
        this.world = world;
        this.circuits = new HashMap<>();
        this.blocks = new HashMap<>();
    }

    public Circuit worst() {
        return circuits.values().stream()
                .max(Comparator.comparingInt(a -> a.getEvents().get()))
                .orElse(null);
    }

    public void writeCircuit(Block block, long id) {
        writeCircuitDirect(block, id);
        BlockData b = block.getBlockData();
        writeCircuitDirect(block.getRelative(BlockFace.DOWN), id);

        if (b instanceof Powerable) {
            if (b instanceof Repeater r) {
                writeCircuitDirect(block.getRelative(r.getFacing()), id);
            } else {
                writeCircuitDirect(block.getRelative(BlockFace.DOWN), id);
            }
        } else {
            writeCircuitDirect(block.getRelative(BlockFace.DOWN), id);
            writeCircuitDirect(block.getRelative(BlockFace.UP), id);
        }
    }

    private void writeCircuitDirect(Block block, long id) {
        blocks.put(new BlockPosition(block), id);
        getOrCreateCircuit(id).write(block);
    }

    public long getBiggestCircuit(Set<Long> s) {
        if (s.isEmpty()) {
            throw new RuntimeException();
        }

        int size = -1;
        long winner = -1;
        int z;

        for (Long i : s) {
            Circuit c = getCircuit(i);
            if (c != null) {
                z = c.countBlocks();
                if (z > size) {
                    size = z;
                    winner = i;
                }
            }
        }

        return winner;
    }

    public long getNeighborCircuit(Block block) {
        Block neighbor;
        Long id = getCircuitId(block);
        Set<Long> conflict = new HashSet<>();

        if (id != null) {
            conflict.add(id);
        }

        for (Direction i : Direction.udnews()) {
            neighbor = block.getRelative(i.getFace());
            id = getCircuitId(neighbor);

            if (id != null) {
                conflict.add(id);
            }
        }

        if (conflict.isEmpty()) {
            id = RNG.r.lmax();
            conflict.add(id);
            writeCircuit(block, id);
        }

        if (conflict.size() > 1) {
            id = getBiggestCircuit(conflict);
            writeCircuit(block, id);
        } else {
            id = conflict.stream().findFirst().get();
            writeCircuit(block, id);
        }

        return id;
    }

    public Circuit getCircuitAt(BlockPosition p) {
        Long id = getCircuitId(p);

        if (id != null) {
            return getCircuit(id);
        }

        return null;
    }

    public Long getCircuitId(BlockPosition p) {
        return blocks.get(p);
    }

    public Long getCircuitId(Block p) {
        return getCircuitId(new BlockPosition(p));
    }

    public Circuit createCircuit() {
        return createCircuit(RNG.r.lmax());
    }

    public Circuit createCircuit(long id) {
        Circuit c = new Circuit(id);
        circuits.put(id, c);
        return c;
    }

    public Circuit getCircuit(long id) {
        return circuits.get(id);
    }

    public Circuit getOrCreateCircuit(long id) {
        Circuit c = getCircuit(id);

        if (c == null) {
            c = createCircuit(id);
        }

        return c;
    }

    public int countCircuits() {
        return circuits.size();
    }

    public int countBlocks() {
        return blocks.size();
    }

    public void remove(BlockPosition block) {
        Long id = blocks.remove(block);

        if (id != null) {
            Circuit c = getCircuit(id);

            if (c != null) {
                c.remove(block);
            }
        }
    }

    public void tick() {
        circuits.entrySet().removeIf(i -> {
            i.getValue().tick();
            return i.getValue().countBlocks() == 0;
        });

        blocks.entrySet().removeIf(i -> {
            if (M.r(0.05)) {
                Circuit c = circuits.get(i.getValue());

                if (c != null) {
                    c.remove(i.getKey());
                }

                return true;
            }

            return false;
        });
    }
}
