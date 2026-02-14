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

import art.arcane.react.content.sampler.SamplerChunkGenMS;
import art.arcane.react.content.sampler.SamplerChunkLoadMS;
import art.arcane.react.content.sampler.SamplerChunksGenerated;
import art.arcane.react.content.sampler.SamplerChunksLoaded;
import art.arcane.react.util.data.TinyColor;
import org.bukkit.Chunk;

public class FeatureChunkLoadGenCostMap extends FeatureChunkHeatmapBase {
    public static final String ID = "chunk-load-gen-cost-map";

    public FeatureChunkLoadGenCostMap() {
        super(ID);
    }

    @Override
    protected String mapLabel() {
        return "Chunk Load/Gen Cost";
    }

    @Override
    protected TinyColor backgroundColor() {
        return new TinyColor(10, 10, 16);
    }

    @Override
    protected double chunkScore(Chunk chunk) {
        double loadMS = chunkSample(chunk, SamplerChunkLoadMS.ID);
        double genMS = chunkSample(chunk, SamplerChunkGenMS.ID);
        double loadRate = chunkSample(chunk, SamplerChunksLoaded.ID);
        double genRate = chunkSample(chunk, SamplerChunksGenerated.ID);

        // Generation spikes are usually more expensive than plain chunk loads.
        return (loadMS * 1.00D) + (genMS * 1.35D) + (loadRate * 0.4D) + (genRate * 0.7D);
    }

    @Override
    protected TinyColor colorFor(double normalized, double rawScore) {
        if (normalized < 0.5D) {
            return gradient(normalized * 2D, new TinyColor(40, 60, 180), new TinyColor(80, 180, 255));
        }

        return gradient((normalized - 0.5D) * 2D, new TinyColor(80, 180, 255), new TinyColor(255, 90, 20));
    }
}
