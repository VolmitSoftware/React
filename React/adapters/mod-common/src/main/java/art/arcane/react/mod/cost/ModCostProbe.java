package art.arcane.react.mod.cost;

import art.arcane.react.mod.ReactModRuntime;
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
 * ticks through the shared tick hooks, and world generation through {@link WorldgenCost}. Event attribution that fails
 * on this loader version is switched off once and tick attribution keeps running. Each drain folds world generation
 * into its owner's total and also keeps it apart, with the step breakdown, as the last {@link Worldgen} window.
 * React's own work is left out, as on Bukkit; its frames still keep that time off the owner that called into React.
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
    private volatile Worldgen worldgen = Worldgen.EMPTY;

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
        try {
            WorldgenCost.index(server.registryAccess(), owners);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("World generation cost is charged to unknown: {}", failure.toString());
        }
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
        WorldgenCost.clear();
        worldgen = Worldgen.EMPTY;
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
        Map<String, Long> worldgenNanos = new TreeMap<>();
        long[] stepNanos = new long[WorldgenStep.values().length];
        WorldgenCost.drainSteps(stepNanos);
        for (CostBucket bucket : CostRegistry.buckets()) {
            long nanos = bucket.nanos.sumThenReset();
            long sync = bucket.syncCalls.sumThenReset();
            long async = bucket.asyncCalls.sumThenReset();
            if (nanos == 0L && sync == 0L && async == 0L || bucket.ownerId().equals(ReactModRuntime.ID)) {
                continue;
            }
            if (bucket.kind() == CostKind.WORLDGEN) {
                worldgenNanos.merge(bucket.ownerId(), nanos, Long::sum);
            }
            consumer.accept(bucket.ownerId(), owners.displayName(bucket.ownerId()), bucket.kind().id(), nanos, sync, async);
        }
        worldgen = new Worldgen(Map.copyOf(worldgenNanos), stepNanos);
    }

    /** World generation in the last drained window. */
    public Worldgen worldgen() {
        return worldgen;
    }

    public String displayName(String ownerId) {
        return owners.displayName(ownerId);
    }

    /** One window of world generation: nanoseconds per owner and per {@link WorldgenStep} ordinal. */
    public record Worldgen(Map<String, Long> ownerNanos, long[] stepNanos) {
        static final Worldgen EMPTY = new Worldgen(Map.of(), new long[WorldgenStep.values().length]);

        public long ownerNanos(String owner) {
            Long nanos = ownerNanos.get(owner);
            return nanos == null ? 0L : nanos;
        }

        public long stepNanos(WorldgenStep step) {
            return stepNanos[step.ordinal()];
        }
    }

    /** One drained owner, for the console check. */
    public record OwnerCost(String ownerId, String displayName, long exclusiveNanos, long syncCalls, long asyncCalls) {
        public static final Comparator<OwnerCost> MOST_EXPENSIVE = Comparator.comparingLong(OwnerCost::exclusiveNanos).reversed();
    }

    /** Drains into one entry per owner, every kind summed. */
    public void drainInto(List<OwnerCost> target) {
        Map<String, OwnerCost> byOwner = new TreeMap<>();
        drain((String id, String name, String kind, long nanos, long sync, long async) -> byOwner.merge(id,
                new OwnerCost(id, name, nanos, sync, async),
                (OwnerCost a, OwnerCost b) -> new OwnerCost(id, a.displayName(), a.exclusiveNanos() + b.exclusiveNanos(),
                        a.syncCalls() + b.syncCalls(), a.asyncCalls() + b.asyncCalls())));
        target.addAll(byOwner.values());
    }
}
