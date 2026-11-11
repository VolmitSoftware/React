package art.arcane.react.core.bridge;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One feature's claim on the bytecode agent. The agent can arrive after the feature has been
 * deactivated or reactivated, so a hook only installs for the activation that requested it.
 */
public final class AgentHookSlot {
    private final Consumer<Runnable> agent;
    private final BooleanSupplier attached;
    private final AtomicInteger activation = new AtomicInteger();

    public AgentHookSlot() {
        this(BytecodeAgent::whenAttached, BytecodeAgent::isInstalled);
    }

    AgentHookSlot(Consumer<Runnable> agent, BooleanSupplier attached) {
        this.agent = agent;
        this.attached = attached;
    }

    public void install(Runnable hook) {
        int requested = activation.incrementAndGet();
        agent.accept(() -> {
            if (activation.get() == requested) {
                hook.run();
            }
        });
    }

    /** Drops a hook that is still waiting. Without an agent no hook exists, so nothing is uninstalled. */
    public void release(Runnable uninstall) {
        activation.incrementAndGet();
        if (attached.getAsBoolean()) {
            uninstall.run();
        }
    }
}
