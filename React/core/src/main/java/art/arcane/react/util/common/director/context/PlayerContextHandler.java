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

package art.arcane.react.util.director.context;

import art.arcane.react.platform.CommandTargets;
import art.arcane.react.util.director.DirectorContextHandler;
import art.arcane.react.util.plugin.VolmitSender;

public final class PlayerContextHandler implements DirectorContextHandler<CommandTargets.Player> {
  @Override
  public Class<CommandTargets.Player> getType() {
    return CommandTargets.Player.class;
  }

  @Override
  public CommandTargets.Player handle(VolmitSender sender) {
    return sender.isPlayer() ? new CommandTargets.Player(sender.player()) : null;
  }
}
