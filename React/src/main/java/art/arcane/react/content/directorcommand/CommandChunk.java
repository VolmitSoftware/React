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

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

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
    SampledChunk c = React.controller(ObserverController.class).getSampled().getChunk(player().getLocation().getChunk());

    if (c != null) {
      for (String i : c.getValues().keySet()) {
        Sampler s = React.sampler(i);
        ReactLanguage.send(
            sender(),
            CommandMessages.CHUNK_SAMPLE,
            MessageArgument.untrusted("sampler", s.getName()),
            MessageArgument.untrusted("value", s.format(c.getValues().get(i).get()))
        );
      }
    } else {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.CHUNK_NOT_SAMPLED);
    }
  }

  @Director(
      name = "worst",
      aliases = {"w"},
      description = "Teleport to and inspect the worst sampled chunk",
      descriptionKey = "command.description.chunk.worst",
      origin = DirectorOrigin.PLAYER
  )
  public void worst() {
    SampledChunk c = React.instance.controller(ObserverController.class).absoluteWorst();

    if (c != null) {
      World world = Bukkit.getWorld(c.getWorldId());
      if (world == null) {
        ReactLanguage.sendPrefixed(sender(), CommandMessages.NO_CHUNKS_SAMPLED);
        return;
      }

      int chunkX = c.getChunkX();
      int chunkZ = c.getChunkZ();
      int blockX = (chunkX << 4) + 8;
      int blockZ = (chunkZ << 4) + 8;
      Player p = player();
      if (!J.runChunk(world, chunkX, chunkZ, () -> {
        Location destination = world.getHighestBlockAt(blockX, blockZ).getLocation().add(0.5D, 1D, 0.5D);
        p.teleportAsync(destination).whenComplete((teleported, failure) -> {
          if (failure != null) {
            React.reportError(new IllegalStateException("Failed to teleport to worst sampled chunk", failure));
          }
        });
      })) {
        React.reportError(new IllegalStateException("Failed to schedule worst sampled chunk destination lookup"));
      }

      for (String i : c.getValues().keySet()) {
        Sampler s = React.sampler(i);
        ReactLanguage.send(
            sender(),
            CommandMessages.CHUNK_SAMPLE,
            MessageArgument.untrusted("sampler", s.getName()),
            MessageArgument.untrusted("value", s.format(c.getValues().get(i).get()))
        );
      }
    } else {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.NO_CHUNKS_SAMPLED);
    }
  }
}
