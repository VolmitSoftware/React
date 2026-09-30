package art.arcane.react.util.common.scheduling;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptDeadlineTest {
  @Test
  void waitCompletesWhenTheResponseArrives() {
    AtomicLong nanos = new AtomicLong();
    AtomicInteger polls = new AtomicInteger();

    PromptWait.Outcome outcome = PromptWait.await(
        () -> polls.incrementAndGet() >= 3,
        () -> true,
        PromptWait.DEFAULT_TIMEOUT_MS,
        nanos::get,
        advancing(nanos)
    );

    assertEquals(PromptWait.Outcome.COMPLETED, outcome);
    assertEquals(3, polls.get());
  }

  @Test
  void waitTimesOutAtTheDeadline() {
    AtomicLong nanos = new AtomicLong();

    PromptWait.Outcome outcome = PromptWait.await(() -> false, () -> true, PromptWait.DEFAULT_TIMEOUT_MS, nanos::get, advancing(nanos));

    assertEquals(PromptWait.Outcome.TIMED_OUT, outcome);
    long waitedMS = TimeUnit.NANOSECONDS.toMillis(nanos.get());
    assertTrue(waitedMS >= PromptWait.DEFAULT_TIMEOUT_MS, "waited " + waitedMS + "ms");
    assertTrue(waitedMS < PromptWait.DEFAULT_TIMEOUT_MS + 1_000L, "waited " + waitedMS + "ms");
  }

  @Test
  void waitStopsWhenReactBecomesUnavailable() {
    AtomicLong nanos = new AtomicLong();
    AtomicInteger polls = new AtomicInteger();

    PromptWait.Outcome outcome = PromptWait.await(
        () -> false,
        () -> polls.incrementAndGet() < 2,
        PromptWait.DEFAULT_TIMEOUT_MS,
        nanos::get,
        advancing(nanos)
    );

    assertEquals(PromptWait.Outcome.UNAVAILABLE, outcome);
  }

  @Test
  void waitStopsWhenTheWorkerIsInterrupted() {
    AtomicLong nanos = new AtomicLong();

    PromptWait.Outcome outcome = PromptWait.await(() -> false, () -> true, PromptWait.DEFAULT_TIMEOUT_MS, nanos::get, ms -> false);

    assertEquals(PromptWait.Outcome.INTERRUPTED, outcome);
  }

  private static LongPredicate advancing(AtomicLong nanos) {
    return ms -> {
      nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(ms));
      return true;
    };
  }
}
