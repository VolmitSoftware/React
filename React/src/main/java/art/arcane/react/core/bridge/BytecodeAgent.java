package art.arcane.react.core.bridge;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.lang.instrument.Instrumentation;
import java.util.List;

public final class BytecodeAgent {
    private static final String UNAVAILABLE_PROPERTY = "react.bytecode-agent.unavailable";
    private static final AgentGate GATE = new AgentGate(
        new JvmAttachment(), BytecodeAgent::runOnAttachThread, BytecodeAgent::runOnServerThread, new JvmMemory(), new LogReport());

    private BytecodeAgent() {}

    /** Starts the single attach attempt for this JVM and returns without waiting for it. */
    public static void install() {
        GATE.request();
    }

    /**
     * Runs the hook now when the agent is attached, on the server thread once a pending attach
     * succeeds, and never when the agent is unavailable.
     */
    public static void whenAttached(Runnable hook) {
        GATE.whenAttached(hook);
    }

    public static boolean isInstalled() {
        return GATE.attached();
    }

    public static Instrumentation instrumentation() {
        return GATE.instrumentation();
    }

    public static List<Object> transformations() {
        return List.of();
    }

    private static void runOnServerThread(Runnable hook) {
        // Patching a server class takes far longer than the job queue's tick budget, which would
        // hold back every later hook, so each one runs as its own server task.
        J.sync(hook, 1);
    }

    private static void runOnAttachThread(Runnable attempt) {
        Thread thread = new Thread(attempt, "React Agent Attach");
        thread.setDaemon(true);
        thread.start();
    }

    private static final class JvmAttachment implements AgentGate.Attachment {
        @Override
        public Instrumentation existing() {
            try {
                return ByteBuddyAgent.getInstrumentation();
            } catch (Throwable notInstalled) {
                return null;
            }
        }

        @Override
        public Instrumentation attach() {
            return ByteBuddyAgent.install();
        }
    }

    /** A System property outlives a hotload, which replaces this class and its static state. */
    private static final class JvmMemory implements AgentGate.Memory {
        @Override
        public String recall() {
            return System.getProperty(UNAVAILABLE_PROPERTY);
        }

        @Override
        public void remember(String reason) {
            System.setProperty(UNAVAILABLE_PROPERTY, reason);
        }
    }

    private static final class LogReport implements AgentGate.Report {
        @Override
        public void attached() {
            React.info("Bytecode agent attached.");
        }

        @Override
        public void unavailable(String reason) {
            React.warn("Bytecode agent unavailable: " + reason
                + ". Features that patch server code stay in measurement-only mode.");
        }

        @Override
        public void attachFailure(Throwable failure) {
            React.verbose("Bytecode agent attach failure", failure);
        }

        @Override
        public void hookFailure(Throwable failure) {
            React.reportError("Could not install a bytecode hook", failure);
        }
    }
}
