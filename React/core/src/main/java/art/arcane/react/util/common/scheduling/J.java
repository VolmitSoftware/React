/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.react.util.common.scheduling;

import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.volmlib.util.function.NastyFunction;
import art.arcane.volmlib.util.function.NastyFuture;
import art.arcane.volmlib.util.function.NastyRunnable;
import art.arcane.volmlib.util.math.FinalInteger;
import art.arcane.volmlib.util.scheduling.AR;
import art.arcane.volmlib.util.scheduling.JSupport;
import art.arcane.volmlib.util.scheduling.SchedulerBridge;
import art.arcane.volmlib.util.scheduling.SR;
import art.arcane.volmlib.util.scheduling.StartupQueueSupport;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * React's scheduler facade. Methods that take {@code Object} handles accept the platform's own entity, world or
 * location objects and hand them to the bound {@link PlatformScheduler}.
 */
public class J {
  private static final long TICK_MS = 50L;
  private static final AtomicInteger TASK_IDS = new AtomicInteger(1);
  private static final Map<Integer, Runnable> REPEATING_CANCELLERS = new ConcurrentHashMap<>();
  private static final StartupQueueSupport STARTUP_QUEUE = new StartupQueueSupport();
  private static final Logger FALLBACK_LOG = Logger.getLogger("React");

  static {
    SchedulerBridge.setSyncScheduler(J::s);
    SchedulerBridge.setDelayedSyncScheduler(J::s);
    SchedulerBridge.setAsyncScheduler(J::a);
    SchedulerBridge.setDelayedAsyncScheduler(J::a);
    SchedulerBridge.setSyncRepeatingScheduler(J::sr);
    SchedulerBridge.setAsyncRepeatingScheduler(J::ar);
    SchedulerBridge.setCancelScheduler(J::car);
    SchedulerBridge.setErrorHandler(e -> reportError("Scheduler bridge task failed", e));
    SchedulerBridge.setInfoLogger(J::verbose);
  }

  public static void dofor(int a, Function<Integer, Boolean> c, int ch, Consumer<Integer> d) {
    JSupport.dofor(a, c, ch, d);
  }

  public static boolean doif(Supplier<Boolean> c, Runnable g) {
    return JSupport.doif(c, g, null);
  }

  public static void a(Runnable a) {
    asyncExecutor().execute(a);
  }

  public static <T> Future<T> a(Callable<T> a) {
    return asyncExecutor().submit(a);
  }

  public static void attemptAsync(NastyRunnable r) {
    JSupport.attemptAsync(r::run, J::a);
  }

  public static <R> R attemptResult(NastyFuture<R> r, R onError) {
    return JSupport.attemptResult(r::run, onError, Throwable::printStackTrace);
  }

  public static <T, R> R attemptFunction(NastyFunction<T, R> r, T param, R onError) {
    return JSupport.attemptFunction(r::run, param, onError, null);
  }

  public static boolean sleep(long ms) {
    return JSupport.sleep(ms);
  }

  public static boolean attempt(NastyRunnable r) {
    return JSupport.attempt(r::run);
  }

  public static Throwable attemptCatch(NastyRunnable r) {
    return JSupport.attemptCatch(r::run);
  }

  public static <T> T attempt(Supplier<T> t, T i) {
    return JSupport.attempt(t::get, i, null);
  }

  public static void executeAfterStartupQueue() {
    JSupport.executeAfterStartupQueue(STARTUP_QUEUE, J::s, J::a);
  }

  public static void ass(Runnable r) {
    JSupport.enqueueAfterStartupSync(STARTUP_QUEUE, r, J::s);
  }

  public static void asa(Runnable r) {
    JSupport.enqueueAfterStartupAsync(STARTUP_QUEUE, r, J::a);
  }

  public static boolean isPrimaryThread() {
    PlatformScheduler scheduler = scheduler();
    return scheduler != null && scheduler.isPrimaryThread();
  }

  /**
   * True once the server has begun stopping. Folia halts its region scheduler before it disables
   * plugins, and Paper stops ticking, so work queued on a server scheduler from then on never runs.
   */
  public static boolean isServerStopping() {
    PlatformScheduler scheduler = scheduler();
    return scheduler != null && scheduler.isStopping();
  }

  public static boolean isFoliaThreading() {
    PlatformScheduler scheduler = scheduler();
    return scheduler != null && scheduler.isRegionized();
  }

  public static boolean isOwnedByCurrentRegion(Object handle) {
    PlatformScheduler scheduler = scheduler();
    return scheduler != null && scheduler.isOwnedByCurrentRegion(handle);
  }

  public static boolean runEntity(Object entity, Runnable runnable) {
    return runEntity(entity, runnable, 0);
  }

  public static boolean runEntity(Object entity, Runnable runnable, int delayTicks) {
    return runEntity(entity, runnable, delayTicks, null);
  }

  public static boolean runEntity(Object entity, Runnable runnable, int delayTicks, Runnable retired) {
    if (entity == null || runnable == null || !isPluginActive()) {
      return false;
    }

    Runnable scheduledRunnable = runnable;
    Runnable scheduledRetired = retired;
    if (retired != null) {
      AtomicBoolean terminal = new AtomicBoolean(false);
      scheduledRunnable = () -> {
        if (terminal.compareAndSet(false, true)) {
          runnable.run();
        }
      };
      scheduledRetired = () -> {
        if (terminal.compareAndSet(false, true)) {
          retired.run();
        }
      };
    }

    return scheduler().entity(entity, scheduledRunnable, Math.max(0, delayTicks), scheduledRetired);
  }

  public static <T> T runChunkResult(Object world, int chunkX, int chunkZ, Supplier<T> supplier, T fallback) {
    if (world == null) {
      return fallback;
    }

    ReactWorld handle = world(world);
    PlatformScheduler scheduler = scheduler();
    return regionResult(
        () -> handle != null,
        () -> scheduler != null && scheduler.isOwnedByCurrentRegion(handle, chunkX, chunkZ),
        task -> scheduler != null && scheduler.region(handle, chunkX, chunkZ, task, 0),
        supplier,
        fallback
    );
  }

  public static boolean runChunk(Object world, int chunkX, int chunkZ, Runnable runnable) {
    return runChunk(world, chunkX, chunkZ, runnable, 0);
  }

  public static boolean runChunk(Object world, int chunkX, int chunkZ, Runnable runnable, int delayTicks) {
    if (world == null || runnable == null || !isPluginActive()) {
      return false;
    }

    int safeDelay = Math.max(0, delayTicks);
    ReactWorld handle = world(world);
    if (handle != null && scheduler().region(handle, chunkX, chunkZ, runnable, safeDelay)) {
      return true;
    }

    if (isFoliaThreading()) {
      return false;
    }

    if (safeDelay <= 0) {
      sync(runnable);
    } else {
      sync(runnable, safeDelay);
    }

    return true;
  }

  public static <T> T runRegionResult(Object location, Supplier<T> supplier, T fallback) {
    PlatformScheduler scheduler = scheduler();
    return regionResult(
        () -> scheduler != null && scheduler.isPlaced(location),
        () -> isOwnedByCurrentRegion(location),
        task -> scheduler != null && scheduler.region(location, task, 0),
        supplier,
        fallback
    );
  }

  private static <T> T regionResult(
      BooleanSupplier placed,
      BooleanSupplier owned,
      Predicate<Runnable> submit,
      Supplier<T> supplier,
      T fallback
  ) {
    if (supplier == null) {
      return fallback;
    }

    if (!isFoliaThreading()) {
      try {
        return sResult(supplier);
      } catch (Throwable throwable) {
        reportError(throwable);
        return fallback;
      }
    }

    if (!placed.getAsBoolean()) {
      return fallback;
    }

    if (owned.getAsBoolean()) {
      try {
        return supplier.get();
      } catch (Throwable throwable) {
        reportError(throwable);
        return fallback;
      }
    }

    AtomicReference<T> result = new AtomicReference<>(fallback);
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    if (!submit.test(() -> {
      try {
        result.set(supplier.get());
      } catch (Throwable throwable) {
        error.set(throwable);
      } finally {
        latch.countDown();
      }
    })) {
      return fallback;
    }

    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        return fallback;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return fallback;
    }

    Throwable throwable = error.get();
    if (throwable != null) {
      reportError(throwable);
      return fallback;
    }

    return result.get();
  }

  public static boolean isThreadOwnershipViolation(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      String message = current.getMessage();
      if (message != null) {
        if (message.contains("Thread failed main thread check")
            || message.contains("owning region's thread")
            || message.contains("current thread is not owner")) {
          return true;
        }
      }

      current = current.getCause();
    }

    return false;
  }

  public static void cancelPluginTasks() {
    PlatformScheduler scheduler = scheduler();
    if (scheduler == null) {
      return;
    }

    for (Runnable cancelAction : REPEATING_CANCELLERS.values()) {
      try {
        cancelAction.run();
      } catch (Throwable ex) {
        verbose("Failed to run cancel action: " + ex.getClass().getSimpleName()
            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      }
    }
    REPEATING_CANCELLERS.clear();

    scheduler.cancelAll();
  }

  public static <T> T sResult(Supplier<T> t) {
    if (isPrimaryThread()) {
      return t.get();
    }

    AtomicBoolean finished = new AtomicBoolean(false);
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    J.s(() -> {
      try {
        result.set(t.get());
      } catch (Throwable throwable) {
        failure.set(throwable);
      } finally {
        finished.set(true);
      }
    });

    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (!finished.get()) {
      PlatformScheduler scheduler = scheduler();
      if (scheduler == null || !scheduler.isReady()) {
        return result.get();
      }
      if (System.nanoTime() >= deadline) {
        warn("J.sResult timed out after 15s waiting for sync task; returning latest result (likely deadlock or shutdown).");
        return result.get();
      }

      if (!J.sleep(15)) {
        Thread.currentThread().interrupt();
        return result.get();
      }
    }

    Throwable throwable = failure.get();
    if (throwable != null) {
      reportError(throwable);
      return null;
    }
    return result.get();
  }

  public static void s(Runnable r) {
    if (!isPluginActive()) {
      return;
    }

    scheduler().queue(r);
  }

  public static void s(Runnable r, int delay) {
    if (delay <= 0) {
      s(r);
      return;
    }

    if (!isPluginActive()) {
      return;
    }

    Runnable delayed = () -> {
      if (!isPluginActive()) {
        return;
      }

      scheduler().queue(r);
    };

    if (isFoliaThreading()) {
      retryDelayedSyncOnUnsupported(delayed, delay);
      return;
    }

    if (runGlobalDelayed(delayed, delay)) {
      return;
    }

    scheduleLater(delayed, delay, "Failed to schedule delayed sync task while plugin is enabled.");
  }

  public static void sync(Runnable r, int delay) {
    if (r == null || !isPluginActive()) {
      return;
    }

    if (delay <= 0) {
      if (isPrimaryThread()) {
        r.run();
        return;
      }

      if (isFoliaThreading()) {
        retrySyncOnUnsupported(r);
        return;
      }

      if (!runGlobalImmediate(r)) {
        scheduleLater(r, 0, "Failed to schedule sync task while plugin is enabled.");
      }
      return;
    }

    if (isFoliaThreading()) {
      retryDelayedSyncOnUnsupported(r, delay);
      return;
    }

    if (runGlobalDelayed(r, delay)) {
      return;
    }

    scheduleLater(r, delay, "Failed to schedule delayed sync task while plugin is enabled.");
  }

  public static void s(Object location, Runnable r, int delay) {
    if (location == null) {
      sync(r, delay);
      return;
    }

    int safeDelay = Math.max(0, delay);
    PlatformScheduler scheduler = scheduler();
    if (scheduler != null && scheduler.region(location, r, safeDelay)) {
      return;
    }

    if (isFoliaThreading()) {
      throw new IllegalStateException("Failed to schedule region task on Folia.");
    }

    sync(r, safeDelay);
  }

  public static void s(Object location, Runnable r) {
    s(location, r, 0);
  }

  public static void sync(Runnable r) {
    sync(r, 0);
  }

  public static void csr(int id) {
    cancelRepeatingTask(id);
  }

  public static int sr(Runnable r, int interval) {
    int safeInterval = Math.max(1, interval);
    RepeatingState state = new RepeatingState();
    int taskId = trackRepeatingTask(() -> state.cancelled = true);

    Runnable[] loop = new Runnable[1];
    loop[0] = () -> {
      if (state.cancelled || !isPluginActive()) {
        REPEATING_CANCELLERS.remove(taskId);
        return;
      }

      try {
        r.run();
      } catch (Throwable e) {
        reportError(e);
      }

      if (state.cancelled || !isPluginActive()) {
        REPEATING_CANCELLERS.remove(taskId);
        return;
      }

      sync(loop[0], safeInterval);
    };

    sync(loop[0], 0);
    return taskId;
  }

  public static void sr(Runnable r, int interval, int intervals) {
    FinalInteger fi = new FinalInteger(0);

    new SR(interval) {
      @Override
      public void run() {
        fi.add(1);
        r.run();

        if (fi.get() >= intervals) {
          cancel();
        }
      }
    };
  }

  public static void a(Runnable r, int delay) {
    if (!isPluginActive()) {
      return;
    }

    if (delay <= 0) {
      if (!scheduler().async(r, 0)) {
        a(r);
      }
      return;
    }

    if (!scheduler().async(r, delay)) {
      a(() -> {
        if (sleep(ticksToMilliseconds(delay))) {
          r.run();
        }
      });
    }
  }

  public static void car(int id) {
    cancelRepeatingTask(id);
  }

  public static int ar(Runnable r, int interval) {
    int safeInterval = Math.max(1, interval);
    RepeatingState state = new RepeatingState();
    int taskId = trackRepeatingTask(() -> state.cancelled = true);

    Runnable[] loop = new Runnable[1];
    loop[0] = () -> {
      if (state.cancelled || !isPluginActive()) {
        REPEATING_CANCELLERS.remove(taskId);
        return;
      }

      try {
        r.run();
      } catch (Throwable e) {
        reportError(e);
      }

      if (state.cancelled || !isPluginActive()) {
        REPEATING_CANCELLERS.remove(taskId);
        return;
      }

      a(loop[0], safeInterval);
    };

    a(loop[0], 0);
    return taskId;
  }

  public static void ar(Runnable r, int interval, int intervals) {
    FinalInteger fi = new FinalInteger(0);

    new AR(interval) {
      @Override
      public void run() {
        fi.add(1);
        r.run();

        if (fi.get() >= intervals) {
          cancel();
        }
      }
    };
  }

  private static int trackRepeatingTask(Runnable cancelAction) {
    int id = TASK_IDS.getAndIncrement();
    REPEATING_CANCELLERS.put(id, cancelAction);
    return id;
  }

  private static void scheduleLater(Runnable runnable, int delayTicks, String failure) {
    boolean scheduled;
    try {
      scheduled = scheduler().later(runnable, delayTicks);
    } catch (UnsupportedOperationException e) {
      scheduler().forceRegionized();
      if (delayTicks <= 0) {
        retrySyncOnUnsupported(runnable);
      } else {
        retryDelayedSyncOnUnsupported(runnable, delayTicks);
      }
      return;
    }

    if (!scheduled && isPluginActive()) {
      throw new IllegalStateException(failure);
    }
  }

  private static void retrySyncOnUnsupported(Runnable runnable) {
    if (runGlobalImmediate(runnable)) {
      return;
    }

    if (isPrimaryThread()) {
      runnable.run();
      return;
    }

    a(() -> {
      if (sleep(TICK_MS)) {
        sync(runnable, 0);
      }
    });
  }

  private static void retryDelayedSyncOnUnsupported(Runnable runnable, int delayTicks) {
    if (runGlobalDelayed(runnable, delayTicks)) {
      return;
    }

    a(() -> {
      if (sleep(ticksToMilliseconds(delayTicks))) {
        sync(runnable, 0);
      }
    });
  }

  private static void cancelRepeatingTask(int id) {
    Runnable cancelAction = REPEATING_CANCELLERS.remove(id);
    if (cancelAction != null) {
      cancelAction.run();
    }
  }

  private static long ticksToMilliseconds(int ticks) {
    return Math.max(0L, ticks) * TICK_MS;
  }

  private static boolean runGlobalImmediate(Runnable runnable) {
    if (isFoliaThreading() && isPrimaryThread()) {
      runnable.run();
      return true;
    }

    PlatformScheduler scheduler = scheduler();
    return scheduler != null && scheduler.global(runnable, 0);
  }

  private static boolean runGlobalDelayed(Runnable runnable, int delayTicks) {
    if (delayTicks <= 0) {
      return runGlobalImmediate(runnable);
    }

    PlatformScheduler scheduler = scheduler();
    return scheduler != null && scheduler.global(runnable, Math.max(0, delayTicks));
  }

  private static boolean isPluginActive() {
    PlatformScheduler scheduler = scheduler();
    return scheduler != null && scheduler.isActive();
  }

  private static PlatformScheduler scheduler() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? null : platform.scheduler();
  }

  private static ReactWorld world(Object world) {
    if (world instanceof ReactWorld handle) {
      return handle;
    }

    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? null : platform.world(world).orElse(null);
  }

  private static ExecutorService asyncExecutor() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? ForkJoinPool.commonPool() : platform.asyncExecutor();
  }

  private static void verbose(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.verbose(message);
    }
  }

  private static void warn(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.warn(message);
    }
  }

  private static void reportError(Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(failure);
    } else {
      FALLBACK_LOG.log(Level.SEVERE, "React task failed with no platform bound", failure);
    }
  }

  private static void reportError(String context, Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(context, failure);
    } else {
      FALLBACK_LOG.log(Level.SEVERE, context, failure);
    }
  }

  private static final class RepeatingState {
    private volatile boolean cancelled;
  }
}
