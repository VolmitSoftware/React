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

package art.arcane.react.content.feature;

import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.util.data.TinyColor;
import org.bukkit.Chunk;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Entity Pressure Heatmap feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureEntityPressureHeatmap extends FeatureChunkHeatmapBase {
  public static final String ID = "entity-pressure-heatmap";

  public FeatureEntityPressureHeatmap() {
    super(ID);
  }

  @Override
  protected String mapLabel() {
    return "Entity Pressure";
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(150, 104, 46);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(5, 7, 14);
  }

  @Override
  protected double chunkScore(Chunk chunk) {
    return chunkSample(chunk, SamplerEntities.ID);
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    if (normalized < 0.5D) {
      return gradient(normalized * 2D, new TinyColor(32, 110, 255), new TinyColor(80, 255, 120));
    }

    return gradient((normalized - 0.5D) * 2D, new TinyColor(80, 255, 120), new TinyColor(255, 80, 30));
  }
}
