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

package art.arcane.react.model;

import art.arcane.react.util.cache.Cache;
import lombok.Data;
import org.bukkit.Chunk;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class SampledWorld {
  private final UUID worldId;
  private final String worldKey;
  private final Map<Long, SampledChunk> chunks;

  public SampledWorld(UUID worldId, String worldKey) {
    this.worldId = worldId;
    this.worldKey = worldKey;
    chunks = new ConcurrentHashMap<>();
  }

  public void remove(Chunk c) {
    remove(c.getX(), c.getZ());
  }

  public void remove(int x, int z) {
    chunks.remove(Cache.key(x, z));
  }

  public Optional<SampledChunk> optionalChunk(Chunk c) {
    return optionalChunk(c.getX(), c.getZ());
  }

  public Optional<SampledChunk> optionalChunk(int x, int z) {
    return Optional.ofNullable(chunks.get(Cache.key(x, z)));
  }

  public SampledChunk getChunk(Chunk c) {
    return getChunk(c.getX(), c.getZ());
  }

  public SampledChunk getChunk(int x, int z) {
    return chunks.computeIfAbsent(
        Cache.key(x, z),
        ignored -> new SampledChunk(worldId, worldKey, x, z)
    );
  }
}
