package art.arcane.react.mod.cost;

import art.arcane.react.platform.cost.OwnerCostProbe;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executor;

/**
 * Per-mod cost on a mod server: event listeners through the loader's {@link EventAttribution}, block entity and entity
 * ticks through the shared tick hooks. Event attribution that fails on this loader version is switched off once and
 * tick attribution keeps running.
 */
public final class ModCostProbe implements OwnerCostProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("React");
    private static final int RECONCILE_TICKS = 100;
    private final ModOwners owners;
    private EventAttribution events;
    private String eventsDisabledReason;
    private volatile Executor serverExecutor;
    private volatile boolean active;
    private boolean installed;
    private int ticksUntilReconcile;

    /** {@code events} is null when this loader's dispatch could not be resolved; {@code reason} then says why. */
    public ModCostProbe(ModOwners owners, EventAttribution events, String reason) {
        this.owners = Objects.requireNonNull(owners, "owners");
        this.events = events;
        this.eventsDisabledReason = events == null ? Objects.requireNonNullElse(reason, "event attribution unavailable") : null;
        CostRegistry.owners(owners);
        if (events == null) {
            LOGGER.warn("Mod event cost attribution is off: {}", eventsDisabledReason);
        }
    }

    public void start(MinecraftServer server, Executor serverThread) {
        CostRegistry.serverThread = server.getRunningThread();
        serverExecutor = Objects.requireNonNull(serverThread, "serverThread");
        if (active) {
            serverThread.execute(this::apply);
        }
    }

    public void stop() {
        active = false;
        apply();
        serverExecutor = null;
        CostRegistry.serverThread = null;
    }

    /** Called every server tick on the server thread. */
    public void tick() {
        if (!installed || events == null || --ticksUntilReconcile > 0) {
            return;
        }
        ticksUntilReconcile = RECONCILE_TICKS;
        try {
            events.reconcile();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disableEvents(failure);
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String unavailableReason() {
        return "";
    }

    /** Why event listeners are not attributed, or null when they are. */
    public String eventAttributionProblem() {
        return eventsDisabledReason;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
        Executor executor = serverExecutor;
        if (executor == null) {
            return;
        }
        if (Thread.currentThread() == CostRegistry.serverThread) {
            apply();
        } else {
            executor.execute(this::apply);
        }
    }

    @Override
    public boolean active() {
        return active;
    }

    private void apply() {
        boolean wanted = active && serverExecutor != null;
        CostRegistry.ticksActive = wanted;
        if (wanted == installed) {
            return;
        }
        installed = wanted;
        if (events == null) {
            return;
        }
        try {
            if (wanted) {
                events.install();
                ticksUntilReconcile = RECONCILE_TICKS;
            } else {
                events.uninstall();
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disableEvents(failure);
        }
    }

    private void disableEvents(Throwable failure) {
        EventAttribution failed = events;
        events = null;
        eventsDisabledReason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        LOGGER.warn("Mod event cost attribution failed and is now off: {}", eventsDisabledReason);
        try {
            failed.uninstall();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            LOGGER.debug("Event attribution cleanup failed", ignored);
        }
    }

    @Override
    public void drain(OwnerCostConsumer consumer) {
        Map<String, long[]> totals = new TreeMap<>();
        for (CostBucket bucket : CostRegistry.buckets()) {
            long nanos = bucket.nanos.sumThenReset();
            long sync = bucket.syncCalls.sumThenReset();
            long async = bucket.asyncCalls.sumThenReset();
            if (nanos == 0L && sync == 0L && async == 0L) {
                continue;
            }
            long[] total = totals.computeIfAbsent(bucket.ownerId(), (String key) -> new long[3]);
            total[0] += nanos;
            total[1] += sync;
            total[2] += async;
        }
        for (Map.Entry<String, long[]> entry : totals.entrySet()) {
            long[] total = entry.getValue();
            consumer.accept(entry.getKey(), owners.displayName(entry.getKey()), total[0], total[1], total[2]);
        }
    }

    /** One drained owner, for the console check. */
    public record OwnerCost(String ownerId, String displayName, long exclusiveNanos, long syncCalls, long asyncCalls) {
        public static final Comparator<OwnerCost> MOST_EXPENSIVE = Comparator.comparingLong(OwnerCost::exclusiveNanos).reversed();
    }

    public void drainInto(List<OwnerCost> target) {
        drain((String id, String name, long nanos, long sync, long async) -> target.add(new OwnerCost(id, name, nanos, sync, async)));
    }
}
