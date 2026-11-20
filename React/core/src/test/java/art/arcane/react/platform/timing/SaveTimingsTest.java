package art.arcane.react.platform.timing;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveTimingsTest {
  private final AtomicLong clock = new AtomicLong(1_000_000L);
  private final SaveTimings timings = new SaveTimings(clock::get);

  private SaveTimings.Save save(SaveTimings.Kind kind, long durationMs) {
    timings.begin(kind, 0L);
    return timings.end(durationMs * 1_000_000L);
  }

  @Test
  void recordsDurationAndSlowestWorld() {
    timings.begin(SaveTimings.Kind.AUTOSAVE, 1_000_000L);
    timings.worldSaved("minecraft:overworld", 30_000_000L);
    timings.worldSaved("minecraft:the_nether", 120_000_000L);
    timings.worldSaved("minecraft:the_end", 5_000_000L);
    SaveTimings.Save save = timings.end(201_000_000L);

    assertEquals(200D, save.durationMs(), 1.0E-9D);
    assertEquals("minecraft:the_nether", save.slowestWorld());
    assertEquals(120D, save.slowestWorldMs(), 1.0E-9D);
    assertEquals(SaveTimings.Kind.AUTOSAVE, timings.last().orElseThrow().kind());
  }

  @Test
  void ignoresWorldsAndEndsOutsideASave() {
    timings.worldSaved("minecraft:overworld", 50_000_000L);
    assertNull(timings.end(10L));
    assertTrue(timings.last().isEmpty());
  }

  @Test
  void maxCoversOnlyTheWindow() {
    save(SaveTimings.Kind.MANUAL, 900L);
    clock.addAndGet(SaveTimings.MAX_WINDOW_MS + 1L);
    save(SaveTimings.Kind.AUTOSAVE, 300L);
    assertEquals(300D, timings.maxDurationMs(), 1.0E-9D);
  }

  @Test
  void rateFollowsTheMeanGapAndFallsAfterSavingStops() {
    assertEquals(0D, timings.savesPerHour());
    save(SaveTimings.Kind.AUTOSAVE, 10L);
    assertEquals(0D, timings.savesPerHour(), "one save has no gap");
    clock.addAndGet(300_000L);
    save(SaveTimings.Kind.AUTOSAVE, 10L);
    clock.addAndGet(300_000L);
    save(SaveTimings.Kind.AUTOSAVE, 10L);
    assertEquals(12D, timings.savesPerHour(), 1.0E-9D);

    clock.addAndGet(1_200_000L);
    assertEquals(3D, timings.savesPerHour(), 1.0E-9D);
  }

  @Test
  void endedWithinOnlyMatchesRecentSaves() {
    save(SaveTimings.Kind.AUTOSAVE, 80L);
    clock.addAndGet(4_000L);
    assertTrue(timings.endedWithin(5_000L).isPresent());
    clock.addAndGet(2_000L);
    assertTrue(timings.endedWithin(5_000L).isEmpty());
  }
}
