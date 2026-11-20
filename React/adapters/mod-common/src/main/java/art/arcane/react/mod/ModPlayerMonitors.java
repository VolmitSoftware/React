package art.arcane.react.mod;

import art.arcane.react.api.monitor.ActionBarMonitor;
import art.arcane.react.api.monitor.MonitorInput;
import art.arcane.react.api.monitor.MonitorViewer;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.model.PlayerSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each React user's saved settings and action bar monitor on a mod server, as React's player controller keeps them on
 * Bukkit. Joins, leaves and the monitor controls (sneak, double sneak, sneak-scroll) are read from the players on
 * every server tick, since loaders share no player events.
 */
final class ModPlayerMonitors {
    private static final long SAVE_INTERVAL_MS = 60_000L;
    private static final long SAVE_DRAIN_TIMEOUT_MS = 30_000L;
    private static final String USE_PERMISSION = "react.use";

    private static volatile ModPlayerMonitors current;

    private final MinecraftServer server;
    private final ModMonitorDisplay display;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    ModPlayerMonitors(MinecraftServer server, ModMonitorDisplay display) {
        this.server = server;
        this.display = display;
    }

    static void bind(ModPlayerMonitors monitors) {
        current = monitors;
    }

    static java.util.Optional<ModPlayerMonitors> current() {
        return java.util.Optional.ofNullable(current);
    }

    void tick() {
        long now = System.currentTimeMillis();
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            online.add(id);
            Session session = sessions.get(id);
            if (session == null) {
                if (ModPermissions.has(player, USE_PERMISSION)) {
                    join(player);
                }
                continue;
            }
            session.poll(player, now);
        }
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            if (!online.contains(id)) {
                Session session = sessions.remove(id);
                if (session != null) {
                    session.quit();
                }
            }
        }
    }

    /** Turns the player's monitor on or off; empty when the player may not use React. */
    Boolean toggle(ServerPlayer player) {
        Session session = session(player);
        if (session == null) {
            return null;
        }
        session.setMonitoring(!session.isMonitoring(), true);
        return session.isMonitoring();
    }

    /** The player's settings, loading them when the player has not been seen yet. */
    Session session(ServerPlayer player) {
        Session session = sessions.get(player.getUUID());
        if (session != null || !ModPermissions.has(player, USE_PERMISSION)) {
            return session;
        }
        return join(player);
    }

    void updateMonitors() {
        for (Session session : sessions.values()) {
            ActionBarMonitor monitor = session.monitor;
            if (monitor != null) {
                monitor.refreshConfiguration();
            }
        }
    }

    void stop() {
        if (current == this) {
            current = null;
        }
        List<Session> stopped = new ArrayList<>(sessions.values());
        sessions.clear();
        for (Session session : stopped) {
            session.quit();
        }
        PlayerSettings.flushPendingSaves(SAVE_DRAIN_TIMEOUT_MS);
    }

    private Session join(ServerPlayer player) {
        Session created = new Session(player, PlayerSettings.get(player.getUUID()));
        Session existing = sessions.putIfAbsent(player.getUUID(), created);
        if (existing != null) {
            return existing;
        }
        created.settings.init();
        if (created.settings.isActionBarMonitoring()) {
            created.setMonitoring(true, false);
            ReactLanguage.sendPrefixed(player, RuntimeMessages.MONITOR_ENABLED);
        }
        return created;
    }

    final class Session implements MonitorViewer {
        private final UUID id;
        private final PlayerSettings settings;
        private final MonitorInput input = new MonitorInput();
        private volatile ActionBarMonitor monitor;
        private ServerLevel level;
        private double lastY;
        private int lastSlot;
        private boolean lastShift;
        private long lastSave;
        private int lastHash;

        private Session(ServerPlayer player, PlayerSettings settings) {
            this.id = player.getUUID();
            this.settings = settings;
            this.level = VanillaApi.level(player);
            this.lastY = player.getY();
            this.lastSlot = VanillaApi.selectedSlot(player.getInventory());
            this.lastShift = player.isShiftKeyDown();
            this.lastSave = System.currentTimeMillis();
            this.lastHash = settings.hashCode();
        }

        @Override
        public UUID viewerId() {
            return id;
        }

        @Override
        public PlayerSettings getSettings() {
            return settings;
        }

        @Override
        public boolean isMonitorSneaking() {
            return input.isMonitorSneaking();
        }

        @Override
        public boolean isLocked() {
            return input.isLocked();
        }

        @Override
        public int getScrollPosition(int maxRemainder) {
            return input.getScrollPosition(maxRemainder);
        }

        boolean isMonitoring() {
            return monitor != null;
        }

        void save() {
            save(false);
        }

        private void poll(ServerPlayer player, long now) {
            ServerLevel currentLevel = VanillaApi.level(player);
            if (currentLevel == level) {
                input.moved(player.getY() - lastY);
            }
            level = currentLevel;
            lastY = player.getY();

            boolean shift = player.isShiftKeyDown();
            if (shift != lastShift) {
                lastShift = shift;
                input.sneak(shift, player.getAbilities().flying, now);
                wake();
            }
            int slot = VanillaApi.selectedSlot(player.getInventory());
            if (slot != lastSlot) {
                int previous = lastSlot;
                lastSlot = slot;
                if (input.scrolled(previous, slot, shift) && input.isSneaking()) {
                    wake();
                }
            }
            input.tick();
            if (now - lastSave >= SAVE_INTERVAL_MS) {
                lastSave = now;
                save(false);
            }
        }

        private void wake() {
            ActionBarMonitor active = monitor;
            if (active != null) {
                active.wakeUp();
            }
        }

        private void setMonitoring(boolean monitoring, boolean saveSetting) {
            if (monitoring == isMonitoring()) {
                return;
            }
            if (!monitoring) {
                monitor.stop();
                monitor = null;
            } else {
                ActionBarMonitor created = new ActionBarMonitor(this, display);
                monitor = created;
                created.start();
            }
            if (saveSetting) {
                settings.setActionBarMonitoring(isMonitoring());
                save(false);
            }
        }

        private void save(boolean force) {
            if (force || lastHash != settings.hashCode()) {
                PlayerSettings.saveSettings(id, settings);
            }
            lastHash = settings.hashCode();
        }

        private void quit() {
            setMonitoring(false, false);
            save(true);
        }
    }
}
