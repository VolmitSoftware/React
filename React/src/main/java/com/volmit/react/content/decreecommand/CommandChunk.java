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

package com.volmit.react.content.decreecommand;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.core.controller.ObserverController;
import com.volmit.react.model.SampledChunk;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.decree.DecreeOrigin;
import com.volmit.react.util.decree.annotations.Decree;
import com.volmit.react.util.format.C;
import com.volmit.react.util.scheduling.J;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

@Decree(
        name = "chunk",
        aliases = {"c"},
        origin = DecreeOrigin.BOTH,
        description = "This is the root chunk command, it contains all current chunk commands"
)
public class CommandChunk implements DecreeExecutor {
    @Decree(
            name = "sample",
            description = "Get the current player-chunk sampled data",
            origin = DecreeOrigin.PLAYER
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

    @Decree(
            name = "worst",
            aliases = {"w"},
            description = "Get the worst chunk on the server/world",
            origin = DecreeOrigin.PLAYER
    )
    public void worst() {
        SampledChunk c = React.instance.controller(ObserverController.class).absoluteWorst();

        if (c != null) {
            Block b = c.getChunk().getBlock(8, 0, 8);
            Player p = player();
            J.s(() -> p.teleport(c.getChunk().getWorld().getHighestBlockAt(b.getX(), b.getY()).getLocation()));

            for (String i : c.getValues().keySet()) {
                Sampler s = React.sampler(i);
                sender().sendMessage(s.getName() + ": " + s.format(c.getValues().get(i).get()));
            }
        } else {
            sender().sendMessage(C.RED + "No chunks are sampled yet. Check back in a second!");
        }
    }
}
