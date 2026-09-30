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
import org.bukkit.Material;

public class SamplerTickSpikeRate extends ReactCachedSampler {
  public static final String ID = "tick-spike-rate";
  private final transient TickClock clock;
  private int spikeThresholdMS = 50;
  private int windowMS = 60000;

  public SamplerTickSpikeRate() {
    this(TickClock.get());
  }

  SamplerTickSpikeRate(TickClock clock) {
    super(ID, 250);
    this.clock = clock;
  }

  @Override
  public void start() {
    clock.acquire(this);
    super.start();
  }

  @Override
  public void stop() {
    clock.release(this);
    super.stop();
  }

  @Override
  public Material getIcon() {
    return Material.REPEATER;
  }

  @Override
  public boolean isSampleAvailable() {
    return clock.snapshot().hasHistory();
  }

  @Override
  public double onSample() {
    return clock.snapshot().spikesPerMinute(System.nanoTime(), Math.max(1, spikeThresholdMS), Math.max(1000, windowMS));
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 1);
  }

  @Override
  public String formattedSuffix(double t) {
    return "SPIKE/m";
  }
}
