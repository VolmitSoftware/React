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

import art.arcane.react.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import art.arcane.volmlib.util.director.handlers.base.OptionalWorldHandlerBase;

public class OptionalWorldHandler extends OptionalWorldHandlerBase implements DirectorParameterHandler<String> {
  private final WorldHandler worlds = new WorldHandler();

  @Override
  protected String excludedPrefix() {
    return "iris/";
  }

  @Override
  public String parse(String in, boolean force) throws DirectorParsingException {
    String value = in == null ? "" : in.trim();
    if ("ALL".equalsIgnoreCase(value)) {
      return "ALL";
    }

    return WorldIdentity.serialize(worlds.parse(value, force));
  }
}
