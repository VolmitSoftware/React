package art.arcane.react.core.controller;

import art.arcane.react.core.history.HistoryQueryEngine;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryQueryConcurrencyTest {
  @Test
  void diskQueryDoesNotHoldTheLiveSegmentLock() throws Exception {
    HistoryController history = new HistoryController();
    HistoryQueryEngine engine = Mockito.mock(HistoryQueryEngine.class);
    history.setQueryEngine(engine);
    CountDownLatch reading = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Mockito.when(engine.query(List.of("tps"), 0L, 1_000L, 1_000L, 1L, 1_000L)).thenAnswer(invocation -> {
      reading.countDown();
      assertTrue(release.await(5L, TimeUnit.SECONDS));
      return null;
    });
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> query = executor.submit(() -> history.query(List.of("tps"), 0L, 1_000L, 1_000L, 1L, 1_000L));
      try {
        assertTrue(reading.await(5L, TimeUnit.SECONDS));
        Future<Boolean> writer = executor.submit(() -> {
          synchronized (history.getActiveSegmentLock()) {
            return true;
          }
        });
        assertTrue(writer.get(1L, TimeUnit.SECONDS));
      } finally {
        release.countDown();
      }
      query.get(5L, TimeUnit.SECONDS);
    }
  }
}
