package art.arcane.react.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    Plugin plugin = Mockito.mock(Plugin.class);
    Mockito.when(plugin.isEnabled()).thenReturn(true);
    Mockito.when(plugin.getName()).thenReturn("MeasuredPlugin");
    return new NaughtyRegisteredListener(
        Mockito.mock(Listener.class),
        executor,
        EventPriority.NORMAL,
        plugin,
        false,
        1L
    );
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
