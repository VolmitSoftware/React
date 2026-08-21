package art.arcane.react.util.common.scheduling;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TickerCloseLifecycleTest {
  @Test
  void closeWaitsForAnActiveTickToFinish() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(1);
    Ticked ticked = mock(Ticked.class);
    when(ticked.shouldTick()).thenReturn(true);
    when(ticked.getTid()).thenReturn("blocking");
    doAnswer(invocation -> {
      started.countDown();
      while (release.getCount() > 0L) {
        try {
          release.await();
        } catch (InterruptedException ignored) {
        }
      }
      finished.countDown();
      return null;
    }).when(ticked).tick();

    Ticker ticker = new Ticker();
    CompletableFuture<Void> closing = null;
    try {
      ticker.register(ticked);
      assertTrue(started.await(2, TimeUnit.SECONDS));

      closing = CompletableFuture.runAsync(ticker::close);
      CompletableFuture<Void> activeClose = closing;
      assertThrows(TimeoutException.class, () -> activeClose.get(100, TimeUnit.MILLISECONDS));

      release.countDown();
      closing.get(2, TimeUnit.SECONDS);
      assertEquals(0L, finished.getCount());
    } finally {
      release.countDown();
      if (closing == null || !closing.isDone()) {
        ticker.close();
      }
    }
  }
}
