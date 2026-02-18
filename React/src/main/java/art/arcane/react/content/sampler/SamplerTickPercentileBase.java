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
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

abstract class SamplerTickPercentileBase extends ReactCachedSampler implements Listener {
  private final ArrayDeque<Double> tickDurations = new ArrayDeque<>();
  private final double percentile;
  private final String suffix;
  private int historyTicks = 1200;
  private transient long lastTickMS = 0;

  protected SamplerTickPercentileBase(String id, double percentile, String suffix) {
    super(id, 250);
    this.percentile = percentile;
    this.suffix = suffix;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ServerTickEvent event) {
    long now = System.currentTimeMillis();
    if (lastTickMS > 0) {
      double tickMS = Math.max(0D, now - lastTickMS);
      synchronized (tickDurations) {
        tickDurations.addLast(tickMS);
        while (tickDurations.size() > historyTicks) {
          tickDurations.removeFirst();
        }
      }

      onTickDuration(tickMS, now);
    }

    lastTickMS = now;
  }

  protected void onTickDuration(double tickMS, long atMS) {
  }

  @Override
  public Material getIcon() {
    return Material.CLOCK;
  }

  @Override
  public double onSample() {
    List<Double> snapshot;
    synchronized (tickDurations) {
      snapshot = new ArrayList<>(tickDurations);
    }

    return SamplerMath.percentile(snapshot, percentile);
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 2);
  }

  @Override
  public String formattedSuffix(double t) {
    return suffix;
  }
}
