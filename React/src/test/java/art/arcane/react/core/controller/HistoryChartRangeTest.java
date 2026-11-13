package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.history.ChartHistory;
import art.arcane.react.core.history.ChartRange;
import art.arcane.react.core.history.HistoryQueryEngine;
import art.arcane.react.core.history.HistorySegment;
import art.arcane.react.core.history.HistoryStore;
import art.arcane.react.core.history.HistoryTier;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.util.project.registry.Registry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryChartRangeTest {
  private static final long BASE = 1_763_030_400_000L;

  @Test
  void scheduledCaptureFeedsTheChartRings() {
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.getId()).thenReturn("tick-time");
    Mockito.when(sampler.getName()).thenReturn("Tick Time");
    Mockito.when(sampler.captureReading()).thenReturn(new Sampler.Reading(12.5D, 1L, true, Sampler.Metadata.UNKNOWN, ""));
    SampleController samples = Mockito.mock(SampleController.class);
    @SuppressWarnings("unchecked")
    Registry<Sampler> registry = Mockito.mock(Registry.class);
    Mockito.when(samples.getSamplers()).thenReturn(registry);
    Mockito.when(registry.all()).thenReturn(List.of(sampler));
    HistoryController history = new HistoryController();
    history.setEnabled(false);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(SampleController.class)).thenReturn(samples);
      history.captureIfDue();
    }

    long capturedAtMs = history.latest().capturedAtMs();
    assertTrue(capturedAtMs > 0L);
    for (ChartRange range : ChartRange.values()) {
      ChartHistory.Window window = history.chartRange(range, List.of("tick-time"), capturedAtMs + range.stepMs());
      assertEquals(12.5D, window.series().getFirst().values()[359], range.id());
    }
  }

  @Test
  void seedingLoadsStoredHistoryWithoutCountingPostStartSamplesTwice(@TempDir Path directory) throws Exception {
    long startedAtMs = BASE + 125_400L;
    HistoryStore store = new HistoryStore(directory, 3);
    store.initialize();
    Map<Long, Double> samples = new LinkedHashMap<>();
    samples.put(BASE + 95_000L, 10D);
    samples.put(BASE + 97_000L, 20D);
    samples.put(BASE + 121_000L, 50D);
    samples.put(BASE + 126_000L, 70D);
    writeRaw(store, samples);
    HistoryController history = new HistoryController();
    history.setHistoryStore(store);
    history.setQueryEngine(new HistoryQueryEngine(store, (ids, fromMs, toMs) -> Map.of()));
    history.setStartedAtMs(startedAtMs);
    history.getChartHistory().record(snapshot(BASE + 126_000L, 70D));

    history.seedChartHistory();

    Double[] hour = history.chartRange(ChartRange.HOUR, List.of("tick-time"), BASE + 130_000L).series().getFirst().values();
    assertEquals(15D, hour[356]);
    assertNull(hour[357]);
    assertNull(hour[358]);
    assertEquals(60D, hour[359]);
    Double[] day = history.chartRange(ChartRange.DAY, List.of("tick-time"), BASE + 240_000L).series().getFirst().values();
    assertEquals(37.5D, day[359]);
    Double[] sixHours = history.chartRange(ChartRange.SIX_HOURS, List.of("tick-time"), BASE + 180_000L).series().getFirst().values();
    assertEquals(15D, sixHours[358]);
    assertEquals(60D, sixHours[359]);
  }

  @Test
  void seedingWithoutStorageLeavesTheRingsEmpty() {
    HistoryController history = new HistoryController();
    history.setStartedAtMs(BASE);

    history.seedChartHistory();

    for (Double value : history.chartRange(ChartRange.HOUR, List.of(), BASE).series().getFirst().values()) {
      assertNull(value);
    }
  }

  private static void writeRaw(HistoryStore store, Map<Long, Double> samples) throws Exception {
    Map<Long, HistorySegment> segments = new LinkedHashMap<>();
    long interval = HistoryTier.RAW.intervalMs();
    int buckets = Math.toIntExact(HistoryTier.RAW.segmentDurationMs() / interval);
    for (Map.Entry<Long, Double> sample : samples.entrySet()) {
      long start = HistoryTier.RAW.segmentStart(sample.getKey());
      HistorySegment segment = segments.computeIfAbsent(start, key -> new HistorySegment(HistoryTier.RAW, key, buckets));
      int index = Math.toIntExact((sample.getKey() - start) / interval);
      segment.series("tick-time", "Tick Time", "ms").add(index, sample.getValue(), sample.getKey());
    }
    for (HistorySegment segment : segments.values()) {
      store.write(segment);
    }
  }

  private static MetricSnapshot snapshot(long capturedAtMs, double value) {
    return MetricSnapshot.of(1L, capturedAtMs, List.of(new MetricSnapshotValue(
        "tick-time", "Tick Time", "ms", value, "", true, capturedAtMs, Sampler.Metadata.UNKNOWN, "",
        Sampler.Diagnostics.EMPTY)));
  }
}
