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

package art.arcane.react.model;

import java.util.Map;

public record CostSnapshot(double total, double maxChunk, double maxWorld, SampledChunk worstChunk) {
  public static final CostSnapshot EMPTY = new CostSnapshot(0D, 0D, 0D, null);

  public static CostSnapshot capture(SampledServer server) {
    return capture(server, false);
  }

  public static CostSnapshot captureAndDecay(SampledServer server) {
    return capture(server, true);
  }

  private static CostSnapshot capture(SampledServer server, boolean decay) {
    double total = 0D;
    double maxChunk = 0D;
    double maxWorld = 0D;
    SampledChunk worst = null;
    double worstTotal = 0D;
    double worstSubScore = 0D;

    for (SampledWorld world : server.getWorlds().values()) {
      double worldScore = 0D;
      for (Map.Entry<Long, SampledChunk> entry : world.getChunks().entrySet()) {
        SampledChunk chunk = entry.getValue();
        SampledChunk.Score score = decay ? chunk.captureAndDecay() : new SampledChunk.Score(chunk.totalScore(), chunk.highestSubScore());
        double chunkScore = score.total();
        worldScore += chunkScore;
        total += chunkScore;
        if (chunkScore > maxChunk) {
          maxChunk = chunkScore;
        }
        if (worst == null
            || chunkScore > worstTotal
            || (chunkScore == worstTotal && score.highest() > worstSubScore)) {
          worst = chunk;
          worstTotal = chunkScore;
          worstSubScore = score.highest();
        }
        if (decay && chunk.isEmpty()) {
          world.getChunks().remove(entry.getKey(), chunk);
        }
      }

      if (worldScore > maxWorld) {
        maxWorld = worldScore;
      }
    }

    return worst == null ? EMPTY : new CostSnapshot(total, maxChunk, maxWorld, worst);
  }
}
