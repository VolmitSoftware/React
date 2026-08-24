package art.arcane.react.content.feature;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class FastExplosionWindowTest {
  @Test
  void concurrentPrimeIndexesAreUniqueAndContiguous() throws InterruptedException {
    FastExplosionWindow window = new FastExplosionWindow();
    int workers = 8;
    int indexesPerWorker = 250;
    Set<Integer> indexes = ConcurrentHashMap.newKeySet();
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(workers);

    try {
      for (int worker = 0; worker < workers; worker++) {
        executor.execute(() -> {
          ready.countDown();
          await(start);
          for (int index = 0; index < indexesPerWorker; index++) {
            indexes.add(window.nextPrimeIndex());
          }
        });
      }

      Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      executor.shutdown();
      Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    } finally {
      start.countDown();
      executor.shutdownNow();
    }

    int expected = workers * indexesPerWorker;
    Assertions.assertEquals(expected, indexes.size());
    for (int index = 0; index < expected; index++) {
      Assertions.assertTrue(indexes.contains(index));
    }
  }

  @Test
  void concurrentPrimeReservationsNeverExceedTheWindowMaximum() throws InterruptedException {
    FastExplosionWindow window = new FastExplosionWindow();
    int workers = 8;
    int maximum = 125;
    AtomicInteger reserved = new AtomicInteger();
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(workers);

    try {
      for (int worker = 0; worker < workers; worker++) {
        executor.execute(() -> {
          ready.countDown();
          await(start);
          for (int request = 0; request < 100; request++) {
            reserved.addAndGet(window.reservePrimes(1, maximum));
          }
        });
      }

      Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      executor.shutdown();
      Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    } finally {
      start.countDown();
      executor.shutdownNow();
    }

    Assertions.assertEquals(maximum, reserved.get());
    Assertions.assertEquals(0, window.reservePrimes(1, maximum));
  }

  @Test
  void resetClearsEveryWindowBudget() {
    FastExplosionWindow window = new FastExplosionWindow();
    Assertions.assertEquals(0, window.nextPrimeIndex());
    Assertions.assertEquals(1, window.nextPrimeIndex());
    Assertions.assertEquals(2, window.reservePrimes(2, 2));
    Assertions.assertTrue(window.tryAcquireExplosionChain(1));
    Assertions.assertFalse(window.tryAcquireExplosionChain(1));

    window.reset();

    Assertions.assertEquals(0, window.nextPrimeIndex());
    Assertions.assertEquals(2, window.reservePrimes(2, 2));
    Assertions.assertTrue(window.tryAcquireExplosionChain(1));
  }

  private void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
