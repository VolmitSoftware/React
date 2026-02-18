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

package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.RollingSequence;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;

public class SamplerPingJitter extends ReactCachedSampler {
  public static final String ID = "ping-jitter";
  private transient RollingSequence avg;
  private transient Map<UUID, Integer> lastPing;
  private transient Method getPingMethod;
  private transient boolean resolvedGetPing = false;
  private int averagingSamples = 20;

  public SamplerPingJitter() {
    super(ID, 1000);
  }

  @Override
  public void start() {
    super.start();
    avg = new RollingSequence(averagingSamples);
    lastPing = new HashMap<>();
  }

  @Override
  public Material getIcon() {
    return Material.CLOCK;
  }

  @Override
  public double onSample() {
    return executeSync(() -> {
      int count = 0;
      double jitter = 0;
      Set<UUID> online = new HashSet<>();
      for (Player player : Bukkit.getOnlinePlayers()) {
        Integer ping = readPing(player);
        if (ping == null) {
          continue;
        }

        UUID id = player.getUniqueId();
        online.add(id);
        Integer previous = lastPing.put(id, ping);
        if (previous != null) {
          jitter += Math.abs(ping - previous);
          count++;
        }
      }

      lastPing.keySet().retainAll(online);
      avg.put(count <= 0 ? 0 : jitter / count);
      return avg.getAverage();
    });
  }

  private Integer readPing(Player player) {
    if (!resolvedGetPing) {
      resolvedGetPing = true;
      try {
        getPingMethod = Player.class.getMethod("getPing");
      } catch (Throwable ignored) {
        getPingMethod = null;
      }
    }

    if (getPingMethod != null) {
      try {
        return (Integer) getPingMethod.invoke(player);
      } catch (Throwable ignored) {
      }
    }

    try {
      Object spigot = player.spigot();
      Method method = spigot.getClass().getMethod("getPing");
      Object ping = method.invoke(spigot);
      if (ping instanceof Number n) {
        return n.intValue();
      }
    } catch (Throwable ignored) {
    }

    return null;
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 2);
  }

  @Override
  public String formattedSuffix(double t) {
    return "ms JITTER";
  }
}
