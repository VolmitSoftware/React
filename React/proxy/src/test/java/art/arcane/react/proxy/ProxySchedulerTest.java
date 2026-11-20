package art.arcane.react.proxy;

import art.arcane.react.core.controller.JobController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxySchedulerTest {
  private final ScheduledExecutorService main = Executors.newSingleThreadScheduledExecutor();
  private final ExecutorService async = Executors.newCachedThreadPool();

  @AfterEach
  void stop() {
    main.shutdownNow();
    async.shutdownNow();
  }

  @Test
  void laterRunsOnReactsMainThread() throws Exception {
    ProxyScheduler scheduler = new ProxyScheduler(main, async, new JobController());
    CompletableFuture<Boolean> primary = new CompletableFuture<>();

    assertTrue(scheduler.later(() -> primary.complete(scheduler.isPrimaryThread()), 1));

    assertTrue(primary.get(5, TimeUnit.SECONDS));
    assertFalse(scheduler.isPrimaryThread());
  }

  @Test
  void asyncRunsOffTheMainThreadAndStopsWhenDeactivated() throws Exception {
    ProxyScheduler scheduler = new ProxyScheduler(main, async, new JobController());
    CompletableFuture<Boolean> primary = new CompletableFuture<>();

    assertTrue(scheduler.async(() -> primary.complete(scheduler.isPrimaryThread()), 0));
    assertFalse(primary.get(5, TimeUnit.SECONDS));

    scheduler.deactivate();
    assertTrue(scheduler.isStopping());
    assertFalse(scheduler.later(() -> {
    }, 0));
    assertFalse(scheduler.region(new Object(), () -> {
    }, 0));
  }
}
