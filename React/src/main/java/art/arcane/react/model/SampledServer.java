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

import art.arcane.volmlib.util.bukkit.WorldIdentity;
import lombok.Data;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class SampledServer {
  private final Map<UUID, SampledWorld> worlds;
  private final Map<String, SampledWorld> worldsByKey;

  public SampledServer() {
    worlds = new ConcurrentHashMap<>();
    worldsByKey = new ConcurrentHashMap<>();
  }

  public SampledChunk getChunk(Chunk chunk) {
    return getWorld(chunk.getWorld()).getChunk(chunk);
  }

  public SampledChunk getChunk(World world, int chunkX, int chunkZ) {
    return getWorld(world).getChunk(chunkX, chunkZ);
  }

  public Optional<SampledChunk> optionalChunk(Chunk c) {
    return optionalWorld(c.getWorld()).flatMap((w) -> w.optionalChunk(c));
  }

  public Optional<SampledChunk> optionalChunk(World world, int chunkX, int chunkZ) {
    return optionalWorld(world).flatMap(sampledWorld -> sampledWorld.optionalChunk(chunkX, chunkZ));
  }

  public Optional<SampledChunk> optionalChunk(String worldKey, int chunkX, int chunkZ) {
    return optionalWorld(worldKey).flatMap(sampledWorld -> sampledWorld.optionalChunk(chunkX, chunkZ));
  }

  public void removeChunk(Chunk chunk) {
    SampledWorld sampledWorld = worlds.get(chunk.getWorld().getUID());
    if (sampledWorld != null) {
      sampledWorld.remove(chunk);
    }
  }

  public void removeWorld(World world) {
    SampledWorld removed = worlds.remove(world.getUID());
    if (removed != null) {
      worldsByKey.remove(removed.getWorldKey(), removed);
    }
  }

  public SampledWorld getWorld(World world) {
    UUID worldId = world.getUID();
    SampledWorld sampledWorld = worlds.get(worldId);
    return sampledWorld != null ? sampledWorld : worlds.computeIfAbsent(worldId, ignored -> index(world, worldId));
  }

  public Optional<SampledWorld> optionalWorld(World world) {
    return Optional.ofNullable(worlds.get(world.getUID()));
  }

  public Optional<SampledWorld> optionalWorld(String worldKey) {
    return Optional.ofNullable(worldsByKey.get(worldKey));
  }

  public void decay() {
    for (SampledWorld sampledWorld : worlds.values()) {
      sampledWorld.decay();
    }
  }

  private SampledWorld index(World world, UUID worldId) {
    SampledWorld sampledWorld = new SampledWorld(worldId, WorldIdentity.serialize(world));
    worldsByKey.put(sampledWorld.getWorldKey(), sampledWorld);
    return sampledWorld;
  }
}
