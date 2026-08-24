package art.arcane.react.content.tweak;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class TweakProjectileLimiterTest {
  @Test
  void concurrentRegionsCannotExceedBurstLimit() throws Exception {
    TweakProjectileLimiter.BurstWindow window = new TweakProjectileLimiter.BurstWindow(1000L);
    ExecutorService executor = Executors.newFixedThreadPool(16);
    try {
      List<Callable<Boolean>> attempts = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        attempts.add(() -> window.increment(1000, 40, 1001L));
      }

      List<Future<Boolean>> results = executor.invokeAll(attempts);
      int allowed = 0;
      for (Future<Boolean> result : results) {
        if (result.get()) {
          allowed++;
        }
      }

      Assertions.assertEquals(40, allowed);
    } finally {
      executor.shutdownNow();
      Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void elapsedWindowResetsTheLimit() {
    TweakProjectileLimiter.BurstWindow window = new TweakProjectileLimiter.BurstWindow(1000L);

    Assertions.assertTrue(window.increment(100, 1, 1001L));
    Assertions.assertFalse(window.increment(100, 1, 1002L));
    Assertions.assertTrue(window.increment(100, 1, 1102L));
  }
}
