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

public class SamplerTicksPerSecond extends ReactCachedSampler {
  public static final String ID = "ticks-per-second";
  private final transient TickClock clock;
  private int countUpTickTimeThresholdMS = 3000;

  public SamplerTicksPerSecond() {
    this(TickClock.get());
  }

  SamplerTicksPerSecond(TickClock clock) {
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
    return Material.NAUTILUS_SHELL;
  }

  @Override
  public boolean isSampleAvailable() {
    return clock.snapshot().hasTicks();
  }

  @Override
  public double onSample() {
    return clock.snapshot().ticksPerSecond(System.nanoTime());
  }

  @Override
  public String formattedValue(double t) {
    double sinceLastTickMS = clock.millisSinceLastTick(System.nanoTime());
    if (sinceLastTickMS > Math.max(1, countUpTickTimeThresholdMS)) {
      return Form.durationSplit(sinceLastTickMS, 1)[0];
    }

    if (t > 19.98) {
      return "20";
    }

    return Form.f(Math.round(t), 0);
  }

  @Override
  public String formattedSuffix(double t) {
    double sinceLastTickMS = clock.millisSinceLastTick(System.nanoTime());
    if (sinceLastTickMS > Math.max(1, countUpTickTimeThresholdMS)) {
      return Form.durationSplit(sinceLastTickMS, 1)[1];
    }

    return "TPS";
  }
}
