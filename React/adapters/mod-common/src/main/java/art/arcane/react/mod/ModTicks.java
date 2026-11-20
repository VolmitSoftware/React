package art.arcane.react.mod;

import art.arcane.react.core.telemetry.TickerDiagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Server-thread scheduling driven by the loader's end-of-server-tick event. Work handed over from another thread runs
 * at the start of the next tick, delayed work once its tick comes, then every repeating task runs once. A failing task
 * is reported and keeps its slot, like a Bukkit repeating task. Every run counts toward {@link #diagnostics}; a run's queue
 * wait is the time from the start of its tick until it begins.
 */
final class ModTicks implements AutoCloseable {
    private final ConcurrentLinkedQueue<Runnable> later = new ConcurrentLinkedQueue<>();
    private final PriorityQueue<Delayed> delayed = new PriorityQueue<>(Comparator.comparingLong(Delayed::due).thenComparingLong(Delayed::order));
    private final List<Runnable> repeating = new CopyOnWriteArrayList<>();
    private final Consumer<String> warnings;
    private long tickNumber;
    private long delayedOrder;
    private final LongAdder submitted = new LongAdder();
    private final LongAdder executed = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder runtimeNanos = new LongAdder();
    private final LongAdder queueNanos = new LongAdder();
    private final AtomicLong maxQueueNanos = new AtomicLong();
    private final AtomicInteger active = new AtomicInteger();
    private volatile boolean closed;

    private record Delayed(long due, long order, Runnable work) {
    }

    ModTicks(Consumer<String> warnings) {
        this.warnings = Objects.requireNonNull(warnings);
    }

    /** False once closed. */
    boolean later(Runnable work) {
        if (closed) {
            return false;
        }
        later.add(Objects.requireNonNull(work));
        submitted.increment();
        return true;
    }

    /** Runs {@code work} on the tick {@code delayTicks} ticks from now; false once closed. */
    boolean later(Runnable work, int delayTicks) {
        if (delayTicks <= 0) {
            return later(work);
        }
        Objects.requireNonNull(work);
        synchronized (delayed) {
            if (closed) {
                return false;
            }
            delayed.add(new Delayed(tickNumber + delayTicks, delayedOrder++, work));
        }
        submitted.increment();
        return true;
    }

    int pending() {
        synchronized (delayed) {
            return later.size() + delayed.size();
        }
    }

    AutoCloseable every(Runnable tick) {
        if (closed) {
            throw new IllegalStateException("Server ticks are closed");
        }
        Runnable entry = Objects.requireNonNull(tick)::run;
        repeating.add(entry);
        return () -> repeating.remove(entry);
    }

    void tick() {
        if (closed) {
            return;
        }
        long tickStarted = System.nanoTime();
        List<Runnable> due = new ArrayList<>();
        synchronized (delayed) {
            tickNumber++;
            while (!delayed.isEmpty() && delayed.peek().due() <= tickNumber) {
                due.add(delayed.poll().work());
            }
        }
        for (int pending = later.size(); pending > 0; pending--) {
            Runnable work = later.poll();
            if (work == null) {
                break;
            }
            run(work, tickStarted);
        }
        for (Runnable work : due) {
            if (closed) {
                return;
            }
            run(work, tickStarted);
        }
        for (Runnable entry : repeating) {
            if (closed) {
                return;
            }
            submitted.increment();
            run(entry, tickStarted);
        }
    }

    TickerDiagnostics diagnostics() {
        return new TickerDiagnostics(submitted.sum(), executed.sum(), failures.sum(), 0L, 0L, 0L, runtimeNanos.sum(),
                queueNanos.sum(), maxQueueNanos.get(), active.get());
    }

    private void run(Runnable work, long tickStarted) {
        long started = System.nanoTime();
        long waited = Math.max(0L, started - tickStarted);
        queueNanos.add(waited);
        maxQueueNanos.accumulateAndGet(waited, Math::max);
        active.incrementAndGet();
        try {
            work.run();
        } catch (RuntimeException failure) {
            failures.increment();
            warnings.accept("Server task failed: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        } finally {
            active.decrementAndGet();
            executed.increment();
            runtimeNanos.add(System.nanoTime() - started);
        }
    }

    @Override
    public void close() {
        closed = true;
        later.clear();
        synchronized (delayed) {
            delayed.clear();
        }
        repeating.clear();
    }
}
