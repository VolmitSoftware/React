package art.arcane.react.core.controller;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class HotloadTaskExecutor implements AutoCloseable {
  private final ExecutorService executor;
  private final Consumer<Throwable> failureHandler;
  private final AtomicBoolean pollQueued;
  private final AtomicBoolean closed;

  HotloadTaskExecutor(String threadName, Consumer<Throwable> failureHandler) {
    String requiredThreadName = Objects.requireNonNull(threadName, "threadName");
    this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    this.pollQueued = new AtomicBoolean();
    this.closed = new AtomicBoolean();
    this.executor = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, requiredThreadName);
      thread.setDaemon(true);
      return thread;
    });
  }

  boolean requestPoll(Runnable poll) {
    Objects.requireNonNull(poll, "poll");
    if (closed.get() || !pollQueued.compareAndSet(false, true)) {
      return false;
    }
    if (submit(() -> {
      pollQueued.set(false);
      if (!closed.get()) {
        poll.run();
      }
    })) {
      return true;
    }
    pollQueued.set(false);
    return false;
  }

  boolean execute(Runnable task) {
    Objects.requireNonNull(task, "task");
    return submit(() -> {
      if (!closed.get()) {
        task.run();
      }
    });
  }

  boolean isClosed() {
    return closed.get();
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    pollQueued.set(false);
    executor.shutdownNow();
  }

  void retire(Runnable cleanup) {
    Objects.requireNonNull(cleanup, "cleanup");
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    pollQueued.set(false);
    try {
      executor.execute(() -> {
        try {
          cleanup.run();
        } catch (RuntimeException failure) {
          failureHandler.accept(failure);
        }
      });
    } catch (RejectedExecutionException rejected) {
      cleanup.run();
    } finally {
      executor.shutdown();
    }
  }

  private boolean submit(Runnable task) {
    if (closed.get()) {
      return false;
    }
    try {
      executor.execute(() -> {
        try {
          task.run();
        } catch (RuntimeException failure) {
          failureHandler.accept(failure);
        }
      });
      return true;
    } catch (RejectedExecutionException rejected) {
      return false;
    }
  }
}
