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
import lombok.*;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AreaActionParams {
  protected String world;
  @Singular
  protected List<Chunk> chunks;
  @Builder.Default
  protected boolean allChunks = true;

  private void addChunksFor(String worldKey) {
    World w = WorldIdentity.resolve(worldKey).orElse(null);

    if (w == null) {
      return;
    }

    if (chunks == null) {
      chunks = new ArrayList<>();
    }

    chunks = new ArrayList<>(chunks);
    chunks.addAll(Arrays.asList(w.getLoadedChunks()));
  }

  public Chunk popChunk() {
    if (chunks == null || chunks.isEmpty()) {
      if (allChunks) {
        if (world != null) {
          addChunksFor(world);
        } else {
          for (World i : Bukkit.getWorlds()) {
            addChunksFor(WorldIdentity.serialize(i));
          }
        }

        allChunks = false;
      }

      if (chunks == null || chunks.isEmpty()) {
        return null;
      }
    }

    return chunks.remove(0);
  }
}
