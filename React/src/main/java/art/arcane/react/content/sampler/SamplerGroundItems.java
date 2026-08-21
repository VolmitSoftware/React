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
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

public class SamplerGroundItems extends ReactCachedSampler {
  public static final String ID = "ground-items";

  public SamplerGroundItems() {
    super(ID, 2000);
  }

  @Override
  public void start() {
    super.start();
    EntityCensusTracker.acquire();
  }

  @Override
  public void stop() {
    EntityCensusTracker.release();
    super.stop();
  }

  @Override
  public Material getIcon() {
    return Material.BONE_MEAL;
  }

  @Override
  public double onSample() {
    if (J.isFoliaThreading()) {
      EntityCensusTracker.refreshFolia();
      return (double) EntityCensusTracker.groundItems();
    }

    return sampleOnMainThread(() -> {
      EntityCensusTracker.refreshMainThread();
      return (double) EntityCensusTracker.groundItems();
    });
  }

  @Override
  public String format(double t) {
    return formattedValue(t) + formattedSuffix(t);
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return " items";
  }
}
