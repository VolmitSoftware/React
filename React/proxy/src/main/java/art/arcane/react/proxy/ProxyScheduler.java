package art.arcane.react.proxy;

import art.arcane.react.core.controller.JobController;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactWorld;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A proxy has no server thread, so React keeps one of its own: global, region and entity work all run there, a tick
 * apart, as on a mod server.
 */
final class ProxyScheduler implements PlatformScheduler {
  private static final long TICK_MS = 50L;
  private final ScheduledExecutorService main;
  private final ExecutorService async;
  private final JobController jobs;
  private volatile Thread mainThread;
  private volatile boolean active = true;
  private volatile boolean ready;

  ProxyScheduler(ScheduledExecutorService main, ExecutorService async, JobController jobs) {
    this.main = main;
    this.async = async;
    this.jobs = jobs;
    main.execute(() -> mainThread = Thread.currentThread());
  }

  void markReady() {
    ready = true;
  }

  void deactivate() {
    active = false;
    ready = false;
  }

  @Override
  public boolean isPrimaryThread() {
    return Thread.currentThread() == mainThread;
  }

  @Override
  public boolean isRegionized() {
    return false;
  }

  @Override
  public boolean isStopping() {
    return !active;
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public boolean isReady() {
    return ready;
  }

  @Override
  public void queue(Runnable task) {
    jobs.queue(task);
  }

  @Override
  public boolean isOwnedByCurrentRegion(Object handle) {
    return isPrimaryThread();
  }

  @Override
  public boolean isPlaced(Object location) {
    return false;
  }

  @Override
  public boolean global(Runnable task, int delayTicks) {
    return later(task, delayTicks);
  }

  @Override
  public boolean region(ReactWorld world, int chunkX, int chunkZ, Runnable task, int delayTicks) {
    return false;
  }

  @Override
  public boolean region(Object location, Runnable task, int delayTicks) {
    return false;
  }

  @Override
  public boolean entity(Object entity, Runnable task, int delayTicks, Runnable retired) {
    return false;
  }

  @Override
  public boolean async(Runnable task, int delayTicks) {
    try {
      if (delayTicks > 0) {
        main.schedule(() -> submit(task), delayTicks * TICK_MS, TimeUnit.MILLISECONDS);
        return true;
      }
      return submit(task);
    } catch (RejectedExecutionException stopped) {
      return false;
    }
  }

  private boolean submit(Runnable task) {
    try {
      async.execute(task);
      return true;
    } catch (RejectedExecutionException stopped) {
      return false;
    }
  }

  @Override
  public boolean later(Runnable task, int delayTicks) {
    if (!active) {
      return false;
    }
    try {
      main.schedule(task, Math.max(0, delayTicks) * TICK_MS, TimeUnit.MILLISECONDS);
      return true;
    } catch (RejectedExecutionException stopped) {
      return false;
    }
  }

  @Override
  public void cancelAll() {
  }
}
