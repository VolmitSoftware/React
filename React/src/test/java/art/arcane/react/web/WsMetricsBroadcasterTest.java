package art.arcane.react.web;

import art.arcane.react.api.web.dto.SamplerDto;
import art.arcane.react.api.web.resource.MetricsResource;
import art.arcane.react.api.web.ws.WebSocketSessions;
import art.arcane.react.api.web.ws.WsChannel;
import art.arcane.react.api.web.ws.WsMetricsBroadcaster;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WsMetricsBroadcasterTest {
  @Test
  void returnsZeroAndDoesNoWorkWithoutSessions() {
    WebSocketSessions sessions = new WebSocketSessions();
    AtomicInteger supplierCalls = new AtomicInteger();
    Supplier<MetricsResource.SnapshotResponse> supplier = () -> {
      supplierCalls.incrementAndGet();
      return snapshot(1L);
    };
    WsMetricsBroadcaster broadcaster = new WsMetricsBroadcaster(supplier, sessions, value -> "FRAME");

    assertEquals(0, broadcaster.broadcastOnce());
    assertEquals(0, supplierCalls.get());
  }

  @Test
  void broadcastsOneSerializationToEverySession() {
    WebSocketSessions sessions = new WebSocketSessions();
    FakeWsChannel first = new FakeWsChannel("first");
    FakeWsChannel second = new FakeWsChannel("second");
    sessions.add(first);
    sessions.add(second);
    AtomicInteger serializerCalls = new AtomicInteger();
    Function<MetricsResource.SnapshotResponse, String> serializer = value -> {
      serializerCalls.incrementAndGet();
      return "FRAME";
    };
    WsMetricsBroadcaster broadcaster = new WsMetricsBroadcaster(() -> snapshot(1L), sessions, serializer);

    assertEquals(2, broadcaster.broadcastOnce());
    assertEquals(1, serializerCalls.get());
    assertEquals(List.of("FRAME"), first.received);
    assertEquals(List.of("FRAME"), second.received);
  }

  @Test
  void skipsDuplicateSnapshotSequence() {
    WebSocketSessions sessions = new WebSocketSessions();
    FakeWsChannel channel = new FakeWsChannel("only");
    sessions.add(channel);
    AtomicInteger sequence = new AtomicInteger(4);
    WsMetricsBroadcaster broadcaster = new WsMetricsBroadcaster(
        () -> snapshot(sequence.get()),
        sessions,
        value -> "SEQ:" + value.sequence()
    );

    assertEquals(1, broadcaster.broadcastOnce());
    assertEquals(0, broadcaster.broadcastOnce());
    sequence.incrementAndGet();
    assertEquals(1, broadcaster.broadcastOnce());
    assertEquals(List.of("SEQ:4", "SEQ:5"), channel.received);
  }

  private static MetricsResource.SnapshotResponse snapshot(long sequence) {
    return new MetricsResource.SnapshotResponse(sequence, 1_000L, new SamplerDto[0]);
  }

  private static final class FakeWsChannel implements WsChannel {
    private final String id;
    private final List<String> received;

    FakeWsChannel(String id) {
      this.id = id;
      this.received = new ArrayList<>();
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public void send(String text) {
      received.add(text);
    }
  }
}
