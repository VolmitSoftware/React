package art.arcane.react.content.sampler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.management.GarbageCollectorMXBean;
import java.util.List;

class GCStatsTrackerTest {
  private static final long FIVE_MINUTES_MS = 300_000L;

  @BeforeEach
  void clearBefore() {
    GCStatsTracker.acquire();
    GCStatsTracker.release();
  }

  @AfterEach
  void clearAfter() {
    GCStatsTracker.acquire();
    GCStatsTracker.release();
  }

  @Test
  void concurrentCycleBeansAreExcludedFromPauseTracking() {
    GarbageCollectorMXBean minorCycles = bean("ZGC Minor Cycles", 9_000L);
    GarbageCollectorMXBean minorPauses = bean("ZGC Minor Pauses", 12L);
    GarbageCollectorMXBean shenandoahCycles = bean("Shenandoah Cycles", 4_000L);
    GarbageCollectorMXBean shenandoahPauses = bean("Shenandoah Pauses", 7L);
    GarbageCollectorMXBean g1Young = bean("G1 Young Generation", 30L);

    List<GarbageCollectorMXBean> beans = List.of(minorCycles, minorPauses, shenandoahCycles, shenandoahPauses, g1Young);

    Assertions.assertEquals(List.of(minorPauses, shenandoahPauses, g1Young), GCStatsTracker.pauseBeans(beans));
    Assertions.assertEquals(49L, GCStatsTracker.totalCollectionTimeMS(beans));
  }

  @Test
  void pausesOlderThanTheWindowAreEvictedBeforeThePercentile() {
    long now = 10_000_000L;
    GCStatsTracker.recordPause(now - FIVE_MINUTES_MS - 60_000L, 500L);
    GCStatsTracker.recordPause(now - FIVE_MINUTES_MS - 1L, 400L);
    GCStatsTracker.recordPause(now - 60_000L, 10L);
    GCStatsTracker.recordPause(now - 1_000L, 20L);

    List<Double> pauses = GCStatsTracker.snapshotPauses(now);

    Assertions.assertEquals(List.of(10D, 20D), pauses);
    Assertions.assertEquals(19.5D, SamplerMath.percentile(pauses, 0.95D), 1.0E-9D);
  }

  @Test
  void gcTimePercentIsStableAcrossPollsAndMatchesTheWindowShare() {
    long collectionMs = 0L;
    for (long uptimeMs = 1_000L; uptimeMs <= 300_000L; uptimeMs += 1_000L) {
      if (uptimeMs % 20_000L == 0L) {
        collectionMs += 60L;
      }
      double percent = GCStatsTracker.sampleGcTimePercent(uptimeMs, collectionMs);
      if (uptimeMs >= 60_000L) {
        Assertions.assertEquals(0.3D, percent, 0.0051D);
      }
    }
  }

  @Test
  void gcTimePercentStartsFromTheJvmLifetimeShare() {
    Assertions.assertEquals(0.5D, GCStatsTracker.sampleGcTimePercent(120_000L, 600L), 1.0E-9D);
    Assertions.assertEquals(1D, GCStatsTracker.sampleGcTimePercent(180_000L, 1_200L), 1.0E-9D);
  }

  private static GarbageCollectorMXBean bean(String name, long collectionTimeMs) {
    GarbageCollectorMXBean bean = Mockito.mock(GarbageCollectorMXBean.class);
    Mockito.when(bean.getName()).thenReturn(name);
    Mockito.when(bean.getCollectionTime()).thenReturn(collectionTimeMs);
    return bean;
  }
}
