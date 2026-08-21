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

package art.arcane.react.util.project.world;

import org.bukkit.entity.Entity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Capability probe for the cube-mob (slime family) Bukkit type. Paper 26.2 introduced
 * org.bukkit.entity.AbstractCubeMob as the shared supertype of Slime and MagmaCube; on 26.1.x the
 * shared supertype carrying getSize/setSize is org.bukkit.entity.Slime (MagmaCube extends Slime
 * there). Resolved once via Class.forName probe, never by version string.
 */
public final class CubeMobs {
  private static final Class<?> CUBE_TYPE;
  private static final MethodHandle GET_SIZE;
  private static final MethodHandle SET_SIZE;

  static {
    Class<?> type = probe("org.bukkit.entity.AbstractCubeMob");
    if (type == null) {
      type = probe("org.bukkit.entity.Slime");
    }
    MethodHandle getSize = null;
    MethodHandle setSize = null;
    if (type != null) {
      try {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        getSize = lookup.findVirtual(type, "getSize", MethodType.methodType(int.class));
        setSize = lookup.findVirtual(type, "setSize", MethodType.methodType(void.class, int.class));
      } catch (ReflectiveOperationException broken) {
        type = null;
        getSize = null;
        setSize = null;
      }
    }
    CUBE_TYPE = type;
    GET_SIZE = getSize;
    SET_SIZE = setSize;
  }

  private CubeMobs() {
  }

  private static Class<?> probe(String name) {
    try {
      return Class.forName(name);
    } catch (Throwable absent) {
      return null;
    }
  }

  public static boolean isCubeMob(Entity entity) {
    return CUBE_TYPE != null && CUBE_TYPE.isInstance(entity);
  }

  public static int getSize(Entity entity) {
    try {
      return (int) GET_SIZE.invoke(entity);
    } catch (Throwable t) {
      throw new IllegalStateException("Cube mob getSize failed for " + entity.getType(), t);
    }
  }

  public static void setSize(Entity entity, int size) {
    try {
      SET_SIZE.invoke(entity, size);
    } catch (Throwable t) {
      throw new IllegalStateException("Cube mob setSize failed for " + entity.getType(), t);
    }
  }
}
