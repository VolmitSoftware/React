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

package art.arcane.react.content.feature.perworld;

import org.bukkit.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReactScopedPressure {
  private static final Map<UUID, PerWorldPressure> STATE = new ConcurrentHashMap<>();
  private static volatile boolean enabled = false;

  private ReactScopedPressure() {
  }

  public static PerWorldPressure localPressure(World world) {
    if (!enabled || world == null) {
      return PerWorldPressure.NORMAL;
    }

    PerWorldPressure current = STATE.get(world.getUID());
    return current == null ? PerWorldPressure.NORMAL : current;
  }

  public static void publish(World world, PerWorldPressure pressure) {
    if (world == null || pressure == null) {
      return;
    }
    STATE.put(world.getUID(), pressure);
  }

  public static void clear(World world) {
    if (world == null) {
      return;
    }
    STATE.remove(world.getUID());
  }

  public static void clearAll() {
    STATE.clear();
  }

  public static void setEnabled(boolean enabledNow) {
    enabled = enabledNow;
    if (!enabledNow) {
      STATE.clear();
    }
  }

  public static boolean isEnabled() {
    return enabled;
  }
}
