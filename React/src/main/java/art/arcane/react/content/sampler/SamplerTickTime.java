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

public class SamplerTickTime extends ReactCachedSampler {
  public static final String ID = "tick-time";
  private static final int GAP_WINDOW_TICKS = 100;
  private final transient TickClock clock;

  public SamplerTickTime() {
    this(TickClock.get());
  }

  SamplerTickTime(TickClock clock) {
    super(ID, 50);
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
    return clock.snapshot().hasHistory();
  }

  @Override
  public double onSample() {
    return clock.snapshot().averageTickMS(GAP_WINDOW_TICKS);
  }

  @Override
  public String formattedValue(double t) {
    return Form.durationSplit(t, 2)[0];
  }

  @Override
  public String formattedSuffix(double t) {
    String unit = Form.durationSplit(t, 2)[1];
    return clock.snapshot().workTimeMode() ? unit : unit + " GAP";
  }
}
