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
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Collection;

public class SamplerChunkTickets extends ReactCachedSampler {
  public static final String ID = "chunk-tickets";
  private transient volatile boolean available = true;

  public SamplerChunkTickets() {
    super(ID, 5000);
  }

  @Override
  public Material getIcon() {
    return Material.NAME_TAG;
  }

  @Override
  public void start() {
    available = true;
    super.start();
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
          for (Collection<Chunk> tickets : world.getPluginChunkTickets().values()) {
            total += tickets.size();
          }
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
    return " tickets";
  }

  @Override
  public boolean isSampleAvailable() {
    return available;
  }
}
