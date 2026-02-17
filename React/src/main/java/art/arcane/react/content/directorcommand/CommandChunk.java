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
import art.arcane.react.model.SampledChunk;
import art.arcane.react.util.decree.DecreeExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.react.util.format.C;
import art.arcane.react.util.scheduling.J;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

@Director(
        name = "chunk",
        aliases = {"c"},
        origin = DirectorOrigin.BOTH,
        description = "This is the root chunk command, it contains all current chunk commands"
)
public class CommandChunk implements DecreeExecutor {
    @Director(
            name = "sample",
            description = "Get the current player-chunk sampled data",
            origin = DirectorOrigin.PLAYER
    )
    public void sample() {
        SampledChunk c = React.controller(ObserverController.class).getSampled().getChunk(player().getLocation().getChunk());

        if (c != null) {
            for (String i : c.getValues().keySet()) {
                Sampler s = React.sampler(i);
                sender().sendMessage(s.getName() + ": " + s.format(c.getValues().get(i).get()));
            }
        } else {
            sender().sendMessage(C.RED + "This chunk is not sampled yet. Check back in a second!");
        }
    }

    @Director(
            name = "worst",
            aliases = {"w"},
            description = "Get the worst chunk on the server/world",
            origin = DirectorOrigin.PLAYER
    )
    public void worst() {
        SampledChunk c = React.instance.controller(ObserverController.class).absoluteWorst();

        if (c != null) {
            Block b = c.getChunk().getBlock(8, 0, 8);
            Player p = player();
            J.runEntity(p, () -> p.teleport(c.getChunk().getWorld().getHighestBlockAt(b.getX(), b.getZ()).getLocation()));

            for (String i : c.getValues().keySet()) {
                Sampler s = React.sampler(i);
                sender().sendMessage(s.getName() + ": " + s.format(c.getValues().get(i).get()));
            }
        } else {
            sender().sendMessage(C.RED + "No chunks are sampled yet. Check back in a second!");
        }
    }
}
