package art.arcane.react.model;

import art.arcane.react.platform.RuntimeStatsHost;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/** Chunk cost attribution from per-world chunk samples, scored the way the Bukkit observer scores them each second. */
public final class ChunkCostTally {
  public static final RuntimeStatsHost.CostShares EMPTY = new RuntimeStatsHost.CostShares(0D, 0D, 0D);

  private ChunkCostTally() {
  }

  /** Scores every chunk, halves its counters and drops chunks left with nothing. */
  public static RuntimeStatsHost.CostShares captureAndDecay(Collection<? extends Map<Long, SampledChunk>> worlds) {
    double total = 0D;
    double maxChunk = 0D;
    double maxWorld = 0D;
    for (Map<Long, SampledChunk> chunks : worlds) {
      double worldScore = 0D;
      Iterator<SampledChunk> iterator = chunks.values().iterator();
      while (iterator.hasNext()) {
        SampledChunk chunk = iterator.next();
        double score = chunk.captureAndDecay().total();
        worldScore += score;
        maxChunk = Math.max(maxChunk, score);
        if (chunk.isEmpty()) {
          iterator.remove();
        }
      }
      total += worldScore;
      maxWorld = Math.max(maxWorld, worldScore);
    }
    return new RuntimeStatsHost.CostShares(total, maxChunk, maxWorld);
  }
}
