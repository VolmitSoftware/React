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

package art.arcane.react.content.tweak;

import art.arcane.react.api.tweak.ReactTweak;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@art.arcane.react.util.config.ConfigDescription("Configuration for Projectile Limiter tweak. Applies per-player and per-chunk projectile burst caps to prevent projectile spam spikes.")
public class TweakProjectileLimiter extends ReactTweak implements Listener {
    public static final String ID = "projectile-limiter";
    @art.arcane.react.util.config.ConfigDoc(value = "Rolling window length for player checks (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
    private int playerWindowMS = 1000;
    @art.arcane.react.util.config.ConfigDoc(value = "Rolling window length for chunk checks (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
    private int chunkWindowMS = 1000;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum projectiles allowed per player window in projectile limiter.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxProjectilesPerPlayerWindow = 40;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum projectiles allowed per chunk window in projectile limiter.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxProjectilesPerChunkWindow = 160;
    @art.arcane.react.util.config.ConfigDoc(value = "Main evaluation interval for projectile limiter in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
    private int cleanupIntervalMS = 5000;
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether projectile limiter applies limit player projectiles.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean limitPlayerProjectiles = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether projectile limiter applies limit chunk projectiles.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean limitChunkProjectiles = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Permission node string checked before projectile limiter enforcement.", impact = "Change this to match your permission model for bypass behavior.")
    private String bypassPermission = "react.bypass.projectile-limit";
    private transient Map<UUID, BurstWindow> playerBursts = new HashMap<>();
    private transient Map<ChunkKey, BurstWindow> chunkBursts = new HashMap<>();
    private transient long lastCleanup = 0;

    public TweakProjectileLimiter() {
        super(ID);
    }

    @Override
    public void onActivate() {
        playerBursts = new HashMap<>();
        chunkBursts = new HashMap<>();
        lastCleanup = 0;
    }

    @Override
    public void onDeactivate() {
        playerBursts.clear();
        chunkBursts.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(ProjectileLaunchEvent event) {
        long now = System.currentTimeMillis();
        cleanup(now);

        if (limitPlayerProjectiles) {
            ProjectileSource source = event.getEntity().getShooter();
            if (source instanceof Player player) {
                if (!player.hasPermission(bypassPermission)) {
                    BurstWindow window = playerBursts.computeIfAbsent(player.getUniqueId(), k -> new BurstWindow(now));
                    if (!window.increment(playerWindowMS, maxProjectilesPerPlayerWindow, now)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        if (limitChunkProjectiles) {
            Location location = event.getEntity().getLocation();
            Chunk chunk = location.getChunk();
            ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
            BurstWindow window = chunkBursts.computeIfAbsent(key, k -> new BurstWindow(now));
            if (!window.increment(chunkWindowMS, maxProjectilesPerChunkWindow, now)) {
                event.setCancelled(true);
            }
        }
    }

    private void cleanup(long now) {
        if (lastCleanup > 0 && now - lastCleanup < cleanupIntervalMS) {
            return;
        }

        lastCleanup = now;
        long playerExpiry = Math.max(1000L, playerWindowMS * 4L);
        long chunkExpiry = Math.max(1000L, chunkWindowMS * 4L);

        Iterator<Map.Entry<UUID, BurstWindow>> playerIterator = playerBursts.entrySet().iterator();
        while (playerIterator.hasNext()) {
            if (now - playerIterator.next().getValue().lastHit > playerExpiry) {
                playerIterator.remove();
            }
        }

        Iterator<Map.Entry<ChunkKey, BurstWindow>> chunkIterator = chunkBursts.entrySet().iterator();
        while (chunkIterator.hasNext()) {
            if (now - chunkIterator.next().getValue().lastHit > chunkExpiry) {
                chunkIterator.remove();
            }
        }
    }

    @Override
    public int getTickInterval() {
        return cleanupIntervalMS;
    }

    @Override
    public void onTick() {
        cleanup(System.currentTimeMillis());
    }

    private static final class BurstWindow {
        @art.arcane.react.util.config.ConfigDoc(value = "Internal timestamp used by projectile limiter to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
        private long start;
        @art.arcane.react.util.config.ConfigDoc(value = "Internal timestamp used by projectile limiter to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
        private long lastHit;
        @art.arcane.react.util.config.ConfigDoc(value = "Internal counter used by projectile limiter while tracking burst activity.", impact = "Primarily runtime state; React updates this automatically during live evaluation.")
        private int count;

        private BurstWindow(long now) {
            start = now;
            lastHit = now;
            count = 0;
        }

        private boolean increment(int windowMS, int limit, long now) {
            if (windowMS <= 0) {
                return true;
            }

            if (now - start > windowMS) {
                start = now;
                count = 0;
            }

            lastHit = now;
            count++;
            return count <= Math.max(1, limit);
        }
    }

    private static final class ChunkKey {
        @art.arcane.react.util.config.ConfigDoc(value = "World identifier used by projectile limiter internal tracking.", impact = "This is runtime identity data and should normally be left to automatic updates.")
        private final UUID world;
        @art.arcane.react.util.config.ConfigDoc(value = "X-axis coordinate used by projectile limiter internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
        private final int x;
        @art.arcane.react.util.config.ConfigDoc(value = "Z-axis coordinate used by projectile limiter internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
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
