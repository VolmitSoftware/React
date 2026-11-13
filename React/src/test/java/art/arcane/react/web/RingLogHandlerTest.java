package art.arcane.react.web;

import art.arcane.react.api.web.LogLine;
import art.arcane.react.api.web.RingLogHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RingLogHandlerTest {

    @Test
    void boundedRingDropsOldestAndRetainsCapacity() {
        RingLogHandler handler = new RingLogHandler(3);
        for (int i = 0; i < 5; i++) {
            LogRecord record = new LogRecord(Level.INFO, "m" + i);
            handler.publish(record);
        }
        List<String> result = handler.recent(10);
        assertEquals(3, result.size());
        assertTrue(result.get(0).contains("m2"), "oldest retained line should contain m2: " + result.get(0));
        assertTrue(result.get(1).contains("m3"), "middle retained line should contain m3: " + result.get(1));
        assertTrue(result.get(2).contains("m4"), "newest retained line should contain m4: " + result.get(2));
        for (String line : result) {
            assertTrue(line.contains("INFO"), "line must contain level INFO: " + line);
        }
    }

    @Test
    void terminalColourCodesAreStrippedFromLines() {
        RingLogHandler handler = new RingLogHandler(10);
        handler.publishExternal("INFO",
            "\u001B[38;2;170;170;170m[\u001B[38;2;85;255;255mReact\u001B[38;2;170;170;170m]\u001B[0m: \u001B[1mRole:\u001B[0m admin", null);
        assertEquals(List.of("[INFO] [React]: Role: admin"), handler.recent(10));
    }

    @Test
    void pairingLinksNeverReachTheWebConsole() {
        RingLogHandler handler = new RingLogHandler(10);
        handler.publishExternal("INFO", "[React]: Link ready. Open it within 10 minutes: https://react.volmitsoftware.com/pair#RCT3.eyJ2IjoxLCJzZWNyZXQiOiJ4In0", null);
        assertEquals(List.of("[INFO] [React]: Link ready. Open it within 10 minutes: https://react.volmitsoftware.com/pair#RCT3.(hidden)"),
            handler.recent(10));
    }

    @Test
    void recentNAndSizeReflectBoundedState() {
        RingLogHandler handler = new RingLogHandler(3);
        for (int i = 0; i < 5; i++) {
            LogRecord record = new LogRecord(Level.INFO, "m" + i);
            handler.publish(record);
        }
        List<String> last2 = handler.recent(2);
        assertEquals(2, last2.size());
        assertTrue(last2.get(0).contains("m3"), "first of last-2 should be m3: " + last2.get(0));
        assertTrue(last2.get(1).contains("m4"), "second of last-2 should be m4: " + last2.get(1));
        List<String> empty = handler.recent(0);
        assertEquals(0, empty.size());
        assertEquals(3, handler.size());
    }

    @Test
    void lineListenerReceivesPublishedLines() {
        RingLogHandler handler = new RingLogHandler(10);
        List<String> received = new ArrayList<>();
        handler.setLineListener(line -> received.add(line.text()));
        handler.publish(new LogRecord(Level.WARNING, "first"));
        handler.publish(new LogRecord(Level.WARNING, "second"));
        assertEquals(2, received.size());
        assertTrue(received.get(0).contains("first"), "listener line 0 should contain 'first': " + received.get(0));
        assertTrue(received.get(1).contains("second"), "listener line 1 should contain 'second': " + received.get(1));
        handler.clearLineListener();
        handler.publish(new LogRecord(Level.WARNING, "third"));
        assertEquals(2, received.size(), "listener must not be called after clearLineListener");
    }

    @Test
    void publishNullDoesNotThrowAndDoesNotChangeSize() {
        RingLogHandler handler = new RingLogHandler(5);
        handler.publish(new LogRecord(Level.INFO, "before"));
        handler.publish(null);
        assertEquals(1, handler.size());
    }

    @Test
    void multilineMessagesAndThrownStackTracesArePreservedAsConsoleLines() {
        RingLogHandler handler = new RingLogHandler(256);
        LogRecord record = new LogRecord(Level.SEVERE, "first line\nsecond line");
        record.setThrown(new IllegalStateException("broken console record"));

        handler.publish(record);

        List<String> lines = handler.recent(256);
        assertTrue(lines.stream().anyMatch(line -> line.contains("first line")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("second line")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("IllegalStateException: broken console record")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("RingLogHandlerTest")));
    }

    @Test
    void externalConsoleRecordsUseTheSameBoundedLinePipeline() {
        RingLogHandler handler = new RingLogHandler(3);

        handler.publishExternal("WARN", "first\nsecond\nthird\nfourth", null);

        List<String> lines = handler.recent(3);
        assertEquals(3, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.startsWith("[WARN] ")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("first")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("fourth")));
    }

    @Test
    void listenerSeesLinesInSequenceOrderFromManyThreads() throws Exception {
        RingLogHandler handler = new RingLogHandler(64);
        List<LogLine> received = new ArrayList<>();
        handler.setLineListener(received::add);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        for (int thread = 0; thread < 8; thread++) {
            int id = thread;
            pool.execute(() -> {
                try {
                    start.await();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int line = 0; line < 500; line++) {
                    handler.publishExternal("INFO", "t" + id + "-" + line, null);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(4_000, received.size());
        for (int index = 0; index < received.size(); index++) {
            assertEquals(index + 1L, received.get(index).seq(), "listener order must match seq order");
        }
        assertEquals(4_000L, handler.tail(1).lastSeq());
    }

    @Test
    void sharedSequenceKeepsRisingAcrossHandlers() {
        AtomicLong sequence = new AtomicLong();
        RingLogHandler first = new RingLogHandler(8, sequence);
        first.publishExternal("INFO", "a\nb", null);
        RingLogHandler second = new RingLogHandler(8, sequence);
        second.publishExternal("INFO", "c", null);
        RingLogHandler.Tail tail = second.tail(10);
        assertEquals(List.of("[INFO] c"), tail.lines());
        assertEquals(3L, tail.lastSeq());
        assertEquals(3L, new RingLogHandler(8, sequence).tail(5).lastSeq(), "an empty ring reports the last issued seq");
    }
}
