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

public record CostSnapshot(double total, double maxChunk, double maxWorld, SampledChunk worstChunk) {
  public static final CostSnapshot EMPTY = new CostSnapshot(0D, 0D, 0D, null);

  public static CostSnapshot capture(SampledServer server) {
    double total = 0D;
    double maxChunk = 0D;
    double maxWorld = 0D;
    SampledChunk worst = null;
    double worstTotal = 0D;

    for (SampledWorld world : server.getWorlds().values()) {
      double worldScore = 0D;
      for (SampledChunk chunk : world.getChunks().values()) {
        double chunkScore = chunk.totalScore();
        worldScore += chunkScore;
        total += chunkScore;
        if (chunkScore > maxChunk) {
          maxChunk = chunkScore;
        }
        if (worst == null
            || chunkScore > worstTotal
            || (chunkScore == worstTotal && chunk.highestSubScore() > worst.highestSubScore())) {
          worst = chunk;
          worstTotal = chunkScore;
        }
      }

      if (worldScore > maxWorld) {
        maxWorld = worldScore;
      }
    }

    return worst == null ? EMPTY : new CostSnapshot(total, maxChunk, maxWorld, worst);
  }
}
