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
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class FeatureChunkQuarantine extends ReactFeature implements Listener {
    public static final String ID = "chunk-quarantine";
    private int tickIntervalMS = 2500;
    private int windowMS = 1600;
    private int quarantineMS = 12000;
    private double scoreTrigger = 145;
    private int maxTrackedChunks = 4096;
    private boolean onlyDuringPressure = true;
    private double pressureIncidentScore = 48;
    private double pressureTickMS = 58;
    private boolean bypassNearPlayers = true;
    private double bypassPlayerRadius = 18;
    private boolean trackNaturalSpawns = true;
    private boolean trackSpawnerSpawns = true;
    private boolean trackRedstone = true;
    private boolean trackPhysics = true;
    private int samplePhysicsEveryN = 3;
    private boolean trackHoppers = true;
    private transient Map<ChunkKey, ChunkState> states = new ConcurrentHashMap<>();
    private transient volatile boolean pressure;

    public FeatureChunkQuarantine() {
        super(ID);
    }

    @Override
    public void onActivate() {
        states = new ConcurrentHashMap<>();
        pressure = false;
    }

    @Override
    public void onDeactivate() {
        states.clear();
        pressure = false;
    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    @Override
    public void onTick() {
        pressure = !onlyDuringPressure || isPressure();
        long now = System.currentTimeMillis();
        long expiry = Math.max(windowMS * 8L, quarantineMS * 2L);

        for (Map.Entry<ChunkKey, ChunkState> entry : states.entrySet()) {
            ChunkState state = entry.getValue();
            if (now - state.lastHit > expiry && now >= state.quarantinedUntil) {
                states.remove(entry.getKey(), state);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(CreatureSpawnEvent event) {
        if (!shouldTrackSpawn(event.getSpawnReason())) {
            return;
        }

        boolean quarantined = registerActivity(event.getLocation(), event.getEntity() instanceof Monster ? 1.4 : 1.0);
        if (!quarantined) {
            return;
        }

        if (bypassNearPlayers && React.hasNearbyPlayer(event.getLocation(), bypassPlayerRadius)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(BlockRedstoneEvent event) {
        if (!trackRedstone || event.getOldCurrent() == event.getNewCurrent()) {
            return;
        }

        Block block = event.getBlock();
        boolean quarantined = registerActivity(block.getLocation(), 0.8);
        if (!quarantined) {
            return;
        }

        if (bypassNearPlayers && React.hasNearbyPlayer(block.getLocation(), bypassPlayerRadius)) {
            return;
        }

        event.setNewCurrent(event.getOldCurrent());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(BlockPhysicsEvent event) {
        if (!trackPhysics) {
            return;
        }

        int stride = Math.max(1, samplePhysicsEveryN);
        if (stride > 1 && ThreadLocalRandom.current().nextInt(stride) != 0) {
            return;
        }

        Block block = event.getBlock();
        boolean quarantined = registerActivity(block.getLocation(), 0.45);
        if (!quarantined) {
            return;
        }

        if (bypassNearPlayers && React.hasNearbyPlayer(block.getLocation(), bypassPlayerRadius)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void on(InventoryMoveItemEvent event) {
        if (!trackHoppers) {
            return;
        }

        Location location = resolveHopperLocation(event);
        if (location == null) {
            return;
        }

        boolean quarantined = registerActivity(location, 0.6);
        if (!quarantined) {
            return;
        }

        if (bypassNearPlayers && React.hasNearbyPlayer(location, bypassPlayerRadius)) {
            return;
        }

        event.setCancelled(true);
    }

    private boolean registerActivity(Location location, double score) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        ChunkKey key = new ChunkKey(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        ChunkState state = states.get(key);
        long now = System.currentTimeMillis();

        if (state == null) {
            if (states.size() >= maxTrackedChunks) {
                return false;
            }

            state = new ChunkState(now);
            ChunkState existing = states.putIfAbsent(key, state);
            if (existing != null) {
                state = existing;
            }
        }

        state.rollover(windowMS, now);
        state.score += score;

        if (pressure && state.score >= scoreTrigger) {
            state.quarantinedUntil = Math.max(state.quarantinedUntil, now + quarantineMS);
            state.score = scoreTrigger * 0.5;
        }

        return now < state.quarantinedUntil;
    }

    private boolean shouldTrackSpawn(CreatureSpawnEvent.SpawnReason reason) {
        boolean natural = switch (reason) {
            case NATURAL, CHUNK_GEN, NETHER_PORTAL, REINFORCEMENTS, JOCKEY, PATROL, RAID -> true;
            default -> false;
        };

        boolean spawner = reason == CreatureSpawnEvent.SpawnReason.SPAWNER || "TRIAL_SPAWNER".equals(reason.name());
        return (trackNaturalSpawns && natural) || (trackSpawnerSpawns && spawner);
    }

    private boolean isPressure() {
        return sample(SamplerTickTime.ID) >= pressureTickMS
                || sample(SamplerIncidentScore.ID) >= pressureIncidentScore;
    }

    private double sample(String id) {
        var sampler = React.sampler(id);
        return sampler == null ? 0D : sampler.sample();
    }

    private Location resolveHopperLocation(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof Hopper source) {
            return source.getBlock().getLocation();
        }

        if (event.getDestination().getHolder() instanceof Hopper destination) {
            return destination.getBlock().getLocation();
        }

        return null;
    }

    private static final class ChunkState {
        private long start;
        private long lastHit;
        private long quarantinedUntil;
        private double score;

        private ChunkState(long now) {
            start = now;
            lastHit = now;
            quarantinedUntil = 0L;
            score = 0D;
        }

        private void rollover(int windowMS, long now) {
            if (now - start > windowMS) {
                start = now;
                score = 0D;
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
