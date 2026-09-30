package art.arcane.react.content.feature;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FeatureCropFastForwardActivityTest {
  private static final long PASS_MS = 2_500L;
  private static final long WARM_TICKS = 200L;
  private static final long MAX_TICKS = 24_000L;

  @Test
  void chunkContinuouslyInsideSimulationDistanceNeverFastForwards() {
    CropActivityLedger ledger = new CropActivityLedger();
    long previousPass = 0L;
    for (long now = 1_000L; now <= 3_600_000L; now += PASS_MS) {
      ledger.stampInside(0, 0, 3, now, previousPass, WARM_TICKS, MAX_TICKS);
      previousPass = now;
    }

    for (int x = -3; x <= 3; x++) {
      for (int z = -3; z <= 3; z++) {
        Assertions.assertEquals(0L, ledger.takePending(CropActivityLedger.pack(x, z)));
      }
    }
  }

  @Test
  void chunkThatLeavesAndReturnsAccruesOnlyItsTimeOutside() {
    CropActivityLedger ledger = new CropActivityLedger();
    long previousPass = 0L;
    long now = 1_000L;
    for (; now <= 11_000L; now += PASS_MS) {
      ledger.stampInside(0, 0, 1, now, previousPass, WARM_TICKS, MAX_TICKS);
      previousPass = now;
    }
    long lastInside = previousPass;
    for (; now <= 61_000L; now += PASS_MS) {
      ledger.stampInside(40, 40, 1, now, previousPass, WARM_TICKS, MAX_TICKS);
      previousPass = now;
    }
    long lastOutsidePass = previousPass;

    ledger.stampInside(0, 0, 1, now, previousPass, WARM_TICKS, MAX_TICKS);

    Assertions.assertEquals((lastOutsidePass - lastInside) / 50L, ledger.takePending(CropActivityLedger.pack(0, 0)));
    Assertions.assertEquals(0L, ledger.takePending(CropActivityLedger.pack(0, 0)));
  }

  @Test
  void briefAbsenceBelowTheWarmThresholdIsIgnored() {
    CropActivityLedger ledger = new CropActivityLedger();
    ledger.stampInside(0, 0, 0, 1_000L, 0L, WARM_TICKS, MAX_TICKS);
    ledger.stampInside(9, 9, 0, 3_500L, 1_000L, WARM_TICKS, MAX_TICKS);
    ledger.stampInside(9, 9, 0, 6_000L, 3_500L, WARM_TICKS, MAX_TICKS);

    ledger.stampInside(0, 0, 0, 8_500L, 6_000L, WARM_TICKS, MAX_TICKS);

    Assertions.assertEquals(0L, ledger.takePending(CropActivityLedger.pack(0, 0)));
  }

  @Test
  void repeatedAbsencesAccumulateUpToTheCap() {
    CropActivityLedger ledger = new CropActivityLedger();
    ledger.stampInside(0, 0, 0, 1_000L, 0L, WARM_TICKS, 1_500L);
    ledger.stampInside(0, 0, 0, 62_000L, 61_000L, WARM_TICKS, 1_500L);
    Assertions.assertEquals(1, ledger.pendingChunks(16).length);

    ledger.stampInside(0, 0, 0, 123_000L, 122_000L, WARM_TICKS, 1_500L);

    Assertions.assertEquals(1_500L, ledger.takePending(CropActivityLedger.pack(0, 0)));
  }

  @Test
  void loadedChunksAreDormantFromTheirLoadTime() {
    CropActivityLedger ledger = new CropActivityLedger();
    ledger.loaded(CropActivityLedger.pack(5, 5), 1_000L);

    ledger.stampInside(5, 5, 0, 31_000L, 28_500L, WARM_TICKS, MAX_TICKS);

    Assertions.assertEquals((28_500L - 1_000L) / 50L, ledger.takePending(CropActivityLedger.pack(5, 5)));
  }

  @Test
  void reloadAndUnloadDiscardPendingGrowth() {
    CropActivityLedger ledger = new CropActivityLedger();
    long chunk = CropActivityLedger.pack(2, -3);
    ledger.loaded(chunk, 1_000L);
    ledger.stampInside(2, -3, 0, 61_000L, 58_500L, WARM_TICKS, MAX_TICKS);
    ledger.loaded(chunk, 62_000L);
    Assertions.assertEquals(0L, ledger.takePending(chunk));

    ledger.stampInside(2, -3, 0, 122_000L, 119_500L, WARM_TICKS, MAX_TICKS);
    ledger.unloaded(chunk);

    Assertions.assertEquals(0L, ledger.takePending(chunk));
    Assertions.assertEquals(0, ledger.size());
  }

  @Test
  void seedingKeepsAnExistingBaseline() {
    CropActivityLedger ledger = new CropActivityLedger();
    long chunk = CropActivityLedger.pack(1, 1);
    ledger.seed(chunk, 1_000L);
    ledger.seed(chunk, 50_000L);

    ledger.stampInside(1, 1, 0, 61_000L, 58_500L, WARM_TICKS, MAX_TICKS);

    Assertions.assertEquals((58_500L - 1_000L) / 50L, ledger.takePending(chunk));
  }
}
