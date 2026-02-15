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

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.util.data.TinyColor;
import org.bukkit.Chunk;

@art.arcane.react.util.config.ConfigDescription("Configuration for Chunk Sampler Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureChunkSamplerMap extends FeatureChunkHeatmapBase {
    public static final String ID = "chunk-sampler-map";

    public FeatureChunkSamplerMap() {
        super(ID);
    }

    @Override
    protected String mapLabel() {
        return "Chunk Cost Heat";
    }

    @Override
    protected TinyColor headerColor() {
        return new TinyColor(84, 114, 146);
    }

    @Override
    protected double chunkScore(Chunk chunk) {
        ObserverController observer = React.controller(ObserverController.class);
        if (observer == null || observer.getSampled() == null || chunk == null) {
            return 0D;
        }

        SampledChunk sampledChunk = observer.getSampled().getChunk(chunk);
        return sampledChunk == null ? 0D : Math.max(0D, sampledChunk.totalScore());
    }

    @Override
    protected TinyColor colorFor(double normalized, double rawScore) {
        if (normalized < 0.33D) {
            return gradient(normalized / 0.33D, new TinyColor(22, 84, 190), new TinyColor(52, 182, 214));
        }
        if (normalized < 0.66D) {
            return gradient((normalized - 0.33D) / 0.33D, new TinyColor(52, 182, 214), new TinyColor(245, 184, 74));
        }
        return gradient((normalized - 0.66D) / 0.34D, new TinyColor(245, 184, 74), new TinyColor(255, 82, 44));
    }
}
