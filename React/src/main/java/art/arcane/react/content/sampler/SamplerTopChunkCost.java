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
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledWorld;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.Map;

public class SamplerTopChunkCost extends ReactCachedSampler {
  public static final String ID = "top-chunk-cost";

  public SamplerTopChunkCost() {
    super(ID, 1000);
  }

  @Override
  public Material getIcon() {
    return Material.MAGMA_BLOCK;
  }

  @Override
  public double onSample() {
    return executeSync(() -> {
      Map<String, SampledWorld> worlds = React.controller(ObserverController.class).getSampled().getWorlds();
      SampledCostMath.CostSnapshot snapshot = SampledCostMath.snapshot(worlds);
      if (snapshot.total() <= 0D) {
        return 0D;
      }

      double tickMS = React.sampler(SamplerTickTime.ID).sample();
      return Math.max(0D, (snapshot.maxChunk() / snapshot.total()) * tickMS);
    });
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 2);
  }

  @Override
  public String formattedSuffix(double t) {
    return "ms TOPC";
  }
}
