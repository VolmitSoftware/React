package art.arcane.react.core.controller;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotloadTaskExecutorTest {
  private static final long TEST_TIMEOUT_SECONDS = 5L;

  @Test
  void pollRunsOnDedicatedThreadWithoutBlockingCaller() throws Exception {
    List<Throwable> failures = new CopyOnWriteArrayList<>();
    HotloadTaskExecutor executor = new HotloadTaskExecutor("hotload-test-io", failures::add);
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(1);
    AtomicReference<String> threadName = new AtomicReference<>();

    try {
      assertTrue(executor.requestPoll(() -> {
        threadName.set(Thread.currentThread().getName());
        started.countDown();
        await(release);
        finished.countDown();
      }));
      assertTrue(started.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
      assertEquals("hotload-test-io", threadName.get());
      assertEquals(1L, finished.getCount());

      release.countDown();
      assertTrue(finished.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
      assertTrue(failures.isEmpty());
    } finally {
      release.countDown();
      executor.close();
    }
  }

  @Test
  void coalescesTicksToOneTrailingPollWhileWorkIsActive() throws Exception {
    List<Throwable> failures = new CopyOnWriteArrayList<>();
    HotloadTaskExecutor executor = new HotloadTaskExecutor("hotload-test-coalesce", failures::add);
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    CountDownLatch bothFinished = new CountDownLatch(2);
    AtomicInteger polls = new AtomicInteger();
    Runnable poll = () -> {
      int invocation = polls.incrementAndGet();
      if (invocation == 1) {
        firstStarted.countDown();
        await(releaseFirst);
      }
      bothFinished.countDown();
    };

    try {
      assertTrue(executor.requestPoll(poll));
      assertTrue(firstStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
      assertTrue(executor.requestPoll(poll));
      assertFalse(executor.requestPoll(poll));

      releaseFirst.countDown();
      assertTrue(bothFinished.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
      assertEquals(2, polls.get());
      assertTrue(failures.isEmpty());
    } finally {
      releaseFirst.countDown();
      executor.close();
    }
  }

  @Test
  void retirementRejectsNewWorkAndCleansUpAfterActiveWork() throws Exception {
    List<Throwable> failures = new CopyOnWriteArrayList<>();
    HotloadTaskExecutor executor = new HotloadTaskExecutor("hotload-test-retire", failures::add);
    CountDownLatch activeStarted = new CountDownLatch(1);
    CountDownLatch releaseActive = new CountDownLatch(1);
    CountDownLatch cleanupFinished = new CountDownLatch(1);
    List<String> order = new CopyOnWriteArrayList<>();

    assertTrue(executor.execute(() -> {
      activeStarted.countDown();
      await(releaseActive);
      order.add("active");
    }));
    assertTrue(activeStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));

    executor.retire(() -> {
      order.add("cleanup");
      cleanupFinished.countDown();
    });
    assertFalse(executor.requestPoll(() -> order.add("stale-poll")));
    assertFalse(executor.execute(() -> order.add("stale-task")));

    releaseActive.countDown();
    assertTrue(cleanupFinished.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    assertEquals(List.of("active", "cleanup"), order);
    assertTrue(failures.isEmpty());
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
