package art.arcane.react.util.common.scheduling;

import art.arcane.react.React;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactExecutorsTest {
  private ReactExecutors previous;

  @BeforeEach
  void saveRuntime() {
    previous = React.executors;
  }

  @AfterEach
  void restoreRuntime() {
    React.executors = previous;
  }

  @Test
  void tickAndAsyncPoolsUseReactNamedDaemonThreads() throws Exception {
    ReactExecutors executors = ReactExecutors.create();
    try {
      Thread tickThread = executors.tickPool().submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
      Thread asyncThread = executors.asyncPool().submit(Thread::currentThread).get(5, TimeUnit.SECONDS);

      assertTrue(tickThread.getName().startsWith("react-tick-"), tickThread.getName());
      assertTrue(tickThread.isDaemon());
      assertEquals(Thread.MIN_PRIORITY, tickThread.getPriority());
      assertTrue(asyncThread.getName().startsWith("react-async-"), asyncThread.getName());
      assertTrue(asyncThread.isDaemon());
      assertEquals(Thread.MIN_PRIORITY, asyncThread.getPriority());
    } finally {
      executors.close();
    }

    assertTrue(executors.isClosed());
    assertTrue(executors.tickPool().isShutdown());
    assertTrue(executors.asyncPool().isShutdown());
  }

  @Test
  void asyncSchedulerRunsOnTheLiveRuntimePool() throws Exception {
    ReactExecutors executors = ReactExecutors.create();
    React.executors = executors;
    try {
      Future<String> name = J.a(() -> Thread.currentThread().getName());

      assertTrue(name.get(5, TimeUnit.SECONDS).startsWith("react-async-"));
    } finally {
      executors.close();
    }
  }

  @Test
  void asyncSchedulerKeepsWorkingWithoutALiveRuntime() throws Exception {
    React.executors = null;
    Thread caller = Thread.currentThread();

    Thread worker = J.a(Thread::currentThread).get(5, TimeUnit.SECONDS);

    assertNotSame(caller, worker);
    assertTrue(worker.getName().startsWith("react-async-"), worker.getName());
  }

  @Test
  void asyncSchedulerKeepsWorkingAfterTheRuntimeCloses() throws Exception {
    ReactExecutors executors = ReactExecutors.create();
    React.executors = executors;
    Thread first = J.a(Thread::currentThread).get(5, TimeUnit.SECONDS);
    executors.close();

    Thread restarted = J.a(Thread::currentThread).get(5, TimeUnit.SECONDS);

    assertNotSame(Thread.currentThread(), restarted);
    assertNotSame(first, restarted);
    assertTrue(restarted.getName().startsWith("react-async-"), restarted.getName());
  }

  @Test
  void asyncPoolGrowsPastCoreThreadsWhileWorkBlocks() throws Exception {
    ReactExecutors executors = ReactExecutors.create();
    int blockers = Runtime.getRuntime().availableProcessors() + 1;
    CountDownLatch release = new CountDownLatch(1);
    List<Future<?>> blocked = new ArrayList<>(blockers);
    try {
      for (int i = 0; i < blockers; i++) {
        blocked.add(executors.asyncPool().submit(() -> awaitQuietly(release)));
      }

      Future<Thread> probe = executors.asyncPool().submit(Thread::currentThread);

      assertTrue(probe.get(2, TimeUnit.SECONDS).getName().startsWith("react-async-"));
    } finally {
      release.countDown();
      for (Future<?> future : blocked) {
        future.get(5, TimeUnit.SECONDS);
      }
      executors.close();
    }
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
