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
import java.util.concurrent.ConcurrentHashMap;

@Data
public class SampledServer {
  private final Map<String, SampledWorld> worlds;

  public SampledServer() {
    worlds = new ConcurrentHashMap<>();
  }

  public SampledChunk getChunk(Chunk chunk) {
    return getWorld(chunk.getWorld()).getChunk(chunk);
  }

  public Optional<SampledChunk> optionalChunk(Chunk c) {
    return optionalWorld(c.getWorld()).flatMap((w) -> w.optionalChunk(c));
  }

  public boolean hasWorld(String world) {
    return worlds.containsKey(world);
  }

  public boolean hasWorld(World world) {
    return hasWorld(WorldIdentity.serialize(world));
  }

  public void removeChunk(Chunk chunk) {
    if (hasWorld(chunk.getWorld())) {
      getWorld(chunk.getWorld()).remove(chunk);
    }
  }

  public void removeWorld(World world) {
    removeWorld(WorldIdentity.serialize(world));
  }

  public void removeWorld(String name) {
    worlds.remove(name);
  }

  public SampledWorld getWorld(World world) {
    return getWorld(WorldIdentity.serialize(world));
  }

  public Optional<SampledWorld> optionalWorld(World world) {
    return Optional.ofNullable(worlds.get(WorldIdentity.serialize(world)));
  }

  public SampledWorld getWorld(String worldKey) {
    return worlds.computeIfAbsent(
        worldKey,
        key -> new SampledWorld(WorldIdentity.resolve(key).orElseThrow(() ->
            new IllegalStateException("World is not loaded: " + key)))
    );
  }
}
