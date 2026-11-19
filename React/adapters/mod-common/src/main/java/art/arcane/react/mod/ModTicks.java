package art.arcane.react.mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Server-thread scheduling driven by the loader's end-of-server-tick event. Work handed over from another thread runs
 * at the start of the next tick, delayed work once its tick comes, then every repeating task runs once. A failing task
 * is reported and keeps its slot, like a Bukkit repeating task.
 */
final class ModTicks implements AutoCloseable {
    private final ConcurrentLinkedQueue<Runnable> later = new ConcurrentLinkedQueue<>();
    private final PriorityQueue<Delayed> delayed = new PriorityQueue<>(Comparator.comparingLong(Delayed::due).thenComparingLong(Delayed::order));
    private final List<Runnable> repeating = new CopyOnWriteArrayList<>();
    private final Consumer<String> warnings;
    private long tickNumber;
    private long delayedOrder;
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
            run(work);
        }
        for (Runnable work : due) {
            if (closed) {
                return;
            }
            run(work);
        }
        for (Runnable entry : repeating) {
            if (closed) {
                return;
            }
            run(entry);
        }
    }

    private void run(Runnable work) {
        try {
            work.run();
        } catch (RuntimeException failure) {
            warnings.accept("Server task failed: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
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
