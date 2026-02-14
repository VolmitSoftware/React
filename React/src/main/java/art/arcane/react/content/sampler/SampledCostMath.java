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

import art.arcane.react.model.SampledChunk;
import art.arcane.react.model.SampledWorld;

import java.util.Map;

final class SampledCostMath {
    private SampledCostMath() {
    }

    static CostSnapshot snapshot(Map<String, SampledWorld> worlds) {
        double total = 0D;
        double maxChunk = 0D;
        double maxWorld = 0D;

        for (SampledWorld world : worlds.values()) {
            double worldScore = 0D;
            for (SampledChunk chunk : world.getChunks().values()) {
                double chunkScore = chunk.totalScore();
                worldScore += chunkScore;
                total += chunkScore;
                if (chunkScore > maxChunk) {
                    maxChunk = chunkScore;
                }
            }

            if (worldScore > maxWorld) {
                maxWorld = worldScore;
            }
        }

        return new CostSnapshot(total, maxChunk, maxWorld);
    }

    static double totalScore(Map<String, SampledWorld> worlds) {
        return snapshot(worlds).total();
    }

    static double maxWorldScore(Map<String, SampledWorld> worlds) {
        return snapshot(worlds).maxWorld();
    }

    static double maxChunkScore(Map<String, SampledWorld> worlds) {
        return snapshot(worlds).maxChunk();
    }

    record CostSnapshot(double total, double maxChunk, double maxWorld) {
    }
}
