package com.volmit.react.content.command;

import art.arcane.edict.Edict;
import art.arcane.edict.api.context.EdictContext;
import com.volmit.react.React;
import com.volmit.react.api.rendering.ReactRenderer;
import com.volmit.react.core.controller.MapController;
import com.volmit.react.core.controller.PlayerController;
import com.volmit.react.core.gui.MonitorConfigGUI;
import com.volmit.react.util.format.C;
import org.bukkit.entity.Player;

public class CommandMonitor {
    @Edict.PlayerOnly
    @Edict.Command("/react monitor")
    @Edict.Aliases("/react mon")
    public static void monitor(
            @Edict.Default("false")
            @Edict.Aliases({"config", "c", "edit", "e"})
            boolean configure
    ) {
        if (configure) {
            Player player = EdictContext.player();
            MonitorConfigGUI.editMonitorConfiguration(player, React.controller(PlayerController.class).getPlayer(player).getSettings().getMonitorConfiguration(),
                    (c) -> React.controller(PlayerController.class).getPlayer(player).saveSettings());
            return;
        }

        React.controller(PlayerController.class).getPlayer(EdictContext.player()).toggleActionBar();
        EdictContext.sender().sendMessage(C.REACT + "Action bar monitor " + (React.controller(PlayerController.class).getPlayer(EdictContext.player()).isActionBarMonitoring() ? "enabled" : "disabled"));
    }

    @Edict.PlayerOnly
    @Edict.Command("/react map")
    @Edict.Aliases({"/react renderer", "/react render"})
    public static void map(
            ReactRenderer renderer
    ) {
        React.controller(MapController.class).openRenderer(EdictContext.player(), renderer);
    }
}
