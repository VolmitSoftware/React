package art.arcane.react.util.atomics;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.ReactExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class AsyncRequestTest {
  private ReactExecutors previous;
  private ReactExecutors executors;
  private AtomicInteger dispatches;

  @BeforeEach
  void setUp() {
    previous = React.executors;
    dispatches = new AtomicInteger();
    ExecutorService inline = new InlineExecutorService(dispatches);
    executors = new ReactExecutors(inline, inline);
    React.executors = executors;
  }

  @AfterEach
  void tearDown() {
    React.executors = previous;
  }

  @Test
  void failedRefreshCanBeRequestedAgain() {
    AtomicInteger attempts = new AtomicInteger();
    AsyncRequest<Integer> request = new AsyncRequest<>(() -> {
      if (attempts.incrementAndGet() == 1) {
        throw new IllegalStateException("first refresh failed");
      }
      return 7;
    }, 0);

    Assertions.assertThrows(IllegalStateException.class, request::request);
    Assertions.assertEquals(7, request.request());
    Assertions.assertEquals(2, attempts.get());
    Assertions.assertEquals(2, dispatches.get());
  }

  private static final class InlineExecutorService extends AbstractExecutorService {
    private final AtomicInteger dispatches;
    private volatile boolean shutdown;

    private InlineExecutorService(AtomicInteger dispatches) {
      this.dispatches = dispatches;
    }

    @Override
    public void execute(Runnable command) {
      dispatches.incrementAndGet();
      command.run();
    }

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }
  }
}
