package art.arcane.react.api.event;

import art.arcane.react.testutil.IsolatedClassLoader;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

class NaughtyRegisteredListenerTest {
  @Test
  void handlerFailureIsTimedCountedAndPropagated() {
    EventException failure = new EventException(new IllegalStateException("handler failed"));
    EventExecutor executor = (ignored, event) -> {
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
      throw failure;
    };
    NaughtyRegisteredListener listener = listener(executor);

    EventException thrown = Assertions.assertThrows(EventException.class,
        () -> listener.callEvent(Mockito.mock(Event.class)));
    NaughtyRegisteredListener.CounterSnapshot first = listener.drainCounters();
    NaughtyRegisteredListener.CounterSnapshot second = listener.drainCounters();

    Assertions.assertSame(failure, thrown);
    Assertions.assertEquals(1L, first.calls());
    Assertions.assertTrue(first.timeNanos() > 0L);
    Assertions.assertEquals(0L, second.calls());
    Assertions.assertEquals(0L, second.timeNanos());
  }

  @Test
  void delegatesToTheOriginalRegisteredListenerIncludingOverrides() throws EventException {
    AtomicInteger overrideCalls = new AtomicInteger();
    EventExecutor executor = (ignored, event) -> Assertions.fail("executor must not be invoked directly");
    RegisteredListener original = new RegisteredListener(
        Mockito.mock(Listener.class),
        executor,
        EventPriority.HIGH,
        plugin(),
        true
    ) {
      @Override
      public void callEvent(Event event) {
        overrideCalls.incrementAndGet();
      }
    };
    NaughtyRegisteredListener wrapped = new NaughtyRegisteredListener(original, executor, 7L);

    wrapped.callEvent(Mockito.mock(Event.class));

    Assertions.assertEquals(1, overrideCalls.get());
    Assertions.assertSame(original, wrapped.delegate());
    Assertions.assertSame(original.getListener(), wrapped.getListener());
    Assertions.assertSame(original.getPlugin(), wrapped.getPlugin());
    Assertions.assertEquals(EventPriority.HIGH, wrapped.getPriority());
    Assertions.assertTrue(wrapped.isIgnoringCancelled());
    Assertions.assertTrue(wrapped.isOwnedBy(7L));
    Assertions.assertEquals(1L, wrapped.drainCounters().calls());
  }

  @Test
  void nestedHandlerTimeIsChargedOnlyToTheInnerHandler() throws EventException {
    long innerParkNanos = TimeUnit.MILLISECONDS.toNanos(20);
    NaughtyRegisteredListener inner = listener((ignored, event) -> LockSupport.parkNanos(innerParkNanos));
    Event nested = Mockito.mock(Event.class);
    NaughtyRegisteredListener outer = listener((ignored, event) -> inner.callEvent(nested));

    outer.callEvent(Mockito.mock(Event.class));
    NaughtyRegisteredListener.CounterSnapshot outerSnapshot = outer.drainCounters();
    NaughtyRegisteredListener.CounterSnapshot innerSnapshot = inner.drainCounters();

    Assertions.assertEquals(1L, outerSnapshot.calls());
    Assertions.assertEquals(1L, innerSnapshot.calls());
    Assertions.assertTrue(innerSnapshot.timeNanos() >= innerParkNanos);
    Assertions.assertTrue(outerSnapshot.timeNanos() < innerParkNanos / 2L,
        "outer exclusive time " + outerSnapshot.timeNanos() + " must exclude the nested " + innerSnapshot.timeNanos());
  }

  @Test
  void nestingAccumulatorResetsBetweenTopLevelCalls() throws EventException {
    long parkNanos = TimeUnit.MILLISECONDS.toNanos(5);
    NaughtyRegisteredListener inner = listener((ignored, event) -> LockSupport.parkNanos(parkNanos));
    Event nested = Mockito.mock(Event.class);
    NaughtyRegisteredListener outer = listener((ignored, event) -> inner.callEvent(nested));

    outer.callEvent(Mockito.mock(Event.class));
    inner.callEvent(nested);
    inner.drainCounters();
    NaughtyRegisteredListener second = listener((ignored, event) -> LockSupport.parkNanos(parkNanos));
    second.callEvent(Mockito.mock(Event.class));

    Assertions.assertTrue(second.drainCounters().timeNanos() >= parkNanos);
  }

  @Test
  void asynchronousEventsAreDelegatedWithoutTiming() throws EventException {
    AtomicInteger executions = new AtomicInteger();
    NaughtyRegisteredListener listener = listener((ignored, event) -> {
      executions.incrementAndGet();
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
    });
    Event async = Mockito.mock(Event.class);
    Mockito.when(async.isAsynchronous()).thenReturn(true);

    listener.callEvent(async);
    NaughtyRegisteredListener.CounterSnapshot snapshot = listener.drainCounters();

    Assertions.assertEquals(1, executions.get());
    Assertions.assertEquals(0L, snapshot.calls());
    Assertions.assertEquals(0L, snapshot.timeNanos());
    Assertions.assertEquals(1L, snapshot.asyncCalls());
    Assertions.assertEquals(0L, listener.drainCounters().asyncCalls());
  }

  @Test
  void callEventDoesNotPinTheDefiningClassLoaderThroughThreadLocals() throws Exception {
    WeakReference<ClassLoader> loader = dispatchThroughAnIsolatedRuntime();

    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (loader.get() != null && System.nanoTime() < deadline) {
      System.gc();
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
    }

    Assertions.assertNull(loader.get(),
        "the class loader that defined the wrapper must be collectable after this thread dispatched an event");
  }

  @Test
  void concurrentCallsAreAccountedWithoutSerializingRegions() throws Exception {
    int workers = 16;
    int callsPerWorker = 1_000;
    NaughtyRegisteredListener listener = listener((ignored, event) -> {
    });
    Event event = Mockito.mock(Event.class);
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>(workers);

    try {
      for (int worker = 0; worker < workers; worker++) {
        futures.add(executor.submit(() -> {
          ready.countDown();
          await(start);
          for (int call = 0; call < callsPerWorker; call++) {
            try {
              listener.callEvent(event);
            } catch (EventException exception) {
              throw new IllegalStateException(exception);
            }
          }
        }));
      }

      Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    } finally {
      start.countDown();
      executor.shutdownNow();
      Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    NaughtyRegisteredListener.CounterSnapshot snapshot = listener.drainCounters();
    Assertions.assertEquals((long) workers * callsPerWorker, snapshot.calls());
    Assertions.assertTrue(snapshot.timeNanos() > 0L);
    Assertions.assertEquals(0L, listener.drainCounters().calls());
  }

  private NaughtyRegisteredListener listener(EventExecutor executor) {
    RegisteredListener original = new RegisteredListener(
        Mockito.mock(Listener.class),
        executor,
        EventPriority.NORMAL,
        plugin(),
        false
    );
    return new NaughtyRegisteredListener(original, executor, 1L);
  }

  private static WeakReference<ClassLoader> dispatchThroughAnIsolatedRuntime() throws Exception {
    IsolatedClassLoader loader = new IsolatedClassLoader(NaughtyRegisteredListener.class);
    Class<?> type = loader.isolatedClass();
    Assertions.assertNotSame(NaughtyRegisteredListener.class, type);
    EventExecutor executor = (ignored, event) -> {
    };
    Plugin plugin = (Plugin) Proxy.newProxyInstance(
        Plugin.class.getClassLoader(),
        new Class<?>[]{Plugin.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getName" -> "MeasuredPlugin";
          case "isEnabled" -> true;
          default -> null;
        }
    );
    RegisteredListener original = new RegisteredListener(
        new Listener() {
        },
        executor,
        EventPriority.NORMAL,
        plugin,
        false
    );
    Object wrapper = type.getConstructor(RegisteredListener.class, EventExecutor.class, long.class)
        .newInstance(original, executor, 1L);
    type.getMethod("callEvent", Event.class).invoke(wrapper, new DispatchedEvent());
    return new WeakReference<>(loader);
  }

  private static final class DispatchedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public HandlerList getHandlers() {
      return HANDLERS;
    }
  }

  private static Plugin plugin() {
    Plugin plugin = Mockito.mock(Plugin.class);
    Mockito.when(plugin.isEnabled()).thenReturn(true);
    Mockito.when(plugin.getName()).thenReturn("MeasuredPlugin");
    return plugin;
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
