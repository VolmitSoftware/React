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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReactScopedPressure {
  private static final Map<UUID, PerWorldPressure> STATE = new ConcurrentHashMap<>();
  private static volatile boolean enabled = false;

  private ReactScopedPressure() {
  }

  public static PerWorldPressure localPressure(UUID worldId) {
    if (!enabled || worldId == null) {
      return PerWorldPressure.NORMAL;
    }

    PerWorldPressure current = STATE.get(worldId);
    return current == null ? PerWorldPressure.NORMAL : current;
  }

  public static void publish(UUID worldId, PerWorldPressure pressure) {
    if (worldId == null || pressure == null) {
      return;
    }
    STATE.put(worldId, pressure);
  }

  public static void clear(UUID worldId) {
    if (worldId == null) {
      return;
    }
    STATE.remove(worldId);
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

  public static boolean isAnyPressure() {
    if (!enabled) {
      return false;
    }

    for (PerWorldPressure pressure : STATE.values()) {
      if (pressure.isPressure()) {
        return true;
      }
    }
    return false;
  }
}
