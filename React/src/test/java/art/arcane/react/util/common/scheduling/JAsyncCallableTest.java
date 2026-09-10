package art.arcane.react.util.common.scheduling;

import art.arcane.multiburst.MultiBurst;
import art.arcane.volmlib.util.scheduling.SchedulerBridge;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class JAsyncCallableTest {
  @Test
  void firstCallableStartsAColdWorkerPool() throws Exception {
    try (IsolatedScheduler scheduler = new IsolatedScheduler()) {
      Thread caller = Thread.currentThread();
      Future<?> submitted = scheduler.submit(() -> new Result("cold", Thread.currentThread()));
      Result result = (Result) submitted.get(5, TimeUnit.SECONDS);

      assertEquals("cold", result.value());
      assertNotSame(caller, result.worker());
    }
  }

  @Test
  void callableRestartsTheWorkerPoolAfterItCloses() throws Exception {
    try (IsolatedScheduler scheduler = new IsolatedScheduler()) {
      Thread caller = Thread.currentThread();
      CompletableFuture<Result> warmed = new CompletableFuture<>();
      scheduler.execute(() -> warmed.complete(new Result("first", Thread.currentThread())));
      Result first = warmed.get(5, TimeUnit.SECONDS);
      scheduler.closePool();

      Result restarted = (Result) scheduler.submit(() -> new Result("restarted", Thread.currentThread()))
          .get(5, TimeUnit.SECONDS);

      assertEquals("first", first.value());
      assertEquals("restarted", restarted.value());
      assertNotSame(caller, restarted.worker());
      assertNotSame(first.worker(), restarted.worker());
    }
  }

  private static final class IsolatedScheduler implements AutoCloseable {
    private final SchedulerClassLoader loader;
    private final Object pool;
    private final Method submit;
    private final Method execute;
    private final Method close;

    private IsolatedScheduler() throws Exception {
      loader = new SchedulerClassLoader();
      Class<?> schedulerType = Class.forName(J.class.getName(), true, loader);
      Class<?> poolType = Class.forName(MultiBurst.class.getName(), true, loader);
      pool = poolType.getField("burst").get(null);
      submit = schedulerType.getMethod("a", Callable.class);
      execute = schedulerType.getMethod("a", Runnable.class);
      close = poolType.getMethod("close");
    }

    private Future<?> submit(Callable<?> task) throws Exception {
      return (Future<?>) submit.invoke(null, task);
    }

    private void execute(Runnable task) throws Exception {
      execute.invoke(null, task);
    }

    private void closePool() throws Exception {
      close.invoke(pool);
    }

    @Override
    public void close() throws Exception {
      try {
        closePool();
      } finally {
        loader.close();
      }
    }
  }

  private static final class SchedulerClassLoader extends URLClassLoader {
    private SchedulerClassLoader() {
      super(new URL[]{
          J.class.getProtectionDomain().getCodeSource().getLocation(),
          MultiBurst.class.getProtectionDomain().getCodeSource().getLocation(),
          SchedulerBridge.class.getProtectionDomain().getCodeSource().getLocation()
      }, JAsyncCallableTest.class.getClassLoader());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!name.equals(J.class.getName()) && !name.startsWith(J.class.getName() + "$")
          && !name.startsWith("art.arcane.multiburst.")
          && !name.equals(SchedulerBridge.class.getName())
          && !name.startsWith(SchedulerBridge.class.getName() + "$")) {
        return super.loadClass(name, resolve);
      }

      synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
          loaded = findClass(name);
        }
        if (resolve) {
          resolveClass(loaded);
        }
        return loaded;
      }
    }
  }

  private record Result(String value, Thread worker) {
  }
}
