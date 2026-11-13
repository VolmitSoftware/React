package art.arcane.react.web;

import art.arcane.react.api.web.ws.WsTransport;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Test transport that records frames; completes writes at once unless {@link #hold()} is on. */
final class RecordingTransport implements WsTransport {
  private final String id;
  private final List<String> sent = new ArrayList<>();
  private final List<Consumer<Throwable>> held = new ArrayList<>();
  private boolean holding;
  private boolean open = true;
  private int pings;
  private int closeCode = -1;

  RecordingTransport(String id) {
    this.id = id;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public synchronized boolean isOpen() {
    return open;
  }

  @Override
  public void sendAsync(String text, Consumer<Throwable> completion) {
    synchronized (this) {
      sent.add(text);
      if (holding) {
        held.add(completion);
        return;
      }
    }
    completion.accept(null);
  }

  @Override
  public synchronized void ping() {
    pings++;
  }

  @Override
  public synchronized void close(int code, String reason) {
    closeCode = code;
    open = false;
  }

  synchronized void hold() {
    holding = true;
  }

  /** Completes every held write in order and stops holding. */
  void release() {
    List<Consumer<Throwable>> completions;
    synchronized (this) {
      holding = false;
      completions = new ArrayList<>(held);
      held.clear();
    }
    for (Consumer<Throwable> completion : completions) {
      completion.accept(null);
    }
  }

  void failHeld() {
    List<Consumer<Throwable>> completions;
    synchronized (this) {
      completions = new ArrayList<>(held);
      held.clear();
    }
    for (Consumer<Throwable> completion : completions) {
      completion.accept(new IllegalStateException("closed"));
    }
  }

  synchronized List<String> sent() {
    return List.copyOf(sent);
  }

  synchronized int heldWrites() {
    return held.size();
  }

  synchronized int pings() {
    return pings;
  }

  synchronized int closeCode() {
    return closeCode;
  }
}
