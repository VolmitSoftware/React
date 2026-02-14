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

public class TweakProjectileLimiter extends ReactTweak implements Listener {
    public static final String ID = "projectile-limiter";
    private int playerWindowMS = 1000;
    private int chunkWindowMS = 1000;
    private int maxProjectilesPerPlayerWindow = 40;
    private int maxProjectilesPerChunkWindow = 160;
    private int cleanupIntervalMS = 5000;
    private boolean limitPlayerProjectiles = true;
    private boolean limitChunkProjectiles = true;
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
        private long start;
        private long lastHit;
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
