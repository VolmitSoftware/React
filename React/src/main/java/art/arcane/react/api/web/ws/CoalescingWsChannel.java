package art.arcane.react.api.web.ws;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Latest-wins metrics channel: one write in flight, a newer frame replaces any unsent one. */
public final class CoalescingWsChannel implements WsChannel {
  private static final AtomicLong COALESCED_FRAMES = new AtomicLong();

  private final WsTransport transport;
  private final Consumer<Throwable> completion = this::onWritten;
  private String pending;
  private boolean writing;
  private boolean failed;

  public CoalescingWsChannel(WsTransport transport) {
    this.transport = transport;
  }

  public static long coalescedFrames() {
    return COALESCED_FRAMES.get();
  }

  @Override
  public String id() {
    return transport.id();
  }

  @Override
  public boolean isOpen() {
    synchronized (this) {
      if (failed) {
        return false;
      }
    }
    return transport.isOpen();
  }

  @Override
  public void ping() {
    transport.ping();
  }

  @Override
  public void close(int code, String reason) {
    synchronized (this) {
      failed = true;
      pending = null;
    }
    transport.close(code, reason);
  }

  public void send(String text) {
    synchronized (this) {
      if (failed) {
        return;
      }
      if (writing) {
        if (pending != null) {
          COALESCED_FRAMES.incrementAndGet();
        }
        pending = text;
        return;
      }
      writing = true;
    }
    transport.sendAsync(text, completion);
  }

  private void onWritten(Throwable failure) {
    String next;
    synchronized (this) {
      if (failure != null) {
        failed = true;
        pending = null;
        writing = false;
        return;
      }
      next = pending;
      pending = null;
      if (next == null) {
        writing = false;
        return;
      }
    }
    transport.sendAsync(next, completion);
  }
}
