package art.arcane.react.mod;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Server-thread scheduling driven by the loader's end-of-server-tick event. Work handed over from another thread runs
 * at the start of the next tick, then every repeating task runs once. A failing task is reported and keeps its slot,
 * like a Bukkit repeating task.
 */
final class ModTicks implements AutoCloseable {
    private final ConcurrentLinkedQueue<Runnable> later = new ConcurrentLinkedQueue<>();
    private final List<Runnable> repeating = new CopyOnWriteArrayList<>();
    private final Consumer<String> warnings;
    private volatile boolean closed;

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
        for (int pending = later.size(); pending > 0; pending--) {
            Runnable work = later.poll();
            if (work == null) {
                break;
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
        repeating.clear();
    }
}
