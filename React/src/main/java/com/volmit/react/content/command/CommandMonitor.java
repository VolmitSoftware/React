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
