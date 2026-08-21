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

public class SamplerGcTimePercent extends ReactCachedSampler {
  public static final String ID = "gc-time-percent";

  public SamplerGcTimePercent() {
    super(ID, 1000);
  }

  @Override
  public void start() {
    super.start();
    GCStatsTracker.acquire();
  }

  @Override
  public void stop() {
    GCStatsTracker.release();
    super.stop();
  }

  @Override
  public Material getIcon() {
    return Material.EXPERIENCE_BOTTLE;
  }

  @Override
  public double onSample() {
    return GCStatsTracker.sampleGcTimePercent();
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 2);
  }

  @Override
  public String formattedSuffix(double t) {
    return "% GC";
  }
}
