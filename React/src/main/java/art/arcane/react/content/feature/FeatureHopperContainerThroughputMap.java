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

import art.arcane.react.content.sampler.SamplerHopperUpdates;
import art.arcane.react.util.data.TinyColor;
import org.bukkit.Chunk;

@art.arcane.react.util.config.ConfigDescription("Configuration for Hopper Container Throughput Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureHopperContainerThroughputMap extends FeatureChunkHeatmapBase {
    public static final String ID = "hopper-container-throughput-map";

    public FeatureHopperContainerThroughputMap() {
        super(ID);
    }

    @Override
    protected String mapLabel() {
        return "Hopper Throughput";
    }

    @Override
    protected TinyColor backgroundColor() {
        return new TinyColor(4, 12, 14);
    }

    @Override
    protected double chunkScore(Chunk chunk) {
        return chunkSample(chunk, SamplerHopperUpdates.ID);
    }

    @Override
    protected TinyColor colorFor(double normalized, double rawScore) {
        if (normalized < 0.5D) {
            return gradient(normalized * 2D, new TinyColor(0, 90, 120), new TinyColor(40, 200, 220));
        }

        return gradient((normalized - 0.5D) * 2D, new TinyColor(40, 200, 220), new TinyColor(255, 200, 40));
    }
}
