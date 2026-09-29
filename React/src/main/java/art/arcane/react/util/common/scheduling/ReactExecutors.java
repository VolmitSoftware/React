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

import art.arcane.react.React;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReactExecutors {
  private static final String TICK_THREAD_PREFIX = "react-tick-";
  private static final String ASYNC_THREAD_PREFIX = "react-async-";
  private static final int MIN_ASYNC_THREADS = 8;
  private static final int ASYNC_THREADS_PER_CORE = 4;
  private static final long ASYNC_KEEP_ALIVE_SECONDS = 60L;
  private static final long CLOSE_TIMEOUT_MS = 2_000L;
  private static final Object FALLBACK_LOCK = new Object();
  private static ExecutorService fallbackAsync;

  private final ExecutorService tickPool;
  private final ExecutorService asyncPool;
  private volatile boolean closed;

  public ReactExecutors(ExecutorService tickPool, ExecutorService asyncPool) {
    this.tickPool = tickPool;
    this.asyncPool = asyncPool;
    this.closed = false;
  }

  public static ReactExecutors create() {
    int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
    return new ReactExecutors(tickPool(cores), asyncPool(cores));
  }

  public static ExecutorService async() {
    ReactExecutors runtime = React.executors;
    if (runtime != null && !runtime.closed) {
      return runtime.asyncPool;
    }

    return fallbackAsync();
  }

  public ExecutorService tickPool() {
    return tickPool;
  }

  public ExecutorService asyncPool() {
    return asyncPool;
  }

  public boolean isClosed() {
    return closed;
  }

  public void close() {
    closed = true;
    tickPool.shutdown();
    asyncPool.shutdown();
    awaitTermination(tickPool);
    awaitTermination(asyncPool);
  }

  private static ExecutorService fallbackAsync() {
    synchronized (FALLBACK_LOCK) {
      if (fallbackAsync == null || fallbackAsync.isShutdown()) {
        fallbackAsync = asyncPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
      }

      return fallbackAsync;
    }
  }

  private static ExecutorService tickPool(int cores) {
    return new ThreadPoolExecutor(cores, cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), threadFactory(TICK_THREAD_PREFIX));
  }

  private static ExecutorService asyncPool(int cores) {
    int maxThreads = Math.max(MIN_ASYNC_THREADS, cores * ASYNC_THREADS_PER_CORE);
    ThreadPoolExecutor pool = new ThreadPoolExecutor(
        cores,
        maxThreads,
        ASYNC_KEEP_ALIVE_SECONDS,
        TimeUnit.SECONDS,
        new HandoffQueue(),
        threadFactory(ASYNC_THREAD_PREFIX),
        new QueueWhenSaturated()
    );
    pool.allowCoreThreadTimeOut(true);
    return pool;
  }

  private static ThreadFactory threadFactory(String prefix) {
    AtomicInteger counter = new AtomicInteger();
    return runnable -> {
      Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
      thread.setDaemon(true);
      thread.setPriority(Thread.MIN_PRIORITY);
      thread.setUncaughtExceptionHandler((worker, failure) -> React.reportError("Uncaught failure on " + worker.getName(), failure));
      return thread;
    };
  }

  private static void awaitTermination(ExecutorService pool) {
    boolean interrupted = Thread.interrupted();
    try {
      if (!pool.awaitTermination(CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        pool.shutdownNow();
      }
    } catch (InterruptedException ex) {
      interrupted = true;
      pool.shutdownNow();
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static final class HandoffQueue extends LinkedTransferQueue<Runnable> {
    @Override
    public boolean offer(Runnable task) {
      return tryTransfer(task);
    }
  }

  private static final class QueueWhenSaturated implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
      if (executor.isShutdown()) {
        throw new RejectedExecutionException("React async executor is closed");
      }

      executor.getQueue().add(task);
    }
  }
}
