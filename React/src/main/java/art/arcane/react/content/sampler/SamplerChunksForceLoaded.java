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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

public class SamplerChunksForceLoaded extends ReactCachedSampler {
  public static final String ID = "chunks-force-loaded";
  private transient volatile boolean available = true;

  public SamplerChunksForceLoaded() {
    super(ID, 5000);
  }

  @Override
  public Material getIcon() {
    return Material.LODESTONE;
  }

  @Override
  public double onSample() {
    return sampleOnMainThread(() -> {
      if (!available) {
        return 0D;
      }

      try {
        long total = 0L;
        for (World world : Bukkit.getWorlds()) {
          total += world.getForceLoadedChunks().size();
        }

        return (double) total;
      } catch (Throwable ex) {
        available = false;
        return 0D;
      }
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
    return " chunks";
  }
}
