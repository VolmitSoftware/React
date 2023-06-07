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
        description = "The chunk command"
)
public class CommandChunk implements DecreeExecutor {
    @Decree(
            name = "sample",
            description = "Get the current chunk samples",
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
            description = "Get the worst chunk",
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
