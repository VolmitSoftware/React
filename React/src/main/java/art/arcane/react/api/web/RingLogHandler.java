package art.arcane.react.api.web;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public class RingLogHandler extends Handler {

    private static final int DEFAULT_CAPACITY = 256;

    private final int capacity;
    private final ArrayDeque<String> ring;
    private final ReentrantLock lock;
    private volatile Consumer<String> lineListener;

    public RingLogHandler() {
        this(DEFAULT_CAPACITY);
    }

    public RingLogHandler(int capacity) {
        this.capacity = capacity;
        this.ring = new ArrayDeque<>(capacity);
        this.lock = new ReentrantLock();
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
        for (String line : lines) {
            lock.lock();
            try {
                while (ring.size() >= capacity) {
                    ring.pollFirst();
                }
                ring.addLast(line);
            } finally {
                lock.unlock();
            }
            Consumer<String> listener = lineListener;
            if (listener != null) {
                try {
                    listener.accept(line);
                } catch (Exception ignored) {
                }
            }
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
            lines.add(prefix + rawLine);
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
        if (n <= 0) {
            return new ArrayList<>();
        }
        lock.lock();
        try {
            int size = ring.size();
            int skip = Math.max(0, size - n);
            List<String> result = new ArrayList<>(Math.min(n, size));
            int index = 0;
            for (String entry : ring) {
                if (index >= skip) {
                    result.add(entry);
                }
                index++;
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    public void setLineListener(Consumer<String> listener) {
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
