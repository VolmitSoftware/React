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

@art.arcane.react.util.config.ConfigDescription("Configuration for Spawn Burst Limiter feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureSpawnBurstLimiter extends ReactFeature implements Listener {
    public static final String ID = "spawn-burst-limiter";
    @art.arcane.react.util.config.ConfigDoc(value = "Main evaluation interval for spawn burst limiter in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
    private int tickIntervalMS = 5000;
    @art.arcane.react.util.config.ConfigDoc(value = "Rolling enforcement window length used by spawn burst limiter (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
    private int windowMS = 1200;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum spawns allowed per chunk window in spawn burst limiter.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxSpawnsPerChunkWindow = 22;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum spawner spawns allowed per chunk window in spawn burst limiter.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxSpawnerSpawnsPerChunkWindow = 10;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum monster spawns allowed per chunk window in spawn burst limiter.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxMonsterSpawnsPerChunkWindow = 15;
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether spawn burst limiter applies enforce natural spawns.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean enforceNaturalSpawns = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether spawn burst limiter applies enforce spawner spawns.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean enforceSpawnerSpawns = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Controls whether spawn burst limiter applies enforce monster spawns.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean enforceMonsterSpawns = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Skips named entities when spawn burst limiter evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
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
        @art.arcane.react.util.config.ConfigDoc(value = "Internal timestamp used by spawn burst limiter to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
        private long start;
        @art.arcane.react.util.config.ConfigDoc(value = "Internal timestamp used by spawn burst limiter to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
        private long lastHit;
        @art.arcane.react.util.config.ConfigDoc(value = "Internal counter used by spawn burst limiter while tracking burst activity.", impact = "Primarily runtime state; React updates this automatically during live evaluation.")
        private int total;
        @art.arcane.react.util.config.ConfigDoc(value = "Internal counter used by spawn burst limiter while tracking burst activity.", impact = "Primarily runtime state; React updates this automatically during live evaluation.")
        private int spawner;
        @art.arcane.react.util.config.ConfigDoc(value = "Internal counter used by spawn burst limiter while tracking burst activity.", impact = "Primarily runtime state; React updates this automatically during live evaluation.")
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
        @art.arcane.react.util.config.ConfigDoc(value = "World identifier used by spawn burst limiter internal tracking.", impact = "This is runtime identity data and should normally be left to automatic updates.")
        private final UUID world;
        @art.arcane.react.util.config.ConfigDoc(value = "X-axis coordinate used by spawn burst limiter internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
        private final int x;
        @art.arcane.react.util.config.ConfigDoc(value = "Z-axis coordinate used by spawn burst limiter internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
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
