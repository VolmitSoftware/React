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

import art.arcane.curse.Curse;
import art.arcane.multiburst.MultiBurst;
import art.arcane.react.React;
import art.arcane.react.core.controller.JobController;
import art.arcane.volmlib.util.function.NastyFunction;
import art.arcane.volmlib.util.function.NastyFuture;
import art.arcane.volmlib.util.function.NastyRunnable;
import art.arcane.volmlib.util.math.FinalInteger;
import art.arcane.volmlib.util.scheduling.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class J {
  private static final long TICK_MS = 50L;
  private static final AtomicInteger TASK_IDS = new AtomicInteger(1);
  private static final Map<Integer, Runnable> REPEATING_CANCELLERS = new java.util.concurrent.ConcurrentHashMap<>();
  private static final StartupQueueSupport STARTUP_QUEUE = new StartupQueueSupport();

  static {
    SchedulerBridge.setSyncScheduler(J::s);
    SchedulerBridge.setDelayedSyncScheduler(J::s);
    SchedulerBridge.setAsyncScheduler(J::a);
    SchedulerBridge.setDelayedAsyncScheduler(J::a);
    SchedulerBridge.setSyncRepeatingScheduler(J::sr);
    SchedulerBridge.setAsyncRepeatingScheduler(J::ar);
    SchedulerBridge.setCancelScheduler(J::car);
    SchedulerBridge.setErrorHandler(e -> {
      React.reportError(e);
      e.printStackTrace();
    });
    SchedulerBridge.setInfoLogger(React::info);
  }

  public static void dofor(int a, Function<Integer, Boolean> c, int ch, Consumer<Integer> d) {
    JSupport.dofor(a, c, ch, d);
  }

  public static boolean doif(Supplier<Boolean> c, Runnable g) {
    return JSupport.doif(c, g, null);
  }

  public static void a(Runnable a) {
    MultiBurst.burst.lazy(a);
  }

  public static <T> Future<T> a(Callable<T> a) {
    return ((ExecutorService) Curse.on(MultiBurst.burst).get("service")).submit(a);
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
    return FoliaScheduler.isPrimaryThread();
  }

  public static boolean isFoliaThreading() {
    return FoliaScheduler.isFoliaThreading(Bukkit.getServer());
  }

  public static boolean isOwnedByCurrentRegion(Entity entity) {
    return FoliaScheduler.isOwnedByCurrentRegion(entity);
  }

  public static boolean isOwnedByCurrentRegion(Location location) {
    return FoliaScheduler.isOwnedByCurrentRegion(location);
  }

  public static boolean runEntity(Entity entity, Runnable runnable) {
    return runEntity(entity, runnable, 0);
  }

  public static boolean runEntity(Entity entity, Runnable runnable, int delayTicks) {
    if (entity == null || runnable == null || !isPluginActive()) {
      return false;
    }

    int safeDelayTicks = Math.max(0, delayTicks);
    if (!isFoliaThreading()) {
      if (safeDelayTicks <= 0) {
        sync(runnable);
      } else {
        sync(runnable, safeDelayTicks);
      }
      return true;
    }

    if (safeDelayTicks <= 0 && isOwnedByCurrentRegion(entity)) {
      runnable.run();
      return true;
    }

    if (FoliaScheduler.runEntity(React.instance, entity, runnable, safeDelayTicks)) {
      return true;
    }

    Location location = safeEntityLocation(entity);
    return location != null && runRegion(location, runnable, safeDelayTicks);
  }

  public static <T> T runChunkResult(World world, int chunkX, int chunkZ, Supplier<T> supplier, T fallback) {
    if (world == null) {
      return fallback;
    }

    Location anchor = new Location(world, (chunkX << 4) + 8.0, 64.0, (chunkZ << 4) + 8.0);
    return runRegionResult(anchor, supplier, fallback);
  }

  public static boolean runChunk(World world, int chunkX, int chunkZ, Runnable runnable) {
    return runChunk(world, chunkX, chunkZ, runnable, 0);
  }

  public static boolean runChunk(World world, int chunkX, int chunkZ, Runnable runnable, int delayTicks) {
    if (world == null || runnable == null || !isPluginActive()) {
      return false;
    }

    int safeDelay = Math.max(0, delayTicks);
    Location anchor = new Location(world, (chunkX << 4) + 8.0, 64.0, (chunkZ << 4) + 8.0);
    if (runRegion(anchor, runnable, safeDelay)) {
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

  public static <T> T runRegionResult(Location location, Supplier<T> supplier, T fallback) {
    if (supplier == null) {
      return fallback;
    }

    if (!isFoliaThreading()) {
      try {
        return sResult(supplier);
      } catch (Throwable throwable) {
        React.reportError(throwable);
        return fallback;
      }
    }

    if (location == null || location.getWorld() == null) {
      return fallback;
    }

    if (isOwnedByCurrentRegion(location)) {
      try {
        return supplier.get();
      } catch (Throwable throwable) {
        React.reportError(throwable);
        return fallback;
      }
    }

    AtomicReference<T> result = new AtomicReference<>(fallback);
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    if (!runRegion(location, () -> {
      try {
        result.set(supplier.get());
      } catch (Throwable throwable) {
        error.set(throwable);
      } finally {
        latch.countDown();
      }
    }, 0)) {
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
      React.reportError(throwable);
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
    Plugin plugin = React.instance;
    if (plugin == null) {
      return;
    }

    for (Runnable cancelAction : REPEATING_CANCELLERS.values()) {
      try {
        cancelAction.run();
      } catch (Throwable ex) {
        React.verbose("Failed to run cancel action: " + ex.getClass().getSimpleName()
            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      }
    }
    REPEATING_CANCELLERS.clear();

    FoliaScheduler.cancelTasks(plugin);

    try {
      Bukkit.getScheduler().cancelTasks(plugin);
    } catch (UnsupportedOperationException | IllegalPluginAccessException ex) {
      // Folia blocks BukkitScheduler usage.
      React.verbose("Skipping BukkitScheduler#cancelTasks for React on this server.");
    }
  }

  public static <T> T sResult(Supplier<T> t) {
    if (isPrimaryThread()) {
      return t.get();
    }

    AtomicBoolean finished = new AtomicBoolean(false);
    AtomicReference<T> result = new AtomicReference<>();
    J.s(() -> {
      result.set(t.get());
      finished.set(true);
    });

    while (!finished.get()) {
      J.sleep(15);
    }

    return result.get();
  }

  public static void s(Runnable r) {
    if (!isPluginActive()) {
      return;
    }

    React react = React.instance;
    if (!react.isReady()) {
      react.getPrejobs().add(r);
      return;
    }

    React.controller(JobController.class).queue(r);
  }

  public static void s(Runnable r, int delay) {
    if (delay <= 0) {
      s(r);
      return;
    }

    if (React.instance == null || !isPluginActive()) {
      return;
    }

    Runnable delayed = () -> {
      React react = React.instance;
      if (react == null || !react.isEnabled()) {
        return;
      }

      if (react.isReady()) {
        React.controller(JobController.class).queue(r);
      } else {
        react.getPrejobs().add(r);
      }
    };

    if (runGlobalDelayed(delayed, delay)) {
      return;
    }

    try {
      Bukkit.getScheduler().scheduleSyncDelayedTask(React.instance, delayed, delay);
    } catch (IllegalPluginAccessException e) {
      if (!isPluginActive()) {
        return;
      }

      throw new IllegalStateException("Failed to schedule delayed sync task while plugin is enabled.", e);
    } catch (UnsupportedOperationException e) {
      a(() -> {
        if (sleep(ticksToMilliseconds(delay))) {
          delayed.run();
        }
      });
    }
  }

  public static void sync(Runnable r, int delay) {
    if (!isPluginActive()) {
      return;
    }

    if (delay <= 0) {
      if (!runGlobalImmediate(r)) {
        try {
          Bukkit.getScheduler().scheduleSyncDelayedTask(React.instance, r);
        } catch (IllegalPluginAccessException e) {
          if (!isPluginActive()) {
            return;
          }

          throw new IllegalStateException("Failed to schedule sync task while plugin is enabled.", e);
        } catch (UnsupportedOperationException e) {
          throw new IllegalStateException("Failed to schedule sync task on this server.", e);
        }
      }
      return;
    }

    if (runGlobalDelayed(r, delay)) {
      return;
    }

    try {
      Bukkit.getScheduler().scheduleSyncDelayedTask(React.instance, r, delay);
    } catch (IllegalPluginAccessException e) {
      if (!isPluginActive()) {
        return;
      }

      throw new IllegalStateException("Failed to schedule delayed sync task while plugin is enabled.", e);
    } catch (UnsupportedOperationException e) {
      throw new IllegalStateException("Failed to schedule delayed sync task on this server.", e);
    }
  }

  public static void s(Location location, Runnable r, int delay) {
    if (location == null) {
      sync(r, delay);
      return;
    }

    int safeDelay = Math.max(0, delay);
    if (runRegion(location, r, safeDelay)) {
      return;
    }

    if (isFoliaThreading()) {
      throw new IllegalStateException("Failed to schedule region task on Folia.");
    }

    sync(r, safeDelay);
  }

  public static void s(Location location, Runnable r) {
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
        React.reportError(e);
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
      if (!runAsyncImmediate(r)) {
        a(r);
      }
      return;
    }

    if (!runAsyncDelayed(r, delay)) {
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
        React.reportError(e);
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
    return FoliaScheduler.runGlobal(React.instance, runnable);
  }

  private static boolean runGlobalDelayed(Runnable runnable, int delayTicks) {
    return FoliaScheduler.runGlobal(React.instance, runnable, Math.max(0, delayTicks));
  }

  private static boolean runRegion(Location location, Runnable runnable, int delayTicks) {
    return FoliaScheduler.runRegion(React.instance, location, runnable, Math.max(0, delayTicks));
  }

  private static boolean runAsyncImmediate(Runnable runnable) {
    return FoliaScheduler.runAsync(React.instance, runnable);
  }

  private static boolean runAsyncDelayed(Runnable runnable, int delayTicks) {
    return FoliaScheduler.runAsync(React.instance, runnable, Math.max(0, delayTicks));
  }

  private static Location safeEntityLocation(Entity entity) {
    try {
      return entity.getLocation();
    } catch (Throwable ex) {
      React.verbose("Failed to resolve entity location safely: " + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      return null;
    }
  }

  private static boolean isPluginActive() {
    React react = React.instance;
    return react != null && react.isEnabled();
  }

  private static final class RepeatingState {
    private volatile boolean cancelled;
  }
}
