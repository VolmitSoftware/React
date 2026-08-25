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
import art.arcane.volmlib.util.math.RollingSequence;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.util.concurrent.atomic.LongAccumulator;

public class SamplerHopperEventSpan extends ReactCachedSampler implements Listener {
  public static final String ID = "hopper-event-span";
  private int tickAverage = 15;
  private transient final LongAccumulator firstEventNanos = new LongAccumulator(Math::min, Long.MAX_VALUE);
  private transient final LongAccumulator lastEventNanos = new LongAccumulator(Math::max, 0L);
  private transient RollingSequence average;

  public SamplerHopperEventSpan() {
    super(ID, 50);
  }

  @Override
  public void start() {
    super.start();
    average = new RollingSequence(Math.max(1, tickAverage));
    firstEventNanos.reset();
    lastEventNanos.reset();
  }

  @Override
  public Material getIcon() {
    return Material.HOPPER;
  }

  @EventHandler
  public void on(ServerTickEvent e) {
    RollingSequence currentAverage = average;
    long first = firstEventNanos.getThenReset();
    long last = lastEventNanos.getThenReset();
    if (currentAverage != null) {
      currentAverage.put(first == Long.MAX_VALUE || last <= first ? 0D : (last - first) / 1.0E6D);
    }
  }

  @EventHandler
  public void on(InventoryMoveItemEvent e) {
    if ((e.getSource().getHolder() instanceof Hopper) || (e.getDestination().getHolder() instanceof Hopper)) {
      recordEvent();
    }
  }

  @Override
  public double onSample() {
    RollingSequence currentAverage = average;
    return currentAverage == null ? 0D : currentAverage.getAverage();
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
    return Form.durationSplit(t, 2)[0];
  }

  @Override
  public String formattedSuffix(double t) {
    return Form.durationSplit(t, 2)[1] + " HOP EVT SPAN";
  }

  private void recordEvent() {
    long now = System.nanoTime();
    firstEventNanos.accumulate(now);
    lastEventNanos.accumulate(now);
  }
}
