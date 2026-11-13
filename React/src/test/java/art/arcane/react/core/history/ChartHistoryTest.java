package art.arcane.react.core.history;

import art.arcane.react.api.sampler.Sampler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartHistoryTest {
  private static final long STEP = ChartRange.HOUR.stepMs();
  private static final long BASE = 1_763_030_400_000L;

  @Test
  void rangesResolveByIdWithFixedResolution() {
    assertEquals(ChartRange.HOUR, ChartRange.byId("1h"));
    assertEquals(ChartRange.SIX_HOURS, ChartRange.byId("6h"));
    assertEquals(ChartRange.DAY, ChartRange.byId("24h"));
    assertNull(ChartRange.byId("7d"));
    assertNull(ChartRange.byId(null));
    assertEquals(10_000L, ChartRange.HOUR.stepMs());
    assertEquals(60_000L, ChartRange.SIX_HOURS.stepMs());
    assertEquals(240_000L, ChartRange.DAY.stepMs());
    assertEquals(360 * 240_000L, ChartRange.DAY.spanMs());
    assertEquals(13, ChartHistory.SAMPLER_IDS.size());
  }

  @Test
  void averagesEachBucketAndLeavesEmptyBucketsNull() {
    ChartHistory history = new ChartHistory();
    history.record(snapshot(BASE + 1_000L, "tick-time", 10D));
    history.record(snapshot(BASE + 6_000L, "tick-time", 20D));
    history.record(snapshot(BASE + 2 * STEP + 500L, "tick-time", 7D));

    ChartHistory.Window window = history.read(ChartRange.HOUR, List.of("tick-time"), BASE + 3 * STEP + 1L);

    Double[] values = window.series().getFirst().values();
    assertEquals(360, values.length);
    assertEquals(15D, values[357]);
    assertNull(values[358]);
    assertEquals(7D, values[359]);
    assertNull(values[0]);
  }

  @Test
  void toExcludesTheOpenBucketAndFromCoversExactlyTheRing() {
    ChartHistory history = new ChartHistory();
    long now = BASE + 4_321L;
    history.record(snapshot(now, "tick-time", 3D));

    ChartHistory.Window window = history.read(ChartRange.HOUR, List.of("tick-time"), now);

    assertEquals(BASE, window.toMs());
    assertEquals(BASE - 360 * STEP, window.fromMs());
    for (Double value : window.series().getFirst().values()) {
      assertNull(value);
    }
    ChartHistory.Window next = history.read(ChartRange.HOUR, List.of("tick-time"), BASE + STEP);
    assertEquals(3D, next.series().getFirst().values()[359]);
  }

  @Test
  void bucketsBeyondTheWindowRollOff() {
    ChartHistory history = new ChartHistory();
    history.record(snapshot(BASE, "tick-time", 1D));
    history.record(snapshot(BASE + STEP, "tick-time", 2D));
    long later = BASE + 360 * STEP;
    history.record(snapshot(later, "tick-time", 9D));
    history.record(snapshot(BASE - STEP, "tick-time", 100D));

    Double[] values = history.read(ChartRange.HOUR, List.of("tick-time"), later + STEP).series().getFirst().values();

    assertEquals(2D, values[0]);
    assertEquals(9D, values[359]);
    history.record(snapshot(later + STEP, "tick-time", 4D));
    Double[] after = history.read(ChartRange.HOUR, List.of("tick-time"), later + 2 * STEP).series().getFirst().values();
    assertNull(after[0]);
    assertEquals(9D, after[358]);
    assertEquals(4D, after[359]);
    for (Double value : after) {
      if (value != null) {
        assertTrue(value == 9D || value == 4D, "rolled-off value " + value);
      }
    }
  }

  @Test
  void slotsReusedAfterAPauseStartEmpty() {
    ChartHistory history = new ChartHistory();
    for (int bucket = 0; bucket < 360; bucket++) {
      history.record(snapshot(BASE + bucket * STEP, "tick-time", 5D));
    }
    long resumed = BASE + 400 * STEP;
    history.record(snapshot(resumed, "tick-time", 8D));

    Double[] values = history.read(ChartRange.HOUR, List.of("tick-time"), resumed + STEP).series().getFirst().values();

    assertEquals(8D, values[359]);
    for (int index = 0; index < 319; index++) {
      assertEquals(5D, values[index], "bucket " + index);
    }
    for (int index = 319; index < 359; index++) {
      assertNull(values[index], "bucket " + index);
    }
  }

  @Test
  void aLongPauseClearsEveryBucket() {
    ChartHistory history = new ChartHistory();
    history.record(snapshot(BASE, "tick-time", 5D));
    long resumed = BASE + 10_000 * STEP;
    history.record(snapshot(resumed, "tick-time", 8D));

    Double[] values = history.read(ChartRange.HOUR, List.of("tick-time"), resumed + STEP).series().getFirst().values();

    int filled = 0;
    for (Double value : values) {
      if (value != null) {
        filled++;
      }
    }
    assertEquals(1, filled);
    assertEquals(8D, values[359]);
  }

  @Test
  void unknownIdsAreSkippedAndEmptyIdsReturnEveryTrackedSampler() {
    ChartHistory history = new ChartHistory();

    ChartHistory.Window selected = history.read(ChartRange.HOUR, List.of("memory-used", "nope", "tick-time"), BASE);
    ChartHistory.Window all = history.read(ChartRange.HOUR, List.of(), BASE);
    ChartHistory.Window unspecified = history.read(ChartRange.HOUR, null, BASE);

    assertEquals(List.of("memory-used", "tick-time"), ids(selected));
    assertEquals(ChartHistory.SAMPLER_IDS, ids(all));
    assertEquals(ChartHistory.SAMPLER_IDS, ids(unspecified));
  }

  @Test
  void seedMergesWithRecordedSamples() {
    ChartHistory history = new ChartHistory();
    long bucket = BASE - STEP;
    history.record(snapshot(bucket + 9_000L, "tick-time", 40D));
    history.seed(ChartRange.HOUR, Map.of("tick-time", List.of(
        point(bucket, 30D, 3L),
        point(bucket - 400 * STEP, 1_000D, 1L)
    )));

    Double[] values = history.read(ChartRange.HOUR, List.of("tick-time"), BASE).series().getFirst().values();

    assertEquals(17.5D, values[359]);
    int filled = 0;
    for (Double value : values) {
      if (value != null) {
        filled++;
      }
    }
    assertEquals(1, filled);
  }

  @Test
  void seedFillsEveryRangeAtItsOwnResolution() {
    ChartHistory history = new ChartHistory();
    long dayStep = ChartRange.DAY.stepMs();
    long dayBucket = Math.floorDiv(BASE, dayStep) * dayStep - dayStep;
    history.seed(ChartRange.DAY, Map.of("memory-used", List.of(point(dayBucket, 2_000D, 4L))));

    Double[] day = history.read(ChartRange.DAY, List.of("memory-used"), dayBucket + dayStep).series().getFirst().values();
    Double[] hour = history.read(ChartRange.HOUR, List.of("memory-used"), dayBucket + dayStep).series().getFirst().values();

    assertEquals(500D, day[359]);
    for (Double value : hour) {
      assertNull(value);
    }
  }

  @Test
  void averagesRoundToFourSignificantDigits() {
    ChartHistory history = new ChartHistory();
    history.record(snapshot(BASE, "tick-time", 12.34567D));
    history.record(snapshot(BASE, "memory-used", 4_567_891_234D));
    history.record(snapshot(BASE, "tick-ms-p50", 0.000123456D));

    ChartHistory.Window window = history.read(
        ChartRange.HOUR, List.of("tick-time", "memory-used", "tick-ms-p50"), BASE + STEP);

    assertEquals(12.35D, window.series().get(0).values()[359]);
    assertEquals(4.568E9D, window.series().get(1).values()[359]);
    assertEquals(1.235E-4D, window.series().get(2).values()[359]);
  }

  @Test
  void unavailableAndNonFiniteValuesAreIgnored() {
    ChartHistory history = new ChartHistory();
    history.record(MetricSnapshot.of(1L, BASE, List.of(
        value("tick-time", 50D, false),
        value("memory-used", Double.NaN, true),
        value("memory-free", Double.POSITIVE_INFINITY, true),
        value("untracked", 1D, true)
    )));
    history.record(snapshot(BASE + 1L, "tick-time", 4D));

    ChartHistory.Window window = history.read(
        ChartRange.HOUR, List.of("tick-time", "memory-used", "memory-free"), BASE + STEP);

    assertEquals(4D, window.series().get(0).values()[359]);
    assertNull(window.series().get(1).values()[359]);
    assertNull(window.series().get(2).values()[359]);
  }

  @Test
  void everyRangeReceivesEachRecordedSample() {
    ChartHistory history = new ChartHistory();
    long dayStep = ChartRange.DAY.stepMs();
    long start = Math.floorDiv(BASE, dayStep) * dayStep;
    history.record(snapshot(start, "network-send-rate", 2D));
    history.record(snapshot(start + STEP, "network-send-rate", 4D));

    Double[] hour = history.read(ChartRange.HOUR, List.of("network-send-rate"), start + 2 * STEP).series().getFirst().values();
    assertEquals(2D, hour[358]);
    assertEquals(4D, hour[359]);
    for (ChartRange range : List.of(ChartRange.SIX_HOURS, ChartRange.DAY)) {
      Double[] values = history.read(range, List.of("network-send-rate"), start + range.stepMs()).series().getFirst().values();
      assertEquals(3D, values[359], range.id());
    }
  }

  private static List<String> ids(ChartHistory.Window window) {
    List<String> ids = new ArrayList<>();
    for (ChartHistory.Series series : window.series()) {
      ids.add(series.id());
    }
    return ids;
  }

  private static HistoryPoint point(long timestampMs, double sum, long count) {
    double average = sum / count;
    return new HistoryPoint(timestampMs, STEP, average, average, average, sum, average, count, timestampMs, timestampMs);
  }

  private static MetricSnapshot snapshot(long capturedAtMs, String id, double value) {
    return MetricSnapshot.of(1L, capturedAtMs, List.of(value(id, value, true)));
  }

  private static MetricSnapshotValue value(String id, double value, boolean available) {
    return new MetricSnapshotValue(id, id, "", value, "", available, 1L, Sampler.Metadata.UNKNOWN, "", Sampler.Diagnostics.EMPTY);
  }
}
