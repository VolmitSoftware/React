package com.volmit.react.content.command;

import com.volmit.react.React;
import com.volmit.react.model.VisualizerType;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.decree.DecreeOrigin;
import com.volmit.react.util.decree.annotations.Decree;
import com.volmit.react.util.decree.annotations.Param;
import com.volmit.react.util.format.C;
import com.volmit.react.util.format.Form;

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
    private CommandEnvironment environment;

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

    @Decree(
        name = "visualize",
        aliases = {"v", "see"},
        description = "Visualize the via glow blocks",
        origin = DecreeOrigin.PLAYER
    )
    public void visualize() {
        React.instance.getPlayerController().getPlayer(player()).getSettings().toggleVisualizing();
        sender().sendMessage(C.REACT + "Visualize "
            + (React.instance.getPlayerController().getPlayer(player()).getSettings().isVisualizing() ? "enabled" : "disabled"));
    }
}
