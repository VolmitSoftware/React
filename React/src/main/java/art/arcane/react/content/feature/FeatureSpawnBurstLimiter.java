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

import art.arcane.react.api.feature.ReactFeature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class FeatureSpawnBurstLimiter extends ReactFeature implements Listener {
    public static final String ID = "spawn-burst-limiter";
    private int tickIntervalMS = 5000;
    private int windowMS = 1200;
    private int maxSpawnsPerChunkWindow = 22;
    private int maxSpawnerSpawnsPerChunkWindow = 10;
    private int maxMonsterSpawnsPerChunkWindow = 15;
    private boolean enforceNaturalSpawns = true;
    private boolean enforceSpawnerSpawns = true;
    private boolean enforceMonsterSpawns = true;
    private boolean ignoreNamedEntities = true;
    private transient Map<ChunkKey, SpawnWindow> windows = new HashMap<>();

    public FeatureSpawnBurstLimiter() {
        super(ID);
    }

    @Override
    public void onActivate() {
        windows = new HashMap<>();
    }

    @Override
    public void onDeactivate() {
        windows.clear();
    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        long expiryMS = Math.max(1000L, windowMS * 4L);
        Iterator<Map.Entry<ChunkKey, SpawnWindow>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().lastHit > expiryMS) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(CreatureSpawnEvent event) {
        if (!shouldEvaluate(event)) {
            return;
        }

        long now = System.currentTimeMillis();
        ChunkKey key = new ChunkKey(event.getLocation().getWorld().getUID(), event.getLocation().getBlockX() >> 4, event.getLocation().getBlockZ() >> 4);
        SpawnWindow window = windows.computeIfAbsent(key, k -> new SpawnWindow(now));
        window.rollover(windowMS, now);
        window.total++;

        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER || "TRIAL_SPAWNER".equals(event.getSpawnReason().name())) {
            window.spawner++;
        }

        if (event.getEntity() instanceof Monster) {
            window.monster++;
        }

        if (window.total > maxSpawnsPerChunkWindow) {
            event.setCancelled(true);
            return;
        }

        if (enforceSpawnerSpawns && window.spawner > maxSpawnerSpawnsPerChunkWindow) {
            event.setCancelled(true);
            return;
        }

        if (enforceMonsterSpawns && event.getEntity() instanceof Monster && window.monster > maxMonsterSpawnsPerChunkWindow) {
            event.setCancelled(true);
        }
    }

    private boolean shouldEvaluate(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (ignoreNamedEntities && entity.getCustomName() != null && !entity.getCustomName().isBlank()) {
            return false;
        }

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        boolean natural = switch (reason) {
            case NATURAL, CHUNK_GEN, NETHER_PORTAL, REINFORCEMENTS, JOCKEY, PATROL, RAID -> true;
            default -> false;
        };

        boolean spawner = reason == CreatureSpawnEvent.SpawnReason.SPAWNER || "TRIAL_SPAWNER".equals(reason.name());
        if (spawner && enforceSpawnerSpawns) {
            return true;
        }

        if (entity instanceof Monster && enforceMonsterSpawns) {
            return true;
        }

        return natural && enforceNaturalSpawns;
    }

    private static final class SpawnWindow {
        private long start;
        private long lastHit;
        private int total;
        private int spawner;
        private int monster;

        private SpawnWindow(long now) {
            start = now;
            lastHit = now;
        }

        private void rollover(int windowMS, long now) {
            if (now - start > windowMS) {
                start = now;
                total = 0;
                spawner = 0;
                monster = 0;
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
