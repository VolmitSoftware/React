package art.arcane.react.util.atomics;

import art.arcane.multiburst.MultiBurst;
import art.arcane.react.React;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

class AsyncRequestTest {
  private MultiBurst previousBurst;
  private MultiBurst burst;

  @BeforeEach
  void setUp() {
    previousBurst = React.burst;
    burst = Mockito.mock(MultiBurst.class);
    Mockito.doAnswer(invocation -> {
      Runnable task = invocation.getArgument(0);
      task.run();
      return null;
    }).when(burst).lazy(Mockito.any(Runnable.class));
    React.burst = burst;
  }

  @AfterEach
  void tearDown() {
    React.burst = previousBurst;
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
    Mockito.verify(burst, Mockito.times(2)).lazy(Mockito.any(Runnable.class));
  }
}
