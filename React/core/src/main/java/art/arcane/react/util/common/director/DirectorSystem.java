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

package art.arcane.react.util.director;

import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.util.director.handlers.BooleanHandler;
import art.arcane.react.util.director.handlers.ByteHandler;
import art.arcane.react.util.director.handlers.DoubleHandler;
import art.arcane.react.util.director.handlers.FloatHandler;
import art.arcane.react.util.director.handlers.IntegerHandler;
import art.arcane.react.util.director.handlers.LongHandler;
import art.arcane.react.util.director.handlers.OptionalWorldHandler;
import art.arcane.react.util.director.handlers.PlayerHandler;
import art.arcane.react.util.director.handlers.ReactRendererHandler;
import art.arcane.react.util.director.handlers.ShortHandler;
import art.arcane.react.util.director.handlers.StringHandler;
import art.arcane.react.util.director.handlers.WorldHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorSystemSupport;

import java.util.List;

public final class DirectorSystem {
  public static final KList<DirectorParameterHandler<?>> handlers = new KList<>(List.<DirectorParameterHandler<?>>of(
      new BooleanHandler(),
      new ByteHandler(),
      new DoubleHandler(),
      new FloatHandler(),
      new IntegerHandler(),
      new LongHandler(),
      new OptionalWorldHandler(),
      new PlayerHandler(),
      new ReactRendererHandler(),
      new ShortHandler(),
      new StringHandler(),
      new WorldHandler()
  ));

  private DirectorSystem() {
  }

  /**
   * Get the handler for the specified type
   *
   * @param type The type to handle
   * @return The corresponding {@link DirectorParameterHandler}, or null
   */
  public static DirectorParameterHandler<?> getHandler(Class<?> type) {
    DirectorParameterHandler<?> handler = DirectorSystemSupport.getHandler(handlers, type, (h, t) -> h.supports(t));
    if (handler != null) {
      return handler;
    }

    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.warn("Unhandled type in Director Parameter: " + type.getName() + ". This is bad!");
    }
    return null;
  }
}
