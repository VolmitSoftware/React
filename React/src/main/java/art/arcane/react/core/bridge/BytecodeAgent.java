package art.arcane.react.core.bridge;

import net.bytebuddy.agent.ByteBuddyAgent;

import java.lang.instrument.Instrumentation;
import java.util.List;

public final class BytecodeAgent {
    private static volatile Instrumentation instrumentation = null;

    private BytecodeAgent() {}

    public static void install() {
        if (instrumentation != null) {
            return;
        }
        try {
            instrumentation = ByteBuddyAgent.install();
        } catch (Throwable ignored) {
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
