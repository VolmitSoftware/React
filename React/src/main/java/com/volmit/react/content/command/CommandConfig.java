package com.volmit.react.content.command;

import com.volmit.react.React;
import com.volmit.react.core.gui.MonitorConfigGUI;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.decree.DecreeOrigin;
import com.volmit.react.util.decree.annotations.Decree;
import com.volmit.react.util.format.C;

@Decree(
    name = "config",
    aliases = {"conf", "cfg", "c"},
    origin = DecreeOrigin.BOTH,
    description = "The configuration command"
)
public class CommandConfig implements DecreeExecutor {
    @Decree(
        name = "monitor",
        aliases = {"m", "mon"},
        description = "Configure the monitor",
        origin = DecreeOrigin.PLAYER
    )
    public void monitor() {
        MonitorConfigGUI.editMonitorConfiguration(player(), React.instance.getPlayerController().getPlayer(player()).getSettings().getMonitorConfiguration(),
            (c) -> React.instance.getPlayerController().getPlayer(player()).saveSettings());
    }
}
