package art.arcane.react.web;

import art.arcane.react.api.web.RingLogHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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
        handler.setLineListener(received::add);
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
}
