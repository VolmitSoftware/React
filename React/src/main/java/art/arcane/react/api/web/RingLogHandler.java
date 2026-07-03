package art.arcane.react.api.web;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
        String line = formatLine(record);
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

    private String formatLine(LogRecord record) {
        java.util.logging.Formatter formatter = getFormatter();
        String message = formatter != null ? formatter.formatMessage(record) : record.getMessage();
        if (message == null) {
            message = "";
        }
        String line = "[" + record.getLevel().getName() + "] " + message;
        while (line.endsWith("\n") || line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }
        return line;
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
