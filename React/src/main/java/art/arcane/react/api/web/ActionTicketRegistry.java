package art.arcane.react.api.web;

import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Outcome of the most recent web-dispatched actions, looked up by ticket id. */
public final class ActionTicketRegistry {
  public static final int CAPACITY = 256;

  public enum State {
    QUEUED("queued"),
    RUNNING("running"),
    DONE("done"),
    FAILED("failed");

    private final String wire;

    State(String wire) {
      this.wire = wire;
    }

    public String wire() {
      return wire;
    }
  }

  public record TicketView(String id, String state, String message) {
  }

  private final Runnable onChange;
  private final LinkedHashMap<String, TicketView> tickets = new LinkedHashMap<>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, TicketView> eldest) {
      return size() > CAPACITY;
    }
  };

  public ActionTicketRegistry(Runnable onChange) {
    this.onChange = onChange;
  }

  public String register() {
    String id = UUID.randomUUID().toString();
    update(id, State.QUEUED, "");
    return id;
  }

  public <T extends ActionParams> String track(ActionTicket<T> ticket) {
    String id = register();
    ticket.onStart(started -> update(id, State.RUNNING, ""));
    ticket.onTerminal(finished -> {
      if (finished.isFailed()) {
        Throwable failure = finished.getFailure();
        String message = failure == null || failure.getMessage() == null
            ? "Action failed"
            : failure.getMessage();
        update(id, State.FAILED, message);
        return;
      }
      update(id, State.DONE, completedMessage(finished));
    });
    return id;
  }

  public void update(String id, State state, String message) {
    synchronized (tickets) {
      tickets.put(id, new TicketView(id, state.wire(), message == null ? "" : message));
    }
    onChange.run();
  }

  public TicketView get(String id) {
    synchronized (tickets) {
      return tickets.get(id);
    }
  }

  private static <T extends ActionParams> String completedMessage(ActionTicket<T> ticket) {
    try {
      return ticket.getAction().getCompletedMessage(ticket);
    } catch (Throwable ignored) {
      return "";
    }
  }
}
