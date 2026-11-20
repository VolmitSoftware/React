package art.arcane.react.mod.cost;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Distinct chunks per owner, summed over levels. An owner holding several tickets on one chunk counts it once; the
 * same chunk position in two levels counts twice. The per-owner sets are kept between scans to avoid reallocating.
 */
public final class ChunkLoaderTally {
    private final Map<String, LongOpenHashSet> level = new HashMap<>();
    private final Map<String, Integer> totals = new TreeMap<>();

    public void add(String owner, long chunk) {
        LongOpenHashSet chunks = level.get(owner);
        if (chunks == null) {
            chunks = new LongOpenHashSet();
            level.put(owner, chunks);
        }
        chunks.add(chunk);
    }

    public void endLevel() {
        for (Map.Entry<String, LongOpenHashSet> entry : level.entrySet()) {
            LongOpenHashSet chunks = entry.getValue();
            if (!chunks.isEmpty()) {
                totals.merge(entry.getKey(), chunks.size(), Integer::sum);
                chunks.clear();
            }
        }
    }

    /** Ends the current level and returns the counts of the scan, sorted by owner, then starts a new scan. */
    public Map<String, Integer> finish() {
        endLevel();
        Map<String, Integer> result = new TreeMap<>(totals);
        totals.clear();
        return result;
    }
}
