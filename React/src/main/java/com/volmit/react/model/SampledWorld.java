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

import com.volmit.react.util.cache.Cache;
import lombok.Data;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Data
public class SampledWorld {
    private final World world;
    private final Map<Long, SampledChunk> chunks;

    public SampledWorld(World world) {
        this.world = world;
        chunks = new HashMap<>();
    }

    public void remove(Chunk c) {
        remove(c.getX(), c.getZ());
    }

    public void remove(int x, int z) {
        chunks.remove(Cache.key(x, z));
    }

    public Optional<SampledChunk> optionalChunk(Chunk c) {
        long k = Cache.key(c.getX(), c.getZ());
        return chunks.containsKey(k) ? Optional.of(chunks.get(k)) : Optional.empty();
    }

    public SampledChunk getChunk(Chunk c) {
        return getChunk(c.getX(), c.getZ());
    }

    public SampledChunk getChunk(int x, int z) {
        return chunks.computeIfAbsent(Cache.key(x, z), (k) -> new SampledChunk(world.getChunkAt(x, z), this));
    }
}


