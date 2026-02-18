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

package art.arcane.react.util.world;

import org.bukkit.World;

import java.lang.reflect.Method;

public final class WorldDistanceSupport {
  private WorldDistanceSupport() {
  }

  public static boolean supportsWorldDistanceSetters() {
    return hasMethod(World.class, "setViewDistance", int.class)
        && hasMethod(World.class, "setSimulationDistance", int.class);
  }

  private static boolean hasMethod(Class<?> type, String name, Class<?>... params) {
    try {
      Method ignored = type.getMethod(name, params);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }
}
