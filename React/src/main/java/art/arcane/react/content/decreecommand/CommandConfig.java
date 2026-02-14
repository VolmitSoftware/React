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

package art.arcane.react.content.decreecommand;

import art.arcane.react.React;
import art.arcane.react.core.controller.PlayerController;
import art.arcane.react.core.gui.MonitorConfigGUI;
import art.arcane.react.util.decree.DecreeExecutor;
import art.arcane.volmlib.util.decree.DecreeOrigin;
import art.arcane.volmlib.util.decree.annotations.Decree;
import org.bukkit.entity.Player;

@Decree(
        name = "config",
        aliases = {"cfg", "c"},
        origin = DecreeOrigin.BOTH,
        description = "This is the place to configure Itemized Settings."
)
public class CommandConfig implements DecreeExecutor {
    @Decree(
            name = "monitor",
            aliases = {"m", "mon"},
            description = "Configure the monitor",
            origin = DecreeOrigin.PLAYER
    )
    public void monitor() {
        Player player = player();
        MonitorConfigGUI.editMonitorConfiguration(player, React.controller(PlayerController.class).getPlayer(player).getSettings().getMonitorConfiguration(),
                (c) -> React.controller(PlayerController.class).getPlayer(player).saveSettings());
    }
}
