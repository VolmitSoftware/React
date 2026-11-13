package art.arcane.react.api.web.ws;

import art.arcane.react.api.web.LogLine;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Ordered log stream for one socket. Past the bound the oldest lines drop and the next frame reports how many.
 * Batched sessions get {@code "logs"} frames, older pages one {@code "log"} frame per line.
 */
public final class LogWsChannel implements WsChannel {
  public static final int DEFAULT_CAPACITY = 2048;
  public static final int MAX_LINES_PER_FLUSH = 256;

  private final WsTransport transport;
  private final Executor flusher;
  private final boolean batched;
  private final int capacity;
  private final ArrayDeque<LogLine> queue = new ArrayDeque<>();
  private final Runnable flushTask = this::flush;
  private final Consumer<Throwable> lastWriteDone = this::onFlushWritten;
  private final Consumer<Throwable> earlierWriteDone = this::onEarlierWrite;
  private long dropped;
  private boolean flushing;
  private boolean failed;

  public LogWsChannel(WsTransport transport, Executor flusher, boolean batched, int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be at least 1");
    }
    this.transport = transport;
    this.flusher = flusher;
    this.batched = batched;
    this.capacity = capacity;
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
    markFailed();
    transport.close(code, reason);
  }

  public boolean isBatched() {
    return batched;
  }

  public synchronized int queued() {
    return queue.size();
  }

  public void offer(LogLine line) {
    synchronized (this) {
      if (failed) {
        return;
      }
      if (queue.size() >= capacity) {
        queue.pollFirst();
        dropped++;
      }
      queue.addLast(line);
      if (flushing) {
        return;
      }
      flushing = true;
    }
    scheduleFlush();
  }

  private void scheduleFlush() {
    try {
      flusher.execute(flushTask);
    } catch (RejectedExecutionException rejected) {
      synchronized (this) {
        flushing = false;
      }
    }
  }

  private void flush() {
    List<LogLine> lines;
    long droppedBefore;
    synchronized (this) {
      if (failed || queue.isEmpty()) {
        flushing = false;
        return;
      }
      int count = Math.min(MAX_LINES_PER_FLUSH, queue.size());
      lines = new ArrayList<>(count);
      for (int index = 0; index < count; index++) {
        lines.add(queue.pollFirst());
      }
      droppedBefore = dropped;
      dropped = 0L;
    }
    if (batched) {
      transport.sendAsync(batchFrame(lines, droppedBefore), lastWriteDone);
      return;
    }
    int last = lines.size() - 1;
    for (int index = 0; index <= last; index++) {
      String frame = lineFrame(lines.get(index), index == 0 ? droppedBefore : 0L);
      transport.sendAsync(frame, index == last ? lastWriteDone : earlierWriteDone);
    }
  }

  private void onEarlierWrite(Throwable failure) {
    if (failure != null) {
      markFailed();
    }
  }

  private void onFlushWritten(Throwable failure) {
    if (failure != null) {
      markFailed();
      return;
    }
    synchronized (this) {
      if (failed || queue.isEmpty()) {
        flushing = false;
        return;
      }
    }
    scheduleFlush();
  }

  private void markFailed() {
    synchronized (this) {
      failed = true;
      flushing = false;
      queue.clear();
    }
  }

  static String lineFrame(LogLine line, long dropped) {
    StringWriter out = new StringWriter(line.text().length() + 64);
    try (JsonWriter json = new JsonWriter(out)) {
      json.beginObject();
      json.name("type").value("log");
      json.name("line").value(line.text());
      json.name("seq").value(line.seq());
      if (dropped > 0L) {
        json.name("dropped").value(dropped);
      }
      json.endObject();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    return out.toString();
  }

  static String batchFrame(List<LogLine> lines, long dropped) {
    StringWriter out = new StringWriter(lines.size() * 96 + 64);
    try (JsonWriter json = new JsonWriter(out)) {
      json.beginObject();
      json.name("type").value("logs");
      json.name("lines").beginArray();
      for (LogLine line : lines) {
        json.beginObject();
        json.name("seq").value(line.seq());
        json.name("line").value(line.text());
        json.endObject();
      }
      json.endArray();
      if (dropped > 0L) {
        json.name("dropped").value(dropped);
      }
      json.endObject();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    return out.toString();
  }
}
