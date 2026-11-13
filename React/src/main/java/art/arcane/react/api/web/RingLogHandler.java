package art.arcane.react.api.web;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;

public class RingLogHandler extends Handler {
    /** Console colour and cursor codes (ANSI CSI sequences); the web shows plain text. */
    private static final Pattern TERMINAL_CODES = Pattern.compile("\u001B\\[[0-?]*[ -/]*[@-~]");
    /**
     * A pairing link's code. The console shows the link to whoever ran the command, as the Animus editor does; the web
     * console must not, since a token that may read the console could redeem a stronger link first.
     */
    private static final Pattern PAIRING_CODES = Pattern.compile("RCT3\\.[A-Za-z0-9_-]+");

    public static final int DEFAULT_CAPACITY = 1000;

    private final int capacity;
    private final ArrayDeque<LogLine> ring;
    private final ReentrantLock lock;
    private final AtomicLong sequence;
    private volatile Consumer<LogLine> lineListener;

    public RingLogHandler() {
        this(DEFAULT_CAPACITY);
    }

    public RingLogHandler(int capacity) {
        this(capacity, new AtomicLong());
    }

    /** {@code sequence} may outlive this handler so line numbers keep rising across listener restarts. */
    public RingLogHandler(int capacity, AtomicLong sequence) {
        this.capacity = capacity;
        this.ring = new ArrayDeque<>(capacity);
        this.lock = new ReentrantLock();
        this.sequence = sequence;
        this.lineListener = null;
        setFormatter(new SimpleFormatter());
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null) {
            return;
        }
        java.util.logging.Formatter formatter = getFormatter();
        String message = formatter != null ? formatter.formatMessage(record) : record.getMessage();
        publishExternal(record.getLevel().getName(), message, record.getThrown());
    }

    public void publishExternal(String level, String message, Throwable thrown) {
        String normalizedLevel = level == null || level.isBlank() ? "INFO" : level;
        List<String> lines = formatLines(normalizedLevel, message, thrown);
        lock.lock();
        try {
            Consumer<LogLine> listener = lineListener;
            for (String text : lines) {
                LogLine line = new LogLine(sequence.incrementAndGet(), text);
                while (ring.size() >= capacity) {
                    ring.pollFirst();
                }
                ring.addLast(line);
                // Listeners only enqueue; running them under the lock keeps sequence order across threads.
                if (listener != null) {
                    try {
                        listener.accept(line);
                    } catch (Exception ignored) {
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private List<String> formatLines(String level, String message, Throwable thrown) {
        if (message == null) {
            message = "";
        }
        List<String> rawLines = new ArrayList<>(Arrays.asList(message.split("\\R", -1)));
        while (rawLines.size() > 1 && rawLines.get(rawLines.size() - 1).isEmpty()) {
            rawLines.remove(rawLines.size() - 1);
        }
        if (thrown != null) {
            StringWriter stack = new StringWriter();
            thrown.printStackTrace(new PrintWriter(stack));
            String[] stackLines = stack.toString().split("\\R");
            rawLines.addAll(Arrays.asList(stackLines));
        }
        String prefix = "[" + level + "] ";
        List<String> lines = new ArrayList<>(rawLines.size());
        for (String rawLine : rawLines) {
            lines.add(prefix + PAIRING_CODES.matcher(TERMINAL_CODES.matcher(rawLine).replaceAll("")).replaceAll("RCT3.(hidden)"));
        }
        return lines;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        lineListener = null;
    }

    public List<String> recent(int n) {
        return tail(n).lines();
    }

    public Tail tail(int n) {
        lock.lock();
        try {
            LogLine newest = ring.peekLast();
            long lastSeq = newest == null ? sequence.get() : newest.seq();
            if (n <= 0) {
                return new Tail(new ArrayList<>(), lastSeq);
            }
            int size = ring.size();
            int skip = Math.max(0, size - n);
            List<String> result = new ArrayList<>(Math.min(n, size));
            int index = 0;
            for (LogLine entry : ring) {
                if (index >= skip) {
                    result.add(entry.text());
                }
                index++;
            }
            return new Tail(result, lastSeq);
        } finally {
            lock.unlock();
        }
    }

    public record Tail(List<String> lines, long lastSeq) {
    }

    public void setLineListener(Consumer<LogLine> listener) {
        this.lineListener = listener;
    }

    public void clearLineListener() {
        this.lineListener = null;
    }

    public int size() {
        lock.lock();
        try {
            return ring.size();
        } finally {
            lock.unlock();
        }
    }
}
