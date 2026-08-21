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
import art.arcane.react.core.controller.PlayerController;
import art.arcane.react.core.gui.MonitorConfigGUI;
import art.arcane.react.core.gui.ReactConfigGUI;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import org.bukkit.entity.Player;

@Director(
    name = "config",
    aliases = {"cfg", "c"},
    origin = DirectorOrigin.BOTH,
    description = "Configure React settings",
    descriptionKey = "command.description.config"
)
public class CommandConfig implements DirectorExecutor {
  @Director(
      name = "gui",
      aliases = {"menu", "editor"},
      description = "Open the React TOML config editor",
      descriptionKey = "command.description.config.gui",
      origin = DirectorOrigin.PLAYER
  )
  public void gui() {
    Player player = player();
    if (!ReactConfigGUI.canConfigure(player)) {
      ReactLanguage.sendPrefixed(sender(), CommandMessages.CONFIG_PERMISSION);
      return;
    }

    ReactConfigGUI.open(player);
  }

  @Director(
      name = "monitor",
      aliases = {"m", "mon"},
      description = "Configure the action bar monitor",
      descriptionKey = "command.description.config.monitor",
      origin = DirectorOrigin.PLAYER
  )
  public void monitor() {
    Player player = player();
    MonitorConfigGUI.editMonitorConfiguration(player, React.controller(PlayerController.class).getPlayer(player).getSettings().getMonitorConfiguration(),
        (c) -> React.controller(PlayerController.class).getPlayer(player).saveSettings());
  }
}
