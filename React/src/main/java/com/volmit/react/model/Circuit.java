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
import com.volmit.react.util.math.M;
import lombok.Data;
import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class Circuit {
    private final long id;
    private final Set<BlockPosition> positions;
    private final AtomicInteger events;
    private final AtomicInteger eventBuffer;
    private final AtomicLong lastEvent;
    private final AtomicBoolean stop;

    public Circuit(long id) {
        this.id = id;
        this.positions = new HashSet<>();
        this.events = new AtomicInteger(0);
        this.eventBuffer = new AtomicInteger(0);
        this.lastEvent = new AtomicLong(M.ms());
        this.stop = new AtomicBoolean(false);
    }

    public int countBlocks() {
        return positions.size();
    }

    public void write(Block block) {
        positions.add(new BlockPosition(block));
        eventBuffer.incrementAndGet();
        lastEvent.set(M.ms());
    }

    public void remove(BlockPosition block) {
        positions.remove(block);
    }

    public void stop() {
        stop.set(true);
    }

    public void tick() {
        events.set(eventBuffer.getAndSet(0));
        if (M.ms() - lastEvent.get() > 10000) {
            positions.clear();
        }
    }
}
