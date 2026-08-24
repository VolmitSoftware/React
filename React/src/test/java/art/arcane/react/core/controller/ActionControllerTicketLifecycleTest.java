package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.util.common.scheduling.Ticker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ActionControllerTicketLifecycleTest {
  private React previous;
  private ActionController controller;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
    controller = new ActionController();
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void terminalRuntimeTicketsAreReapedBeforeCapacityIsChecked() {
    ActionTicket<Params> ticket = ticket(action("terminal"));
    ticket.complete();
    controller.getTicketRuntime().add(ticket);

    controller.onTick();

    Assertions.assertTrue(controller.getTicketRuntime().isEmpty());
  }

  @Test
  void startCallbackFailureTerminatesWithoutEnteringRuntime() {
    ActionTicket<Params> ticket = ticket(action("start-failure"));
    IllegalStateException failure = new IllegalStateException("start callback failed");
    ticket.onStart(ignored -> {
      throw failure;
    });
    controller.queueAction(ticket);

    Assertions.assertDoesNotThrow(controller::onTick);

    Assertions.assertTrue(ticket.isDone());
    Assertions.assertTrue(ticket.isFailed());
    Assertions.assertSame(failure, ticket.getFailure());
    Assertions.assertTrue(controller.getTicketRuntime().isEmpty());
  }

  @Test
  void actionFailureTerminatesAndIsReaped() {
    Action<Params> action = action("work-failure");
    IllegalArgumentException failure = new IllegalArgumentException("work failed");
    Mockito.doThrow(failure).when(action).workOn(Mockito.any());
    ActionTicket<Params> ticket = ticket(action);
    controller.queueAction(ticket);

    Assertions.assertDoesNotThrow(controller::onTick);

    Assertions.assertTrue(ticket.isDone());
    Assertions.assertTrue(ticket.isFailed());
    Assertions.assertSame(failure, ticket.getFailure());
    Assertions.assertTrue(controller.getTicketRuntime().isEmpty());
  }

  @Test
  void stopFailsAndClearsQueuedAndRunningTickets() {
    ActionTicket<Params> queued = ticket(action("queued"));
    ActionTicket<Params> running = ticket(action("running"));
    running.start();
    controller.queueAction(queued);
    controller.getTicketRuntime().add(running);

    controller.stop();

    Assertions.assertTrue(queued.isDone());
    Assertions.assertTrue(queued.isFailed());
    Assertions.assertTrue(running.isDone());
    Assertions.assertTrue(running.isFailed());
    Assertions.assertTrue(controller.getTicketQueue().isEmpty());
    Assertions.assertTrue(controller.getTicketRuntime().isEmpty());

    ActionTicket<Params> late = ticket(action("late"));
    controller.queueAction(late);

    Assertions.assertTrue(late.isDone());
    Assertions.assertTrue(late.isFailed());
    Assertions.assertTrue(controller.getTicketQueue().isEmpty());
  }

  private ActionTicket<Params> ticket(Action<Params> action) {
    return new ActionTicket<>(action, new Params());
  }

  @SuppressWarnings("unchecked")
  private Action<Params> action(String id) {
    Action<Params> action = Mockito.mock(Action.class);
    Mockito.when(action.isEnabled()).thenReturn(true);
    Mockito.when(action.getId()).thenReturn(id);
    return action;
  }

  private static final class Params implements ActionParams {
  }
}
