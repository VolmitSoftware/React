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

import art.arcane.react.api.sampler.ReactCachedRateSampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class SamplerChunksGenerated extends ReactCachedRateSampler implements Listener {
  public static final String ID = "chunks-generated";

  public SamplerChunksGenerated() {
    super(ID, 1000);
  }

  @Override
  public Material getIcon() {
    return Material.FURNACE_MINECART;
  }

  @EventHandler
  public void on(ChunkLoadEvent event) {
    if (event.isNewChunk()) {
      increment();
      getChunkCounter(event.getChunk()).addAndGet(1D);
    }
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return "GEN/s";
  }
}
