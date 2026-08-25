package art.arcane.react.core.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryStoreTest {
  @Test
  void compactsRawSamplesWithExactWeightedEnvelope(@TempDir Path directory) throws Exception {
    HistoryStore store = new HistoryStore(directory, 3);
    store.initialize();
    HistorySegment raw = new HistorySegment(HistoryTier.RAW, 0L, 900);
    HistorySeries series = raw.series("tick-time", "Tick Time", "ms");
    for (int index = 0; index < 10; index++) {
      series.set(index, 40D + index);
    }
    store.write(raw);

    store.compactAll(HistoryTier.TEN_SECONDS.segmentDurationMs());
    Map<String, List<HistoryPoint>> points = store.points(
        HistoryTier.TEN_SECONDS,
        Set.of("tick-time"),
        0L,
        10_000L
    );

    HistoryPoint point = points.get("tick-time").get(0);
    assertEquals(10L, point.count());
    assertEquals(40D, point.first());
    assertEquals(40D, point.minimum());
    assertEquals(49D, point.maximum());
    assertEquals(445D, point.sum());
    assertEquals(49D, point.last());
  }

  @Test
  void pruningRequiresTheCoveringLowerResolutionSegment(@TempDir Path directory) throws Exception {
    HistoryStore store = new HistoryStore(directory, 3);
    store.initialize();
    HistorySegment raw = new HistorySegment(HistoryTier.RAW, 0L, 900);
    raw.series("tps", "TPS", "").set(0, 20D);
    store.write(raw);

    int beforeCompaction = store.prune(Long.MAX_VALUE / 4L, Map.of(HistoryTier.RAW, 1L));
    assertEquals(0, beforeCompaction);
    assertTrue(store.contains(HistoryTier.RAW, 0L));

    store.compactAll(HistoryTier.TEN_SECONDS.segmentDurationMs());
    int afterCompaction = store.prune(Long.MAX_VALUE / 4L, Map.of(HistoryTier.RAW, 1L));
    assertEquals(1, afterCompaction);
    assertFalse(store.contains(HistoryTier.RAW, 0L));
    assertTrue(store.contains(HistoryTier.TEN_SECONDS, 0L));
  }

  @Test
  void compactionRefreshesARollupAfterLateSourceRecovery(@TempDir Path directory) throws Exception {
    HistoryStore store = new HistoryStore(directory, 3);
    store.initialize();
    HistorySegment initial = new HistorySegment(HistoryTier.RAW, 0L, 900);
    initial.series("tps", "TPS", "").set(0, 20D);
    store.write(initial);
    store.compactAll(HistoryTier.TEN_SECONDS.segmentDurationMs());

    HistorySegment recovered = new HistorySegment(HistoryTier.RAW, 0L, 900);
    HistorySeries recoveredSeries = recovered.series("tps", "TPS", "");
    recoveredSeries.set(0, 20D);
    recoveredSeries.set(1, 40D);
    store.write(recovered);
    Path rawPath = directory.resolve(HistoryTier.RAW.directory()).resolve("0.rht");
    Files.setLastModifiedTime(rawPath, FileTime.fromMillis(System.currentTimeMillis() + 10_000L));

    store.compactAll(HistoryTier.TEN_SECONDS.segmentDurationMs());
    HistoryPoint point = store.points(
        HistoryTier.TEN_SECONDS,
        Set.of("tps"),
        0L,
        10_000L
    ).get("tps").getFirst();

    assertEquals(2L, point.count());
    assertEquals(60D, point.sum());
    assertEquals(40D, point.maximum());
  }
}
