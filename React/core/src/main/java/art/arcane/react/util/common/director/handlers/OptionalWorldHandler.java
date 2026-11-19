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
import art.arcane.react.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

/**
 * A world key, or {@code ALL} for every world. Bare world names and key paths resolve to the full key.
 */
public class OptionalWorldHandler implements DirectorParameterHandler<String> {
  private final WorldHandler worlds = new WorldHandler();

  @Override
  public KList<String> getPossibilities() {
    KList<String> options = new KList<>();
    options.add("ALL");
    for (CommandTargets.World world : WorldHandler.worldOptions()) {
      options.add(world.key());
    }
    return options;
  }

  @Override
  public String toString(String world) {
    return world;
  }

  @Override
  public String parse(String in, boolean force) throws DirectorParsingException {
    String value = in == null ? "" : in.trim();
    if ("ALL".equalsIgnoreCase(value)) {
      return "ALL";
    }

    return worlds.parse(value, force).key();
  }

  @Override
  public boolean supports(Class<?> type) {
    return false;
  }

  @Override
  public String getRandomDefault() {
    return "ALL";
  }
}
