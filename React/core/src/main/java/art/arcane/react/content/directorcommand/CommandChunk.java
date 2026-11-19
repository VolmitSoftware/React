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

package art.arcane.react.content.directorcommand;

import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;

@Director(
    name = "chunk",
    aliases = {"c"},
    origin = DirectorOrigin.BOTH,
    description = "Inspect sampled chunk data",
    descriptionKey = "command.description.chunk"
)
public class CommandChunk implements DirectorExecutor {
  @Director(
      name = "sample",
      description = "Show sampled data for the current chunk",
      descriptionKey = "command.description.chunk.sample",
      origin = DirectorOrigin.PLAYER
  )
  public void sample() {
    host().chunkSample(sender());
  }

  @Director(
      name = "worst",
      aliases = {"w"},
      description = "Teleport to and inspect the worst sampled chunk",
      descriptionKey = "command.description.chunk.worst",
      origin = DirectorOrigin.PLAYER
  )
  public void worst() {
    host().chunkWorst(sender());
  }
}
