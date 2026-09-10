package art.arcane.react.core.bridge;

import art.arcane.react.React;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.lang.instrument.Instrumentation;
import java.util.List;

public final class BytecodeAgent {
    private static volatile Instrumentation instrumentation = null;

    private BytecodeAgent() {}

    public static synchronized void install() {
        if (instrumentation != null) {
            return;
        }
        try {
            instrumentation = ByteBuddyAgent.install();
        } catch (Throwable failure) {
            React.reportError("Could not attach React's bytecode agent", failure);
        }
    }

    public static boolean isInstalled() {
        return instrumentation != null;
    }

    public static Instrumentation instrumentation() {
        return instrumentation;
    }

    public static List<Object> transformations() {
        return List.of();
    }
}
