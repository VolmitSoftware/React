package art.arcane.react.web;

import art.arcane.react.api.web.dto.SamplerDto;
import art.arcane.react.api.web.ws.WebSocketSessions;
import art.arcane.react.api.web.ws.WsChannel;
import art.arcane.react.api.web.ws.WsMetricsBroadcaster;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WsMetricsBroadcasterTest {

    private static final class FakeWsChannel implements WsChannel {
        private final String channelId;
        private final List<String> received = new ArrayList<>();

        FakeWsChannel(String channelId) {
            this.channelId = channelId;
        }

        @Override
        public String id() {
            return channelId;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void send(String text) {
            received.add(text);
        }

        List<String> received() {
            return received;
        }
    }

    @Test
    void it_returns_zero_and_does_no_work_when_no_sessions() {
        WebSocketSessions sessions = new WebSocketSessions();
        AtomicInteger supplierCallCount = new AtomicInteger(0);
        AtomicInteger serializerCallCount = new AtomicInteger(0);

        Supplier<SamplerDto[]> supplier = () -> {
            supplierCallCount.incrementAndGet();
            return new SamplerDto[0];
        };

        Function<SamplerDto[], String> serializer = dtos -> {
            serializerCallCount.incrementAndGet();
            return "FRAME";
        };

        WsMetricsBroadcaster broadcaster = new WsMetricsBroadcaster(supplier, sessions, serializer);
        int result = broadcaster.broadcastOnce();

        assertEquals(0, result);
        assertEquals(0, supplierCallCount.get());
        assertEquals(0, serializerCallCount.get());
    }

    @Test
    void it_serializes_current_snapshot_once_and_broadcasts_to_all_sessions() {
        WebSocketSessions sessions = new WebSocketSessions();
        FakeWsChannel channelA = new FakeWsChannel("a");
        FakeWsChannel channelB = new FakeWsChannel("b");
        sessions.add(channelA);
        sessions.add(channelB);

        SamplerDto dto = new SamplerDto();
        dto.id = "tps";
        SamplerDto[] fixedData = new SamplerDto[]{dto};

        AtomicInteger serializerCallCount = new AtomicInteger(0);

        Supplier<SamplerDto[]> supplier = () -> fixedData;

        Function<SamplerDto[], String> serializer = dtos -> {
            serializerCallCount.incrementAndGet();
            return "FRAME";
        };

        WsMetricsBroadcaster broadcaster = new WsMetricsBroadcaster(supplier, sessions, serializer);
        int result = broadcaster.broadcastOnce();

        assertEquals(2, result);
        assertEquals(1, serializerCallCount.get());
        assertEquals(List.of("FRAME"), channelA.received());
        assertEquals(List.of("FRAME"), channelB.received());
    }

    @Test
    void it_uses_live_snapshot_each_call() {
        WebSocketSessions sessions = new WebSocketSessions();
        FakeWsChannel channel = new FakeWsChannel("x");
        sessions.add(channel);

        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<SamplerDto[]> supplier = () -> {
            int n = callCount.incrementAndGet();
            SamplerDto[] dtos = new SamplerDto[n];
            for (int i = 0; i < n; i++) {
                dtos[i] = new SamplerDto();
            }
            return dtos;
        };

        AtomicReference<String> lastSerialized = new AtomicReference<>("");

        Function<SamplerDto[], String> serializer = dtos -> {
            String frame = "LEN:" + dtos.length;
            lastSerialized.set(frame);
            return frame;
        };

        WsMetricsBroadcaster broadcaster = new WsMetricsBroadcaster(supplier, sessions, serializer);

        broadcaster.broadcastOnce();
        assertEquals("LEN:1", lastSerialized.get());

        broadcaster.broadcastOnce();
        assertEquals("LEN:2", lastSerialized.get());

        assertEquals(List.of("LEN:1", "LEN:2"), channel.received());
    }
}
