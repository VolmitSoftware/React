package art.arcane.react.mod.cost;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkLoaderTallyTest {
    @Test
    void finish_countsEachChunkOncePerOwnerAndLevel() {
        ChunkLoaderTally tally = new ChunkLoaderTally();
        tally.add("create", 1L);
        tally.add("create", 1L);
        tally.add("create", 2L);
        tally.add("minecraft", 1L);
        tally.endLevel();
        tally.add("create", 1L);

        Map<String, Integer> counts = tally.finish();

        assertEquals(Map.of("create", 3, "minecraft", 1), counts);
    }

    @Test
    void finish_startsTheNextScanEmpty() {
        ChunkLoaderTally tally = new ChunkLoaderTally();
        tally.add("create", 1L);
        tally.finish();
        tally.add("ae2", 5L);

        assertEquals(Map.of("ae2", 1), tally.finish());
    }
}
