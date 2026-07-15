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

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public class CustomMobChecker {
  private static final long PROBE_INTERVAL_MS = 30_000L;
  private static volatile Object mobManager;
  private static volatile Method getActiveMob;
  private static volatile long nextProbeAt;

  public static boolean isCustom(Entity entity) {
    Object manager = mobManager;
    Method method = getActiveMob;
    if (manager == null || method == null) {
      if (!resolve()) {
        return false;
      }

      manager = mobManager;
      method = getActiveMob;
      if (manager == null || method == null) {
        return false;
      }
    }

    try {
      Object result = method.invoke(manager, entity.getUniqueId());
      return result instanceof Optional<?> active && active.isPresent();
    } catch (Throwable ignored) {
      mobManager = null;
      getActiveMob = null;
      nextProbeAt = System.currentTimeMillis() + PROBE_INTERVAL_MS;
      return false;
    }
  }

  private static synchronized boolean resolve() {
    if (mobManager != null && getActiveMob != null) {
      return true;
    }

    long now = System.currentTimeMillis();
    if (now < nextProbeAt) {
      return false;
    }

    nextProbeAt = now + PROBE_INTERVAL_MS;
    try {
      if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
        return false;
      }

      Class<?> mythic = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
      Object instance = mythic.getMethod("inst").invoke(null);
      Object manager = instance.getClass().getMethod("getMobManager").invoke(instance);
      Method method = manager.getClass().getMethod("getActiveMob", UUID.class);
      method.setAccessible(true);
      mobManager = manager;
      getActiveMob = method;
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }
}
