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

import art.arcane.react.api.event.layer.ServerTickEvent;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayDeque;
import java.util.Collection;

public class SamplerTickTime extends ReactCachedSampler implements Listener {
  public static final String ID = "tick-time";
  private static final int GAP_WINDOW_TICKS = 100;
  // Server#getAverageTickTime is paper-only; latch after first NoSuchMethodError and
  // approximate from inter-tick gaps instead (floors at ~50ms, still signals lag).
  private transient volatile boolean averageTickTimeUnsupported;
  private transient long lastTickMS;
  private transient final ArrayDeque<Double> tickGaps = new ArrayDeque<>();

  public SamplerTickTime() {
    super(ID, 50);
  }

  @Override
  public Material getIcon() {
    return Material.NAUTILUS_SHELL;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ServerTickEvent event) {
    if (!averageTickTimeUnsupported) {
      return;
    }

    recordTick(System.currentTimeMillis());
  }

  void recordTick(long nowMS) {
    if (lastTickMS > 0) {
      synchronized (tickGaps) {
        tickGaps.addLast((double) Math.max(0L, nowMS - lastTickMS));
        while (tickGaps.size() > GAP_WINDOW_TICKS) {
          tickGaps.removeFirst();
        }
      }
    }

    lastTickMS = nowMS;
  }

  @Override
  public double onSample() {
    if (averageTickTimeUnsupported) {
      return measuredTickTime();
    }

    return sampleOnMainThread(this::readAverageTickTime);
  }

  private double measuredTickTime() {
    synchronized (tickGaps) {
      return averageTickMS(tickGaps);
    }
  }

  private Double readAverageTickTime() {
    Server server = Bukkit.getServer();
    if (server == null) {
      return 0D;
    }

    try {
      double tickTime = server.getAverageTickTime();
      return Double.isFinite(tickTime) ? Math.max(0D, tickTime) : 0D;
    } catch (NoSuchMethodError e) {
      averageTickTimeUnsupported = true;
      return measuredTickTime();
    }
  }

  static double averageTickMS(Collection<Double> gaps) {
    if (gaps == null || gaps.isEmpty()) {
      return 0D;
    }

    double total = 0D;
    int counted = 0;
    for (Double gap : gaps) {
      if (gap != null && Double.isFinite(gap) && gap >= 0D) {
        total += gap;
        counted++;
      }
    }

    return counted == 0 ? 0D : total / counted;
  }

  @Override
  public String format(double t) {
    return formattedValue(t) + formattedSuffix(t);
  }

  @Override
  public Component format(Component value, Component suffix) {
    return Component.empty().append(value).append(suffix);
  }

  @Override
  public String formattedValue(double t) {
    return Form.durationSplit(t, 0)[0];
  }

  @Override
  public String formattedSuffix(double t) {
    return Form.durationSplit(t, 0)[1];
  }

}
