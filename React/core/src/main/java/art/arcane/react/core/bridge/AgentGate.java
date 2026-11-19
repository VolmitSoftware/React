package art.arcane.react.core.bridge;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Makes at most one attach attempt and keeps it off the calling thread. A refused attach can
 * block for seconds, so callers queue their hooks instead of waiting for the result.
 */
final class AgentGate {
    enum State { IDLE, PENDING, ATTACHED, UNAVAILABLE }

    interface Attachment {
        Instrumentation existing();

        Instrumentation attach() throws Throwable;
    }

    interface Memory {
        String recall();

        void remember(String reason);
    }

    interface Report {
        void attached();

        void unavailable(String reason);

        void attachFailure(Throwable failure);

        void hookFailure(Throwable failure);
    }

    private final Attachment attachment;
    private final Executor attachThread;
    private final Executor serverThread;
    private final Memory memory;
    private final Report report;
    private final List<Runnable> waiting = new ArrayList<>();
    private State state = State.IDLE;
    private Instrumentation instrumentation;

    AgentGate(Attachment attachment, Executor attachThread, Executor serverThread, Memory memory, Report report) {
        this.attachment = attachment;
        this.attachThread = attachThread;
        this.serverThread = serverThread;
        this.memory = memory;
        this.report = report;
    }

    synchronized State state() {
        return state;
    }

    /** True without starting an attempt when an agent is attached or was already loaded into this JVM. */
    synchronized boolean attached() {
        return state == State.ATTACHED || (state == State.IDLE && attachment.existing() != null);
    }

    synchronized Instrumentation instrumentation() {
        return instrumentation;
    }

    void request() {
        String unavailableReason = null;
        boolean attached = false;
        synchronized (this) {
            if (state != State.IDLE) {
                return;
            }
            Instrumentation existing = attachment.existing();
            if (existing != null) {
                instrumentation = existing;
                state = State.ATTACHED;
                attached = true;
            } else {
                unavailableReason = memory.recall();
                if (unavailableReason != null) {
                    state = State.UNAVAILABLE;
                } else {
                    state = State.PENDING;
                }
            }
        }
        if (attached) {
            report.attached();
            return;
        }
        if (unavailableReason != null) {
            report.unavailable(unavailableReason);
            return;
        }
        try {
            attachThread.execute(this::attempt);
        } catch (RuntimeException failure) {
            resolve(null, failure);
        }
    }

    void whenAttached(Runnable hook) {
        request();
        synchronized (this) {
            if (state == State.PENDING) {
                waiting.add(hook);
                return;
            }
            if (state != State.ATTACHED) {
                return;
            }
        }
        hook.run();
    }

    private void attempt() {
        Instrumentation attached = null;
        Throwable failure = null;
        try {
            attached = attachment.attach();
        } catch (Throwable thrown) {
            failure = thrown;
        }
        resolve(attached, failure);
    }

    private void resolve(Instrumentation attached, Throwable failure) {
        List<Runnable> hooks;
        String reason = null;
        synchronized (this) {
            hooks = new ArrayList<>(waiting);
            waiting.clear();
            if (attached != null) {
                instrumentation = attached;
                state = State.ATTACHED;
            } else {
                reason = failure == null
                    ? "the attach returned no instrumentation"
                    : "the JVM refused the self-attach (" + failure.getClass().getSimpleName() + ")";
                memory.remember(reason);
                state = State.UNAVAILABLE;
            }
        }
        if (attached == null) {
            report.unavailable(reason);
            if (failure != null) {
                report.attachFailure(failure);
            }
            return;
        }
        report.attached();
        for (Runnable hook : hooks) {
            serverThread.execute(() -> runHook(hook));
        }
    }

    private void runHook(Runnable hook) {
        try {
            hook.run();
        } catch (Throwable failure) {
            report.hookFailure(failure);
        }
    }
}
