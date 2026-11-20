package art.arcane.react.mod;

import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.Subscription;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * React's entity ticker on mods, as Bukkit's entity controller runs it: each server tick it samples up to
 * {@link #PER_WORLD_UPDATES_PER_TICK} entities per world in rotation, refreshes their priority, crowding and nearest
 * player when ten seconds have passed, and then calls the tick listeners. Entities that start being tracked, which is
 * spawning and chunk loading, are refreshed right away and get the pause and doze flags they carry reconciled; the
 * entities already loaded when React starts are reconciled on its first tick.
 */
public final class ModEntityTicker {
    static final int PER_WORLD_UPDATES_PER_TICK = 15;
    private static final long MANAGED_CLEANUP_TIMEOUT_MS = 30_000L;
    private static final long SCRATCH_SWEEP_INTERVAL_MS = 30_000L;
    private static volatile ModEntityTicker active;
    private final MinecraftServer server;
    private final Logger logger;
    private final List<Consumer<GameEntity>> allListeners = new CopyOnWriteArrayList<>();
    private final Map<String, List<Consumer<GameEntity>>> typeListeners = new ConcurrentHashMap<>();
    private final Map<ServerLevel, Cursor> cursors = new HashMap<>();
    private long lastSweepMs = System.currentTimeMillis();
    private boolean reconciledLoaded;

    private static final class Cursor {
        private List<Entity> snapshot = List.of();
        private int next;
    }

    ModEntityTicker(MinecraftServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    void start() {
        ReactEntity.resumeManagedCleanup();
        EntityPriority.active().rebuildPriority();
        active = this;
    }

    void stop() {
        if (active == this) {
            active = null;
        }
        allListeners.clear();
        typeListeners.clear();
        cursors.clear();
        ReactEntity.releaseAllManagedState();
        if (!ReactEntity.awaitManagedCleanup(MANAGED_CLEANUP_TIMEOUT_MS)) {
            logger.warn("React entity cleanup did not drain within {}ms", MANAGED_CLEANUP_TIMEOUT_MS);
        }
        ReactEntity.clearScratch();
    }

    Subscription subscribe(Collection<String> types, Consumer<GameEntity> listener) {
        if (listener == null) {
            return Subscription.NONE;
        }
        List<String> registered = types == null ? List.of() : List.copyOf(types);
        if (registered.isEmpty()) {
            allListeners.add(listener);
        } else {
            for (String type : registered) {
                typeListeners.computeIfAbsent(type, (String ignored) -> new CopyOnWriteArrayList<>()).add(listener);
            }
        }
        return () -> {
            allListeners.removeIf((Consumer<GameEntity> candidate) -> candidate == listener);
            for (String type : registered) {
                List<Consumer<GameEntity>> listeners = typeListeners.get(type);
                if (listeners != null) {
                    listeners.removeIf((Consumer<GameEntity> candidate) -> candidate == listener);
                    if (listeners.isEmpty()) {
                        typeListeners.remove(type, listeners);
                    }
                }
            }
        };
    }

    void tick() {
        if (!reconciledLoaded) {
            reconciledLoaded = true;
            reconcileLoaded();
        }
        long now = System.currentTimeMillis();
        if (now - lastSweepMs >= SCRATCH_SWEEP_INTERVAL_MS) {
            lastSweepMs = now;
            ReactEntity.sweepScratch();
        }
        if (!hasListeners()) {
            return;
        }
        EntityPriority priority = EntityPriority.active();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : next(level)) {
                tick(entity, priority);
            }
        }
    }

    /** An entity started being tracked: it spawned, or its chunk loaded. */
    public static void trackingStarted(Entity entity) {
        ModEntityTicker ticker = active;
        if (ticker == null || entity == null) {
            return;
        }
        try {
            ReactEntity.reconcileManagedState(new ModGameEntity(entity));
            if (ticker.hasListeners()) {
                ticker.tick(entity, EntityPriority.active());
            }
        } catch (RuntimeException failure) {
            ticker.logger.warn("React could not reconcile a loaded entity", failure);
        }
    }

    /**
     * Refreshes an entity right away, as Bukkit's entity controller does on damage, targeting, interaction, pose
     * changes, healing, taming, item drops, item merges and breeding.
     */
    public static void poke(Entity entity) {
        ModEntityTicker ticker = active;
        if (ticker == null || entity == null || !(entity.level() instanceof ServerLevel) || !ticker.hasListeners()) {
            return;
        }
        try {
            ticker.tick(entity, EntityPriority.active());
        } catch (RuntimeException failure) {
            ticker.logger.warn("React could not refresh an entity", failure);
        }
    }

    /** An entity stopped being tracked; unloads give back what React paused or dozed, as Bukkit does on unload. */
    public static void trackingEnded(Entity entity) {
        ModEntityTicker ticker = active;
        if (ticker == null || entity == null) {
            return;
        }
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) {
            return;
        }
        try {
            ReactEntity.releaseManagedState(new ModGameEntity(entity));
        } catch (RuntimeException failure) {
            ticker.logger.warn("React could not release an unloaded entity", failure);
        }
    }

    private void reconcileLoaded() {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : snapshot(level)) {
                try {
                    ReactEntity.reconcileManagedState(new ModGameEntity(entity));
                } catch (RuntimeException failure) {
                    logger.warn("React could not reconcile a loaded entity", failure);
                }
            }
        }
    }

    private void tick(Entity entity, EntityPriority priority) {
        GameEntity game = new ModGameEntity(entity);
        if (!ReactEntity.tick(game, priority)) {
            return;
        }
        for (Consumer<GameEntity> listener : allListeners) {
            dispatch(listener, game);
        }
        List<Consumer<GameEntity>> listeners = typeListeners.get(game.type());
        if (listeners == null) {
            return;
        }
        for (Consumer<GameEntity> listener : listeners) {
            dispatch(listener, game);
        }
    }

    private void dispatch(Consumer<GameEntity> listener, GameEntity entity) {
        try {
            listener.accept(entity);
        } catch (RuntimeException failure) {
            logger.error("Entity tick listener crashed", failure);
        }
    }

    private List<Entity> next(ServerLevel level) {
        Cursor cursor = cursors.computeIfAbsent(level, (ServerLevel ignored) -> new Cursor());
        List<Entity> sampled = new ArrayList<>(PER_WORLD_UPDATES_PER_TICK);
        boolean refreshed = false;
        while (sampled.size() < PER_WORLD_UPDATES_PER_TICK) {
            if (cursor.next >= cursor.snapshot.size()) {
                if (refreshed) {
                    break;
                }
                cursor.snapshot = snapshot(level);
                cursor.next = 0;
                refreshed = true;
                if (cursor.snapshot.isEmpty()) {
                    break;
                }
            }
            Entity entity = cursor.snapshot.get(cursor.next++);
            if (!entity.isRemoved()) {
                sampled.add(entity);
            }
        }
        return sampled;
    }

    private static List<Entity> snapshot(ServerLevel level) {
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }

    private boolean hasListeners() {
        if (!allListeners.isEmpty()) {
            return true;
        }
        for (List<Consumer<GameEntity>> listeners : typeListeners.values()) {
            if (!listeners.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
