package com.volmit.react.content.command;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.model.SampledChunk;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.decree.DecreeOrigin;
import com.volmit.react.util.decree.annotations.Decree;
import com.volmit.react.util.format.C;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

@Decree(
        name = "react",
        aliases = {"re"},
        origin = DecreeOrigin.BOTH,
        description = "The root react command"
)
public class CommandReact implements DecreeExecutor {
    private CommandConfig config;
    private CommandAction action;
    private CommandChunk chunk;

    @Decree(
        name = "monitor",
        aliases = {"m", "mon"},
        description = "Monitor the server via action bar",
        origin = DecreeOrigin.PLAYER
    )
    public void monitor() {
        React.instance.getPlayerController().getPlayer(player()).toggleActionBar();
        sender().sendMessage(C.REACT + "Action bar monitor " + (React.instance.getPlayerController().getPlayer(player()).isActionBarMonitoring() ? "enabled" : "disabled"));
    }
}
