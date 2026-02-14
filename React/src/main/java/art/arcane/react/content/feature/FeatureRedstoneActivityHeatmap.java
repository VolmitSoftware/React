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

import art.arcane.react.content.sampler.SamplerRedstoneUpdates;
import art.arcane.react.util.data.TinyColor;
import org.bukkit.Chunk;

public class FeatureRedstoneActivityHeatmap extends FeatureChunkHeatmapBase {
    public static final String ID = "redstone-activity-heatmap";

    public FeatureRedstoneActivityHeatmap() {
        super(ID);
    }

    @Override
    protected String mapLabel() {
        return "Redstone Activity";
    }

    @Override
    protected TinyColor backgroundColor() {
        return new TinyColor(14, 4, 4);
    }

    @Override
    protected double chunkScore(Chunk chunk) {
        return chunkSample(chunk, SamplerRedstoneUpdates.ID);
    }

    @Override
    protected TinyColor colorFor(double normalized, double rawScore) {
        if (normalized < 0.5D) {
            return gradient(normalized * 2D, new TinyColor(40, 0, 0), new TinyColor(180, 20, 0));
        }

        return gradient((normalized - 0.5D) * 2D, new TinyColor(180, 20, 0), new TinyColor(255, 220, 40));
    }
}
