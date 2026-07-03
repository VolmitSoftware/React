package art.arcane.react.web;

import art.arcane.react.api.web.ws.CoalescingWsChannel;
import art.arcane.react.api.web.ws.WsChannel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoalescingWsChannelTest {

    private static final class FakeWsChannel implements WsChannel {
        private final String channelId;
        private final List<String> received = new ArrayList<>();
        private volatile CountDownLatch blockLatch;

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
        public synchronized void send(String text) {
            CountDownLatch latch = blockLatch;
            if (latch != null) {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            received.add(text);
        }

        List<String> received() {
            return received;
        }

        void setBlockLatch(CountDownLatch latch) {
            this.blockLatch = latch;
        }
    }

    @Test
    void it_forwards_single_frame_with_inline_executor() {
        FakeWsChannel delegate = new FakeWsChannel("ch1");
        CoalescingWsChannel coalescing = new CoalescingWsChannel(delegate, Runnable::run);
        coalescing.send("A");
        assertEquals(List.of("A"), delegate.received());
    }

    @Test
    void it_drops_oldest_and_keeps_newest_when_drain_deferred() {
        FakeWsChannel delegate = new FakeWsChannel("ch2");
        List<Runnable> captured = new ArrayList<>();
        CoalescingWsChannel coalescing = new CoalescingWsChannel(delegate, captured::add);

        coalescing.send("A");
        coalescing.send("B");
        coalescing.send("C");

        assertEquals(1, captured.size(), "Expected exactly one drain task, not one per send");

        captured.get(0).run();

        assertEquals(List.of("C"), delegate.received());
    }

    @Test
    void it_does_not_block_caller_when_delegate_blocks() throws InterruptedException {
        FakeWsChannel delegate = new FakeWsChannel("ch3");
        CountDownLatch blockLatch = new CountDownLatch(1);
        delegate.setBlockLatch(blockLatch);

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-ws-send");
            t.setDaemon(true);
            return t;
        });

        CoalescingWsChannel coalescing = new CoalescingWsChannel(delegate, executor);

        AtomicBoolean sendReturned = new AtomicBoolean(false);

        long startNs = System.nanoTime();
        coalescing.send("X");
        sendReturned.set(true);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        assertTrue(sendReturned.get(), "send() must return on the caller thread immediately");
        assertTrue(elapsedMs < 1000L, "send() must return in under 1s; took " + elapsedMs + "ms");

        blockLatch.countDown();

        long deadline = System.currentTimeMillis() + 5000L;
        while (delegate.received().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(List.of("X"), delegate.received(), "Frame must eventually be delivered after latch released");

        executor.shutdownNow();
    }

    @Test
    void it_delegates_id_and_isOpen() {
        FakeWsChannel delegate = new FakeWsChannel("delegate-id");
        CoalescingWsChannel coalescing = new CoalescingWsChannel(delegate, Runnable::run);
        assertEquals("delegate-id", coalescing.id());
        assertTrue(coalescing.isOpen());
    }

    @Test
    void it_does_not_schedule_second_drain_task_during_active_drain() throws InterruptedException {
        FakeWsChannel delegate = new FakeWsChannel("ch5");
        List<Runnable> captured = new ArrayList<>();
        CoalescingWsChannel coalescing = new CoalescingWsChannel(delegate, captured::add);

        coalescing.send("A");
        assertEquals(1, captured.size());

        coalescing.send("B");
        assertEquals(1, captured.size(), "No second task should be scheduled while drain is still pending");

        captured.get(0).run();
        assertEquals(List.of("B"), delegate.received());

        captured.clear();
        coalescing.send("C");
        assertEquals(1, captured.size(), "After drain completes, next send should schedule a new task");
        captured.get(0).run();
        assertEquals(List.of("B", "C"), delegate.received());
    }

    @Test
    void it_handles_send_after_drain_completes() {
        FakeWsChannel delegate = new FakeWsChannel("ch6");
        List<Runnable> captured = new ArrayList<>();
        CoalescingWsChannel coalescing = new CoalescingWsChannel(delegate, captured::add);

        coalescing.send("first");
        assertEquals(1, captured.size());
        captured.get(0).run();
        assertEquals(List.of("first"), delegate.received());

        captured.clear();
        coalescing.send("second");
        assertEquals(1, captured.size());
        captured.get(0).run();
        assertEquals(List.of("first", "second"), delegate.received());
    }

    @Test
    void it_swallows_exceptions_from_delegate_send() {
        WsChannel throwingDelegate = new WsChannel() {
            @Override
            public String id() {
                return "thrower";
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void send(String text) {
                throw new RuntimeException("delegate failed");
            }
        };
        CoalescingWsChannel coalescing = new CoalescingWsChannel(throwingDelegate, Runnable::run);
        assertFalse(throwsAny(() -> coalescing.send("boom")), "CoalescingWsChannel must swallow delegate exceptions");
    }

    private boolean throwsAny(Runnable r) {
        try {
            r.run();
            return false;
        } catch (Throwable t) {
            return true;
        }
    }
}
