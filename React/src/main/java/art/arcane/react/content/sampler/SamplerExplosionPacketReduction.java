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

import art.arcane.react.React;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.content.feature.FeatureExplosionPacketBatching;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.RollingSequence;
import org.bukkit.Material;

public class SamplerExplosionPacketReduction extends ReactCachedSampler {
  public static final String ID = "explosion-packet-reduction";

  private transient final RollingSequence ratioAvg = new RollingSequence(5);

  public SamplerExplosionPacketReduction() {
    super(ID, 1000);
  }

  @Override
  public Material getIcon() {
    return Material.TNT;
  }

  @Override
  public double onSample() {
    FeatureExplosionPacketBatching feature = React.feature(FeatureExplosionPacketBatching.class);
    if (feature == null) {
      return 0D;
    }

    long explosions = feature.readAndResetExplosions();
    long clusters = feature.readAndResetClusters();
    if (explosions <= 0L) {
      ratioAvg.put(0D);
      return Math.max(0D, ratioAvg.getAverage());
    }

    double reduction = 1D - ((double) clusters / (double) explosions);
    if (reduction < 0D) {
      reduction = 0D;
    }
    if (reduction > 1D) {
      reduction = 1D;
    }
    ratioAvg.put(reduction);
    return Math.max(0D, ratioAvg.getAverage());
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t * 100D));
  }

  @Override
  public String formattedSuffix(double t) {
    return "%";
  }
}
