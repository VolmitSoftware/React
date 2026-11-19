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

import art.arcane.react.util.director.context.PlayerContextHandler;
import art.arcane.react.util.director.context.WorldContextHandler;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.director.context.DirectorContextHandlerType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface DirectorContextHandler<T> extends DirectorContextHandlerType<T, VolmitSender> {
  Map<Class<?>, DirectorContextHandler<?>> contextHandlers = buildContextHandlers();

  static Map<Class<?>, DirectorContextHandler<?>> buildContextHandlers() {
    Map<Class<?>, DirectorContextHandler<?>> handlers = new LinkedHashMap<>();
    for (DirectorContextHandler<?> handler : List.<DirectorContextHandler<?>>of(new PlayerContextHandler(), new WorldContextHandler())) {
      handlers.put(handler.getType(), handler);
    }
    return Collections.unmodifiableMap(handlers);
  }
}
