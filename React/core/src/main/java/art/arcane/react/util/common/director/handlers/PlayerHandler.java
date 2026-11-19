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

package art.arcane.react.util.director.handlers;

import art.arcane.react.platform.CommandTargets;
import art.arcane.react.util.director.DirectorHosts;
import art.arcane.react.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class PlayerHandler implements DirectorParameterHandler<CommandTargets.Player> {
  private static final String PLATFORM_PLAYER = "org.bukkit.entity.Player";

  @Override
  public KList<CommandTargets.Player> getPossibilities() {
    return new KList<>(DirectorHosts.command().players());
  }

  @Override
  public String toString(CommandTargets.Player player) {
    return player.name();
  }

  @Override
  public CommandTargets.Player parse(String in, boolean force) throws DirectorParsingException {
    KList<CommandTargets.Player> options = getPossibilities(in);

    if (options.isEmpty()) {
      throw new DirectorParsingException("Unable to find Player \"" + in + "\"");
    }

    CommandTargets.Player exact = options.stream()
        .filter(player -> toString(player).equalsIgnoreCase(in))
        .findFirst()
        .orElse(null);

    if (exact != null) {
      return exact;
    }

    throw new DirectorParsingException("Unable to filter which Player \"" + in + "\"");
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(CommandTargets.Player.class) || type.getName().equals(PLATFORM_PLAYER);
  }

  @Override
  public String getRandomDefault() {
    return "playername";
  }
}
