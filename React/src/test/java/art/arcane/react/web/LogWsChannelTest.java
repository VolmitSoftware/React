package art.arcane.react.web;

import art.arcane.react.api.web.LogLine;
import art.arcane.react.api.web.ws.LogWsChannel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogWsChannelTest {

  @Test
  void singleLineFramesKeepTheOldShapeAndAddSeq() {
    RecordingTransport transport = new RecordingTransport("single");
    LogWsChannel channel = new LogWsChannel(transport, Runnable::run, false, 16);

    channel.offer(new LogLine(7L, "[INFO] \"quoted\" line"));

    JsonObject frame = JsonParser.parseString(transport.sent().getFirst()).getAsJsonObject();
    assertEquals("log", frame.get("type").getAsString());
    assertEquals("[INFO] \"quoted\" line", frame.get("line").getAsString());
    assertEquals(7L, frame.get("seq").getAsLong());
    assertFalse(frame.has("dropped"));
  }

  @Test
  void linesQueuedDuringAWriteGoOutInOrderInOneBatch() {
    RecordingTransport transport = new RecordingTransport("batch");
    transport.hold();
    LogWsChannel channel = new LogWsChannel(transport, Runnable::run, true, 1024);

    for (long seq = 1L; seq <= 500L; seq++) {
      channel.offer(new LogLine(seq, "line-" + seq));
    }
    assertEquals(1, transport.sent().size(), "only the first line is in flight");
    transport.release();

    List<Long> seqs = new ArrayList<>();
    for (String raw : transport.sent()) {
      JsonObject frame = JsonParser.parseString(raw).getAsJsonObject();
      assertEquals("logs", frame.get("type").getAsString());
      for (JsonElement element : frame.getAsJsonArray("lines")) {
        JsonObject line = element.getAsJsonObject();
        seqs.add(line.get("seq").getAsLong());
        assertEquals("line-" + line.get("seq").getAsLong(), line.get("line").getAsString());
      }
    }
    assertEquals(500, seqs.size());
    for (int index = 0; index < seqs.size(); index++) {
      assertEquals(index + 1L, seqs.get(index));
    }
    assertTrue(transport.sent().size() <= 3, "499 waiting lines need at most two batches of 256");
  }

  @Test
  void theBoundDropsTheOldestLinesAndReportsTheCount() {
    RecordingTransport transport = new RecordingTransport("bounded");
    transport.hold();
    LogWsChannel channel = new LogWsChannel(transport, Runnable::run, true, 10);

    for (long seq = 1L; seq <= 26L; seq++) {
      channel.offer(new LogLine(seq, "line-" + seq));
    }
    assertEquals(10, channel.queued());
    transport.release();

    JsonObject second = JsonParser.parseString(transport.sent().get(1)).getAsJsonObject();
    assertEquals(15L, second.get("dropped").getAsLong(), "lines 2-16 were dropped while line 1 was in flight");
    JsonArray lines = second.getAsJsonArray("lines");
    assertEquals(17L, lines.get(0).getAsJsonObject().get("seq").getAsLong());
    assertEquals(26L, lines.get(lines.size() - 1).getAsJsonObject().get("seq").getAsLong());
  }

  @Test
  void singleLineModeReportsDropsOnTheNextFrame() {
    RecordingTransport transport = new RecordingTransport("single-bounded");
    transport.hold();
    LogWsChannel channel = new LogWsChannel(transport, Runnable::run, false, 2);
    for (long seq = 1L; seq <= 5L; seq++) {
      channel.offer(new LogLine(seq, "line-" + seq));
    }
    transport.release();
    transport.release();

    List<String> sent = transport.sent();
    JsonObject firstAfterDrop = JsonParser.parseString(sent.get(1)).getAsJsonObject();
    assertEquals(2L, firstAfterDrop.get("dropped").getAsLong());
    assertEquals(4L, firstAfterDrop.get("seq").getAsLong());
    JsonObject last = JsonParser.parseString(sent.get(2)).getAsJsonObject();
    assertEquals(5L, last.get("seq").getAsLong());
    assertFalse(last.has("dropped"));
  }

  @Test
  void aFailedWriteClosesTheChannelAndClearsItsQueue() {
    RecordingTransport transport = new RecordingTransport("failing");
    transport.hold();
    LogWsChannel channel = new LogWsChannel(transport, Runnable::run, true, 16);
    channel.offer(new LogLine(1L, "a"));
    channel.offer(new LogLine(2L, "b"));
    transport.failHeld();
    assertFalse(channel.isOpen());
    assertEquals(0, channel.queued());
    channel.offer(new LogLine(3L, "c"));
    assertEquals(1, transport.sent().size());
  }

  @Test
  void aRejectedFlushIsRetriedByTheNextLine() {
    RecordingTransport transport = new RecordingTransport("rejected");
    boolean[] reject = {true};
    LogWsChannel channel = new LogWsChannel(transport, task -> {
      if (reject[0]) {
        throw new RejectedExecutionException("full");
      }
      task.run();
    }, true, 16);
    channel.offer(new LogLine(1L, "a"));
    assertTrue(transport.sent().isEmpty());
    reject[0] = false;
    channel.offer(new LogLine(2L, "b"));
    JsonArray lines = JsonParser.parseString(transport.sent().getFirst()).getAsJsonObject().getAsJsonArray("lines");
    assertEquals(2, lines.size());
  }
}
