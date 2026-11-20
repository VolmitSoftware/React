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

import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.HostCommand;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

@Director(
    name = "debug",
    origin = DirectorOrigin.BOTH,
    description = "React debugging commands",
    descriptionKey = "command.description.debug"
)
public class CommandDebug implements DirectorExecutor {
  @Director(name = "dump", sync = true, description = "Create and optionally upload a diagnostic report", descriptionKey = "command.description.debugdump")
  @HostCommand("debugDump")
  public void dump(
      @Param(name = "upload", defaultValue = "true", description = "Upload the report to mclo.gs", descriptionKey = "command.parameter.debugdump_upload") boolean upload,
      @Param(name = "sender", contextual = true) ReactCommandSender sender
  ) {
    host().debugDump(new VolmitSender(sender), upload);
  }

  @Director(
      name = "entity-data",
      aliases = {"ed"},
      description = "Show data for the entity being looked at",
      descriptionKey = "command.description.debug.entity",
      origin = DirectorOrigin.PLAYER,
      sync = true
  )
  @HostCommand("debugEntityData")
  public void entityData() {
    host().debugEntityData(sender());
  }
}
