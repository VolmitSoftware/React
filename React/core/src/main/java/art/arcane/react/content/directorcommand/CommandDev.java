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
import art.arcane.volmlib.util.director.annotations.Param;

@Director(
    name = "dev",
    aliases = {"developer", "d"},
    origin = DirectorOrigin.BOTH,
    description = "Developer-only React validation and diagnostics",
    descriptionKey = "command.description.dev"
)
public class CommandDev implements DirectorExecutor {
  @Director(
      name = "test-all",
      aliases = {"ta"},
      origin = DirectorOrigin.PLAYER,
      sync = true,
      description = "Audit React state, then queue the direct action suite one step at a time in your current world",
      descriptionKey = "command.description.dev.test_all"
  )
  @HostCommand("devTestAll")
  public void testAll(
      @Param(
          name = "radius",
          description = "Chunk radius used for local purge entity and chunk test steps",
          descriptionKey = "command.parameter.dev.radius",
          defaultValue = "2",
          aliases = {"r"}
      )
      int radius
  ) {
    host().devTestAll(sender(), radius);
  }

  @Director(
      name = "verify",
      aliases = {"v", "selftest"},
      origin = DirectorOrigin.BOTH,
      sync = true,
      description = "Verify bridge, sampler, and lazy-gravity behavior and report PASS or FAIL",
      descriptionKey = "command.description.dev.verify"
  )
  @HostCommand("devVerify")
  public void verify() {
    host().devVerify(sender());
  }
}
