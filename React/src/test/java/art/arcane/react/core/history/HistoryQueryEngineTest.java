package art.arcane.react.core.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class HistoryQueryEngineTest {
  @Test
  void completeRollupDoesNotReadFinerTiers(@TempDir Path directory) throws Exception {
    HistoryStore store = Mockito.spy(new HistoryStore(directory, 3));
    store.initialize();
    HistorySegment rollup = new HistorySegment(HistoryTier.TEN_SECONDS, 0L, 2);
    rollup.series("tps", "TPS", "").set(0, 20D);
    rollup.series("tps", "TPS", "").set(1, 19D);
    store.write(rollup);
    HistoryQueryEngine engine = new HistoryQueryEngine(store, (ids, from, to) -> Map.of());

    HistoryQueryResult result = engine.query(List.of("tps"), 0L, 20_000L, 10_000L, 1L, 20_000L);

    assertEquals(2, result.series().getFirst().points().size());
    verify(store, never()).points(any(HistoryStore.QueryView.class), eq(HistoryTier.RAW), anySet(), anyLong(), anyLong());
  }

  @Test
  void fillsOnlyMissingRollupBucketsAndKeepsLiveSamples(@TempDir Path directory) throws Exception {
    HistoryStore store = Mockito.spy(new HistoryStore(directory, 3));
    store.initialize();
    HistorySegment rollup = new HistorySegment(HistoryTier.TEN_SECONDS, 0L, 3);
    rollup.series("tps", "TPS", "").set(0, 20D);
    rollup.series("tps", "TPS", "").set(2, 18D);
    store.write(rollup);
    HistorySegment raw = new HistorySegment(HistoryTier.RAW, 0L, 30);
    raw.series("tps", "TPS", "").set(0, 1D);
    raw.series("tps", "TPS", "").set(10, 17D);
    store.write(raw);
    HistoryPoint live = new HistoryPoint(11_000L, 1_000L, 19D, 19D, 19D, 19D, 19D, 1L);
    HistoryQueryEngine engine = new HistoryQueryEngine(store, (ids, from, to) -> Map.of("tps", List.of(live)));

    List<HistoryPoint> points = engine.query(List.of("tps"), 0L, 30_000L, 10_000L, 1L, 30_000L)
        .series().getFirst().points();

    assertEquals(3, points.size());
    assertEquals(20D, points.get(0).sum());
    assertEquals(36D, points.get(1).sum());
    assertEquals(2L, points.get(1).count());
    assertEquals(18D, points.get(2).sum());
    verify(store).points(any(HistoryStore.QueryView.class), eq(HistoryTier.RAW), eq(Set.of("tps")), eq(10_000L), eq(20_000L));
    verify(store, never()).points(any(HistoryStore.QueryView.class), eq(HistoryTier.RAW), eq(Set.of("tps")), eq(0L), eq(30_000L));
  }

  @Test
  void queryCacheObservesRewrittenAndPrunedSegments(@TempDir Path directory) throws Exception {
    HistoryStore store = new HistoryStore(directory, 3);
    store.initialize();
    HistorySegment raw = new HistorySegment(HistoryTier.RAW, 0L, 900);
    raw.series("tps", "TPS", "").set(0, 20D);
    store.write(raw);
    assertEquals(20D, readPoints(store, HistoryTier.RAW, Set.of("tps"), 0L, 1_000L).get("tps").getFirst().sum());
    raw.series("tps").set(0, 18D);
    store.write(raw);
    assertEquals(18D, readPoints(store, HistoryTier.RAW, Set.of("tps"), 0L, 1_000L).get("tps").getFirst().sum());
    store.compactAll(HistoryTier.TEN_SECONDS.segmentDurationMs());
    store.prune(Long.MAX_VALUE / 4L, Map.of(HistoryTier.RAW, 1L));
    assertEquals(Map.of(), readPoints(store, HistoryTier.RAW, Set.of("tps"), 0L, 1_000L));
  }
  @Test
  void coldQueriesReadSharedMissingRangesTogether(@TempDir Path directory) throws Exception {
    HistoryStore store = Mockito.spy(new HistoryStore(directory, 3));
    store.initialize();
    HistorySegment raw = new HistorySegment(HistoryTier.RAW, 0L, 900);
    raw.series("tps", "TPS", "").set(0, 20D);
    raw.series("tick-time", "Tick Time", "ms").set(0, 10D);
    store.write(raw);
    HistoryQueryEngine engine = new HistoryQueryEngine(store, (ids, from, to) -> Map.of());

    HistoryQueryResult result = engine.query(List.of("tps", "tick-time"), 0L, 10_000L, 10_000L, 1L, 10_000L);

    assertEquals(2, result.series().size());
    verify(store, times(1)).points(any(HistoryStore.QueryView.class), eq(HistoryTier.RAW), eq(Set.of("tps", "tick-time")), eq(0L), eq(10_000L));
    verify(store, times(1)).points(any(HistoryStore.QueryView.class), eq(HistoryTier.TEN_SECONDS), eq(Set.of("tps", "tick-time")), eq(0L), eq(10_000L));
  }

  @Test
  void compactionAndPruningCannotRemoveAnOpenQuerySource(@TempDir Path directory) throws Exception {
    HistoryStore store = Mockito.spy(new HistoryStore(directory, 3));
    store.initialize();
    HistorySegment raw = new HistorySegment(HistoryTier.RAW, 0L, 900);
    raw.series("tps", "TPS", "").set(0, 20D);
    raw.series("tps").set(1, 18D);
    store.write(raw);
    Path rawPath = directory.resolve("raw/0.rht");
    CountDownLatch beforeRaw = new CountDownLatch(1);
    CountDownLatch releaseRaw = new CountDownLatch(1);
    Mockito.doAnswer(invocation -> {
      beforeRaw.countDown();
      assertTrue(releaseRaw.await(5L, TimeUnit.SECONDS));
      return invocation.callRealMethod();
    }).when(store).points(any(HistoryStore.QueryView.class), eq(HistoryTier.RAW), anySet(), anyLong(), anyLong());
    HistoryQueryEngine engine = new HistoryQueryEngine(store, (ids, from, to) -> Map.of());
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<HistoryQueryResult> query = executor.submit(() -> engine.query(List.of("tps"), 0L, 10_000L, 10_000L, 1L, 10_000L));
      try {
        assertTrue(beforeRaw.await(5L, TimeUnit.SECONDS));
        store.compactAll(HistoryTier.TEN_SECONDS.segmentDurationMs());
        assertEquals(1, store.prune(Long.MAX_VALUE / 4L, Map.of(HistoryTier.RAW, 1L)));
        assertFalse(store.contains(HistoryTier.RAW, 0L));
        assertTrue(Files.exists(rawPath));
        HistoryQueryResult newer = engine.query(List.of("tps"), 0L, 10_000L, 10_000L, 1L, 10_000L);
        assertEquals(38D, newer.series().getFirst().points().getFirst().sum());
      } finally {
        releaseRaw.countDown();
      }
      HistoryPoint result = query.get(5L, TimeUnit.SECONDS).series().getFirst().points().getFirst();
      assertEquals(38D, result.sum());
      assertEquals(2L, result.count());
      assertFalse(Files.exists(rawPath));
    }
  }

  @Test
  void failedQueryReleasesRetiredFiles(@TempDir Path directory) throws Exception {
    HistoryStore store = Mockito.spy(new HistoryStore(directory, 3));
    store.initialize();
    HistorySegment raw = new HistorySegment(HistoryTier.RAW, 0L, 900);
    raw.series("tps", "TPS", "").set(0, 20D);
    store.write(raw);
    Mockito.doAnswer(invocation -> {
      store.compactAll(HistoryTier.TEN_SECONDS.segmentDurationMs());
      store.prune(Long.MAX_VALUE / 4L, Map.of(HistoryTier.RAW, 1L));
      throw new IOException("Read failed");
    }).when(store).points(any(HistoryStore.QueryView.class), eq(HistoryTier.RAW), anySet(), anyLong(), anyLong());
    HistoryQueryEngine engine = new HistoryQueryEngine(store, (ids, from, to) -> Map.of());

    assertThrows(IOException.class, () -> engine.query(List.of("tps"), 0L, 10_000L, 1_000L, 1L, 10_000L));
    assertFalse(Files.exists(directory.resolve("raw/0.rht")));
  }

  private Map<String, List<HistoryPoint>> readPoints(HistoryStore store, HistoryTier tier, Set<String> ids, long fromMs, long toMs) throws Exception {
    try (HistoryStore.QueryView view = store.openQuery(fromMs, toMs, Map::of)) {
      return store.points(view, tier, ids, fromMs, toMs);
    }
  }

}
