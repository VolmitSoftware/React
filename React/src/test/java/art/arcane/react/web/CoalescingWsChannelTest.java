package art.arcane.react.web;

import art.arcane.react.api.web.ws.CoalescingWsChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoalescingWsChannelTest {

    @Test
    void it_forwards_a_frame_when_idle() {
        RecordingTransport transport = new RecordingTransport("ch1");
        CoalescingWsChannel channel = new CoalescingWsChannel(transport);
        channel.send("A");
        channel.send("B");
        assertEquals(List.of("A", "B"), transport.sent());
    }

    @Test
    void it_keeps_one_write_in_flight_and_sends_only_the_newest_pending_frame() {
        RecordingTransport transport = new RecordingTransport("ch2");
        transport.hold();
        CoalescingWsChannel channel = new CoalescingWsChannel(transport);
        long coalescedBefore = CoalescingWsChannel.coalescedFrames();

        channel.send("A");
        channel.send("B");
        channel.send("C");
        channel.send("D");

        assertEquals(List.of("A"), transport.sent(), "nothing else is written while A is in flight");
        assertEquals(1, transport.heldWrites());
        assertEquals(coalescedBefore + 2L, CoalescingWsChannel.coalescedFrames());

        transport.release();

        assertEquals(List.of("A", "D"), transport.sent());
        channel.send("E");
        assertEquals(List.of("A", "D", "E"), transport.sent());
    }

    @Test
    void a_stalled_client_never_blocks_the_caller() {
        RecordingTransport transport = new RecordingTransport("ch3");
        transport.hold();
        CoalescingWsChannel channel = new CoalescingWsChannel(transport);
        long started = System.nanoTime();
        for (int index = 0; index < 10_000; index++) {
            channel.send("frame-" + index);
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        assertTrue(elapsedMs < 1_000L, "sends must not wait for the socket; took " + elapsedMs + " ms");
        assertEquals(1, transport.heldWrites());
        transport.release();
        assertEquals(List.of("frame-0", "frame-9999"), transport.sent());
    }

    @Test
    void a_failed_write_closes_the_channel() {
        RecordingTransport transport = new RecordingTransport("ch4");
        transport.hold();
        CoalescingWsChannel channel = new CoalescingWsChannel(transport);
        channel.send("A");
        channel.send("B");
        transport.failHeld();
        assertFalse(channel.isOpen());
        channel.send("C");
        assertEquals(List.of("A"), transport.sent());
    }

    @Test
    void it_delegates_id_ping_and_close() {
        RecordingTransport transport = new RecordingTransport("delegate-id");
        CoalescingWsChannel channel = new CoalescingWsChannel(transport);
        assertEquals("delegate-id", channel.id());
        assertTrue(channel.isOpen());
        channel.ping();
        assertEquals(1, transport.pings());
        channel.close(4401, "Token revoked");
        assertEquals(4401, transport.closeCode());
        assertFalse(channel.isOpen());
    }
}
