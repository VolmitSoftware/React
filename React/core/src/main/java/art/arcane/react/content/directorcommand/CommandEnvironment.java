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

import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.HostCommand;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;

@Director(
    name = "environment",
    aliases = {"env"},
    origin = DirectorOrigin.BOTH,
    description = "Inspect system and server environment information",
    descriptionKey = "command.description.environment"
)
public class CommandEnvironment implements DirectorExecutor {
  @Director(
      name = "info",
      aliases = {"i"},
      description = "Print environment details",
      descriptionKey = "command.description.environment.info"
  )
  @HostCommand("environmentInfo")
  public void info() {
    host().environmentInfo(sender());
  }
}
