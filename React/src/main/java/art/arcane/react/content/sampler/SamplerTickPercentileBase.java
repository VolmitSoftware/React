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

public abstract class SamplerTickPercentileBase extends ReactCachedSampler {
  private final transient TickClock clock;
  private final double percentile;
  private final String suffix;
  private int historyTicks = 1200;

  protected SamplerTickPercentileBase(String id, double percentile, String suffix) {
    this(id, percentile, suffix, TickClock.get());
  }

  protected SamplerTickPercentileBase(String id, double percentile, String suffix, TickClock clock) {
    super(id, 250);
    this.clock = clock;
    this.percentile = percentile;
    this.suffix = suffix;
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
    return Material.CLOCK;
  }

  @Override
  public boolean isSampleAvailable() {
    return clock.snapshot().hasHistory();
  }

  @Override
  public double onSample() {
    return clock.snapshot().percentile(percentile, historyTicks);
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 2);
  }

  @Override
  public String formattedSuffix(double t) {
    return clock.snapshot().workTimeMode() ? suffix : suffix + " GAP";
  }
}
