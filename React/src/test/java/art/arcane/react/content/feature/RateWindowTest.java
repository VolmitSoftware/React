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

class RateWindowTest {
  @Test
  void concurrentRegionsShareOneExactLimit() throws Exception {
    RateWindow<Counter> window = new RateWindow<>(Counter.values().length);
    window.reset(1000L);
    ExecutorService executor = Executors.newFixedThreadPool(16);
    try {
      List<Callable<Boolean>> attempts = new ArrayList<>();
      for (int i = 0; i < 500; i++) {
        attempts.add(() -> window.tryAcquire(Counter.FIRST, 75, 1001L, 1000L));
      }

      int accepted = 0;
      for (Future<Boolean> result : executor.invokeAll(attempts)) {
        if (result.get()) {
          accepted++;
        }
      }

      Assertions.assertEquals(75, accepted);
    } finally {
      executor.shutdownNow();
      Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void countersAreIndependentAndResetTogether() {
    RateWindow<Counter> window = new RateWindow<>(Counter.values().length);
    window.reset(1000L);

    Assertions.assertTrue(window.tryAcquire(Counter.FIRST, 1, 1001L, 100L));
    Assertions.assertFalse(window.tryAcquire(Counter.FIRST, 1, 1002L, 100L));
    Assertions.assertTrue(window.tryAcquire(Counter.SECOND, 1, 1002L, 100L));
    Assertions.assertTrue(window.tryAcquire(Counter.FIRST, 1, 1102L, 100L));
    Assertions.assertTrue(window.tryAcquire(Counter.SECOND, 1, 1102L, 100L));
  }

  private enum Counter {
    FIRST,
    SECOND
  }
}
