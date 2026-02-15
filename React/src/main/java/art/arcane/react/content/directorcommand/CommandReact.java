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

import art.arcane.curse.Curse;
import art.arcane.react.React;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.PlayerController;
import art.arcane.react.core.gui.ReactMapGUI;
import art.arcane.react.util.decree.DecreeExecutor;
import art.arcane.react.util.world.WorldDistanceSupport;
import art.arcane.volmlib.util.decree.DecreeOrigin;
import art.arcane.volmlib.util.decree.annotations.Decree;
import art.arcane.volmlib.util.decree.annotations.Param;
import art.arcane.react.util.decree.handlers.ReactRendererHandler;
import art.arcane.react.util.format.C;

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
    private CommandBenchmark benchmark;
    private CommandDebug debug;
    private CommandIntegration integration;


    @Decree(
            name = "monitor",
            aliases = {"m", "mon"},
            description = "Monitor the server via action bar",
            origin = DecreeOrigin.PLAYER
    )
    public void monitor() {
        React.controller(PlayerController.class).getPlayer(player()).toggleActionBar();
        sender().sendMessage(C.REACT + "Action bar monitor " + (React.controller(PlayerController.class).getPlayer(player()).isActionBarMonitoring() ? "enabled" : "disabled"));
    }


    @Decree(
            name = "set-player-view-distance",
            aliases = {"vd", "view-distance"},
            description = "Visualize the via glow blocks",
            origin = DecreeOrigin.PLAYER
    )
    public void vd(
            @Param(name = "distance")
            int d) {
        if (!WorldDistanceSupport.supportsWorldDistanceSetters()) {
            sender().sendMessage(C.REACT + "This command requires Paper/Purpur. Spigot does not expose world view/simulation distance setters.");
            return;
        }

        if (d > 32)
            d = 32;

        Curse.on(player().getWorld()).method("setViewDistance", int.class).invoke(d);
        Curse.on(player().getWorld()).method("setSimulationDistance", int.class).invoke(d);
    }

    @Decree(
            name = "map",
            description = "Visualize the via glow blocks",
            origin = DecreeOrigin.PLAYER
    )
    public void map(
            @Param(
                    name = "renderer",
                    defaultValue = "unknown",
                    customHandler = ReactRendererHandler.class
            ) ReactRenderer renderer
    ) {
        if (renderer == null || "unknown".equalsIgnoreCase(renderer.getId())) {
            ReactMapGUI.open(player());
            return;
        }

        React.controller(MapController.class).openRenderer(player(), renderer);
    }

    @Decree(
            name = "reload",
            aliases = {"rl"},
            description = "Reload React")
    public void reload() {
        sender().sendMessage("Reloading React, YOu may see errors in the console, this is OKAY! Its the threads we are sampling being killed.");
        React.instance.reload();
        sender().sendMessage("React v" + React.instance.getDescription().getVersion() + " Reloaded!");
    }

    @Decree(
            name = "version",
            description = "Get React version")
    public void version() {
        sender().sendMessage("React " + React.instance.getDescription().getVersion());
    }
}
