package art.arcane.react.web;

import art.arcane.react.api.web.WebRevisions;
import art.arcane.react.api.web.resource.MetricsResource;
import art.arcane.react.api.web.ws.CoalescingWsChannel;
import art.arcane.react.api.web.ws.WebSocketSessions;
import art.arcane.react.api.web.ws.WsMetricsBroadcaster;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WsMetricsBroadcasterTest {
  private static final WebRevisions.Snapshot REVISIONS = new WebRevisions.Snapshot(1L, 1L, 1L, 1L, 1L, 1L);

  @Test
  void returnsZeroAndDoesNoWorkWithoutSessions() {
    WebSocketSessions<CoalescingWsChannel> sessions = new WebSocketSessions<>();
    AtomicInteger supplierCalls = new AtomicInteger();
    Supplier<MetricsResource.LiveFrame> supplier = () -> {
      supplierCalls.incrementAndGet();
      return frame(1L, REVISIONS);
    };
    WsMetricsBroadcaster broadcaster = new WsMetricsBroadcaster(supplier, sessions, value -> "FRAME");

    assertEquals(0, broadcaster.broadcastOnce());
    assertEquals(0, supplierCalls.get());
  }

  @Test
  void broadcastsOneSerializationToEverySession() {
    WebSocketSessions<CoalescingWsChannel> sessions = new WebSocketSessions<>();
    RecordingTransport first = new RecordingTransport("first");
    RecordingTransport second = new RecordingTransport("second");
    sessions.add(new CoalescingWsChannel(first));
    sessions.add(new CoalescingWsChannel(second));
    AtomicInteger serializerCalls = new AtomicInteger();
    Function<MetricsResource.LiveFrame, String> serializer = value -> {
      serializerCalls.incrementAndGet();
      return "FRAME";
    };
    WsMetricsBroadcaster broadcaster = new WsMetricsBroadcaster(() -> frame(1L, REVISIONS), sessions, serializer);

    assertEquals(2, broadcaster.broadcastOnce());
    assertEquals(1, serializerCalls.get());
    assertEquals(List.of("FRAME"), first.sent());
    assertEquals(List.of("FRAME"), second.sent());
  }

  @Test
  void skipsUnchangedFramesAndPushesWhenSequenceOrRevisionsMove() {
    WebSocketSessions<CoalescingWsChannel> sessions = new WebSocketSessions<>();
    RecordingTransport transport = new RecordingTransport("only");
    sessions.add(new CoalescingWsChannel(transport));
    AtomicInteger sequence = new AtomicInteger(4);
    AtomicReference<WebRevisions.Snapshot> revisions = new AtomicReference<>(REVISIONS);
    WsMetricsBroadcaster broadcaster = new WsMetricsBroadcaster(
        () -> frame(sequence.get(), revisions.get()),
        sessions,
        value -> "SEQ:" + value.sequence() + ":" + value.revisions().tweaks()
    );

    assertEquals(1, broadcaster.broadcastOnce());
    assertEquals(0, broadcaster.broadcastOnce());
    sequence.incrementAndGet();
    assertEquals(1, broadcaster.broadcastOnce());
    revisions.set(new WebRevisions.Snapshot(1L, 2L, 1L, 1L, 1L, 1L));
    assertEquals(1, broadcaster.broadcastOnce());
    assertEquals(0, broadcaster.broadcastOnce());
    assertEquals(List.of("SEQ:4:1", "SEQ:5:1", "SEQ:5:2"), transport.sent());
  }

  @Test
  void sendsTheCurrentFrameToANewSocketOnlyWithoutWaitingForACapture() {
    WebSocketSessions<CoalescingWsChannel> sessions = new WebSocketSessions<>();
    RecordingTransport open = new RecordingTransport("open");
    RecordingTransport admitted = new RecordingTransport("admitted");
    CoalescingWsChannel openChannel = new CoalescingWsChannel(open);
    CoalescingWsChannel admittedChannel = new CoalescingWsChannel(admitted);
    sessions.add(openChannel);
    sessions.add(admittedChannel);
    WsMetricsBroadcaster broadcaster = new WsMetricsBroadcaster(
        () -> frame(7L, REVISIONS),
        sessions,
        value -> "SEQ:" + value.sequence()
    );

    assertEquals(2, broadcaster.broadcastOnce());

    broadcaster.sendCurrent(admittedChannel);

    assertEquals(List.of("SEQ:7", "SEQ:7"), admitted.sent());
    assertEquals(List.of("SEQ:7"), open.sent());
    assertEquals(0, broadcaster.broadcastOnce());
  }

  private static MetricsResource.LiveFrame frame(long sequence, WebRevisions.Snapshot revisions) {
    return new MetricsResource.LiveFrame(sequence, 1_000L, new MetricsResource.LiveSampleDto[0], revisions);
  }
}
