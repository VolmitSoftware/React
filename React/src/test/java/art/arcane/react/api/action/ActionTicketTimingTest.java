package art.arcane.react.api.action;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Duration bookkeeping the ticket takes from its PrecisionStopwatch. */
class ActionTicketTimingTest {

  @Test
  void durationIsUnsetUntilTheTicketFinishes() {
    ActionTicket<Params> ticket = ticket();
    Assertions.assertEquals(-1D, ticket.getDuration());
    Assertions.assertEquals(0L, ticket.getStartedAt());

    ticket.start();

    Assertions.assertEquals(-1D, ticket.getDuration());
    Assertions.assertTrue(ticket.getStartedAt() > 0L);
    Assertions.assertEquals(0L, ticket.getCompletedAt());
  }

  @Test
  void completingWithoutStartRecordsZeroDuration() {
    ActionTicket<Params> ticket = ticket();

    ticket.complete();

    Assertions.assertEquals(0D, ticket.getDuration());
    Assertions.assertTrue(ticket.getCompletedAt() > 0L);
  }

  @Test
  void durationIsElapsedMillisecondsBetweenStartAndComplete() throws InterruptedException {
    ActionTicket<Params> ticket = ticket();

    ticket.start();
    Thread.sleep(40L);
    ticket.complete();

    double wallClock = ticket.getCompletedAt() - ticket.getStartedAt();
    Assertions.assertTrue(ticket.getDuration() >= 38D, "duration " + ticket.getDuration());
    Assertions.assertTrue(ticket.getDuration() < 5_000D, "duration " + ticket.getDuration());
    Assertions.assertEquals(wallClock, ticket.getDuration(), 3D);
  }

  @Test
  void secondStartDoesNotRestartTheStopwatch() throws InterruptedException {
    ActionTicket<Params> ticket = ticket();

    ticket.start();
    long startedAt = ticket.getStartedAt();
    Thread.sleep(60L);
    ticket.start();
    ticket.complete();

    Assertions.assertEquals(startedAt, ticket.getStartedAt());
    Assertions.assertTrue(ticket.getDuration() >= 58D, "duration " + ticket.getDuration());
  }

  @Test
  void durationIsFrozenOnceTheTicketIsDone() throws InterruptedException {
    ActionTicket<Params> ticket = ticket();
    ticket.start();
    ticket.complete();
    double duration = ticket.getDuration();
    long completedAt = ticket.getCompletedAt();

    Thread.sleep(20L);
    ticket.complete();
    ticket.fail(new IllegalStateException("late"));

    Assertions.assertEquals(duration, ticket.getDuration());
    Assertions.assertEquals(completedAt, ticket.getCompletedAt());
  }

  @Test
  void failureRecordsTheElapsedDuration() throws InterruptedException {
    ActionTicket<Params> ticket = ticket();

    ticket.start();
    Thread.sleep(30L);
    ticket.fail(new IllegalStateException("failed"));

    Assertions.assertTrue(ticket.isDone());
    Assertions.assertTrue(ticket.getDuration() >= 28D, "duration " + ticket.getDuration());
    Assertions.assertTrue(ticket.getDuration() < 5_000D, "duration " + ticket.getDuration());
  }

  @SuppressWarnings("unchecked")
  private static ActionTicket<Params> ticket() {
    Action<Params> action = Mockito.mock(Action.class);
    return new ActionTicket<>(action, new Params());
  }

  private static final class Params implements ActionParams {
  }
}
