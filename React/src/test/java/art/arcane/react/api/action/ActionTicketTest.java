package art.arcane.react.api.action;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

class ActionTicketTest {
  @Test
  void completeBeforeStartIsTerminalAndIdempotent() {
    ActionTicket<Params> ticket = ticket();
    AtomicInteger completions = new AtomicInteger();
    AtomicInteger terminals = new AtomicInteger();
    ticket.onComplete(ignored -> completions.incrementAndGet());
    ticket.onTerminal(ignored -> terminals.incrementAndGet());

    ticket.complete();
    long completedAt = ticket.getCompletedAt();
    ticket.complete();
    ticket.start();

    Assertions.assertTrue(ticket.isDone());
    Assertions.assertFalse(ticket.isFailed());
    Assertions.assertFalse(ticket.isRunning());
    Assertions.assertEquals(0L, ticket.getStartedAt());
    Assertions.assertEquals(completedAt, ticket.getCompletedAt());
    Assertions.assertEquals(0D, ticket.getDuration());
    Assertions.assertEquals(1, completions.get());
    Assertions.assertEquals(1, terminals.get());
  }

  @Test
  void failureInvokesOnlyTheTerminalContractExactlyOnce() {
    ActionTicket<Params> ticket = ticket();
    AtomicInteger completions = new AtomicInteger();
    AtomicInteger terminals = new AtomicInteger();
    IllegalStateException failure = new IllegalStateException("failed");
    ticket.onComplete(ignored -> completions.incrementAndGet());
    ticket.onTerminal(completed -> {
      Assertions.assertTrue(completed.isFailed());
      Assertions.assertSame(failure, completed.getFailure());
      terminals.incrementAndGet();
    });

    ticket.fail(failure);
    ticket.fail(new IllegalStateException("duplicate"));
    ticket.complete();

    Assertions.assertEquals(0, completions.get());
    Assertions.assertEquals(1, terminals.get());
  }

  @Test
  void startCallbackFailureMarksTheTicketFailed() {
    ActionTicket<Params> ticket = ticket();
    IllegalStateException failure = new IllegalStateException("start callback failed");
    ticket.onStart(ignored -> {
      throw failure;
    });

    IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, ticket::start);

    Assertions.assertSame(failure, thrown);
    Assertions.assertTrue(ticket.isDone());
    Assertions.assertTrue(ticket.isFailed());
    Assertions.assertFalse(ticket.isRunning());
    Assertions.assertSame(failure, ticket.getFailure());
  }

  @Test
  void completionCallbackFailureUpgradesCompletionToFailure() {
    ActionTicket<Params> ticket = ticket();
    IllegalArgumentException failure = new IllegalArgumentException("completion callback failed");
    ticket.onComplete(ignored -> {
      throw failure;
    });
    ticket.start();

    IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class, ticket::complete);

    Assertions.assertSame(failure, thrown);
    Assertions.assertTrue(ticket.isDone());
    Assertions.assertTrue(ticket.isFailed());
    Assertions.assertFalse(ticket.isRunning());
    Assertions.assertSame(failure, ticket.getFailure());
  }

  @SuppressWarnings("unchecked")
  private ActionTicket<Params> ticket() {
    Action<Params> action = Mockito.mock(Action.class);
    return new ActionTicket<>(action, new Params());
  }

  private static final class Params implements ActionParams {
  }
}
