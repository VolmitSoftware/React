package art.arcane.react.content.feature;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class FarmBurstStateTest {
  @Test
  void smoothingBeginsAtTheConfiguredConcurrentThreshold() throws Exception {
    FeatureFarmBurstSmoother.BurstState state = new FeatureFarmBurstSmoother.BurstState();
    state.reset(1000L);
    ExecutorService executor = Executors.newFixedThreadPool(16);
    try {
      List<Callable<Boolean>> events = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        events.add(() -> state.record(1001L, 1200, 72));
      }

      int smoothed = 0;
      for (Future<Boolean> result : executor.invokeAll(events)) {
        if (result.get()) {
          smoothed++;
        }
      }

      Assertions.assertEquals(29, smoothed);
    } finally {
      executor.shutdownNow();
      Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void isolatedGrowthDoesNotEnterSmoothing() {
    FeatureFarmBurstSmoother.BurstState state = new FeatureFarmBurstSmoother.BurstState();
    state.reset(1000L);

    Assertions.assertFalse(state.record(1001L, 1200, 72));
    Assertions.assertFalse(state.record(2202L, 1200, 72));
  }
}
