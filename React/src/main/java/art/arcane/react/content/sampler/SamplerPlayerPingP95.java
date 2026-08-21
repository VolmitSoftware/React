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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class SamplerPlayerPingP95 extends ReactCachedSampler {
  public static final String ID = "player-ping-p95";
  private transient Method getPingMethod;
  private transient boolean resolvedGetPing = false;

  public SamplerPlayerPingP95() {
    super(ID, 1000);
  }

  @Override
  public Material getIcon() {
    return Material.CLOCK;
  }

  @Override
  public double onSample() {
    return sampleOnMainThread(() -> {
      List<Double> pings = new ArrayList<>();
      for (Player player : Bukkit.getOnlinePlayers()) {
        Integer ping = readPing(player);
        if (ping != null) {
          pings.add((double) Math.max(0, ping));
        }
      }

      return SamplerMath.percentile(pings, 0.95D);
    });
  }

  protected Integer readPing(Player player) {
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
    return Form.f(t, 1);
  }

  @Override
  public String formattedSuffix(double t) {
    return "ms P95PING";
  }
}
