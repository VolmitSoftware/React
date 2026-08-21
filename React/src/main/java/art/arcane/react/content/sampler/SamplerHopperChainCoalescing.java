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
import art.arcane.react.content.feature.FeatureHopperChainCoalescing;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RollingSequence;
import org.bukkit.Material;

public class SamplerHopperChainCoalescing extends ReactCachedSampler {
  public static final String ID = "hopper-chain-coalescing";
  private static final double ONE_OVER_MS = 1.0 / 1000D;

  private transient final RollingSequence avg = new RollingSequence(5);
  private transient long lastSample = 0L;

  public SamplerHopperChainCoalescing() {
    super(ID, 1000);
  }

  @Override
  public Material getIcon() {
    return Material.HOPPER;
  }

  @Override
  public double onSample() {
    FeatureHopperChainCoalescing feature = React.feature(FeatureHopperChainCoalescing.class);
    if (feature == null) {
      return 0D;
    }

    if (lastSample == 0L) {
      lastSample = M.ms();
    }

    long ticksSaved = feature.readAndResetTicksSaved();
    long now = M.ms();
    long durationMs = Math.max(now - lastSample, 1000L);
    lastSample = now;
    avg.put(ticksSaved / (durationMs * ONE_OVER_MS));
    return Math.max(0D, avg.getAverage());
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return "HC/s";
  }
}
