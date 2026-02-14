/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FeaturePortalTrafficSmoother extends ReactFeature implements Listener {
    public static final String ID = "portal-traffic-smoother";
    private int tickIntervalMS = 2000;
    private int windowMS = 1000;
    private int maxPlayerPortalsPerChunkWindow = 6;
    private int maxEntityPortalsPerChunkWindow = 16;
    private int cooloffMS = 5000;
    private int playerDelayTicks = 2;
    private int entityDelayTicks = 4;
    private int maxQueuedDelays = 512;
    private boolean onlyDuringPressure = true;
    private double pressureIncidentScore = 40;
    private double pressureTickMS = 52;
    private boolean bypassNearPlayers = true;
    private double bypassPlayerRadius = 10;
    private transient Map<ChunkKey, PortalWindow> windows = new ConcurrentHashMap<>();
    private transient Map<UUID, Long> delayed = new ConcurrentHashMap<>();

    public FeaturePortalTrafficSmoother() {
        super(ID);
    }

    @Override
    public void onActivate() {
        windows = new ConcurrentHashMap<>();
        delayed = new ConcurrentHashMap<>();
    }

    @Override
    public void onDeactivate() {
        windows.clear();
        delayed.clear();
    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        long windowExpiry = Math.max(windowMS * 5L, cooloffMS * 2L);

        for (Map.Entry<ChunkKey, PortalWindow> entry : windows.entrySet()) {
            PortalWindow window = entry.getValue();
            if (now - window.lastHit > windowExpiry && now >= window.throttleUntil) {
                windows.remove(entry.getKey(), window);
            }
        }

        for (Map.Entry<UUID, Long> entry : delayed.entrySet()) {
            if (entry.getValue() <= now) {
                delayed.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(PlayerPortalEvent event) {
        Location destination = event.getTo();
        if (destination == null) {
            return;
        }

        if (!shouldManage(destination)) {
            return;
        }

        if (!isThrottled(destination, true)) {
            return;
        }

        Player player = event.getPlayer();
        if (delayed.size() >= maxQueuedDelays) {
            event.setCancelled(true);
            return;
        }

        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        if (now < delayed.getOrDefault(id, 0L)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        delayed.put(id, now + 10000L);
        Location to = destination.clone();
        int delay = Math.max(1, playerDelayTicks);
        J.ss(() -> {
            Player online = Bukkit.getPlayer(id);
            if (online != null && online.isOnline() && !online.isDead()) {
                online.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
            delayed.remove(id);
        }, delay);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(EntityPortalEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }

        Location destination = event.getTo();
        if (destination == null) {
            return;
        }

        if (!shouldManage(destination)) {
            return;
        }

        if (!isThrottled(destination, false)) {
            return;
        }

        if (delayed.size() >= maxQueuedDelays) {
            event.setCancelled(true);
            return;
        }

        Entity entity = event.getEntity();
        UUID id = entity.getUniqueId();
        long now = System.currentTimeMillis();
        if (now < delayed.getOrDefault(id, 0L)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        delayed.put(id, now + 10000L);
        Location to = destination.clone();
        int delay = Math.max(1, entityDelayTicks);
        J.ss(() -> {
            Entity live = Bukkit.getEntity(id);
            if (live != null && live.isValid() && !live.isDead()) {
                live.teleport(to);
            }
            delayed.remove(id);
        }, delay);
    }

    private boolean shouldManage(Location location) {
        if (bypassNearPlayers && React.hasNearbyPlayer(location, bypassPlayerRadius)) {
            return false;
        }

        if (!onlyDuringPressure) {
            return true;
        }

        return sample(SamplerTickTime.ID) >= pressureTickMS
                || sample(SamplerIncidentScore.ID) >= pressureIncidentScore;
    }

    private boolean isThrottled(Location location, boolean player) {
        if (location.getWorld() == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        ChunkKey key = new ChunkKey(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        PortalWindow window = windows.computeIfAbsent(key, k -> new PortalWindow(now));
        window.rollover(windowMS, now);

        if (player) {
            window.players++;
        } else {
            window.entities++;
        }

        int limit = player ? maxPlayerPortalsPerChunkWindow : maxEntityPortalsPerChunkWindow;
        int count = player ? window.players : window.entities;

        if (count > limit) {
            window.throttleUntil = Math.max(window.throttleUntil, now + cooloffMS);
        }

        return now < window.throttleUntil;
    }

    private double sample(String id) {
        var sampler = React.sampler(id);
        return sampler == null ? 0D : sampler.sample();
    }

    private static final class PortalWindow {
        private long start;
        private long lastHit;
        private long throttleUntil;
        private int players;
        private int entities;

        private PortalWindow(long now) {
            start = now;
            lastHit = now;
            throttleUntil = 0;
        }

        private void rollover(int windowMS, long now) {
            if (now - start > windowMS) {
                start = now;
                players = 0;
                entities = 0;
            }

            lastHit = now;
        }
    }

    private static final class ChunkKey {
        private final UUID world;
        private final int x;
        private final int z;

        private ChunkKey(UUID world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof ChunkKey key)) {
                return false;
            }

            return x == key.x && z == key.z && world.equals(key.world);
        }

        @Override
        public int hashCode() {
            int result = world.hashCode();
            result = 31 * result + x;
            result = 31 * result + z;
            return result;
        }
    }
}
