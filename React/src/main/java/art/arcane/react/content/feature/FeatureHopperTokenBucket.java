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
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class FeatureHopperTokenBucket extends ReactFeature implements Listener {
    public static final String ID = "hopper-token-bucket";
    private int tickIntervalMS = 3000;
    private double bucketCapacity = 120;
    private double refillPerSecond = 55;
    private double costPerMove = 1;
    private boolean bypassWhenNearbyPlayers = true;
    private double bypassPlayerRadius = 16;
    private transient Map<ChunkKey, Bucket> buckets = new HashMap<>();

    public FeatureHopperTokenBucket() {
        super(ID);
    }

    @Override
    public void onActivate() {
        buckets = new HashMap<>();
    }

    @Override
    public void onDeactivate() {
        buckets.clear();
    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        long expiry = 60000L;
        Iterator<Map.Entry<ChunkKey, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().lastUse > expiry) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void on(InventoryMoveItemEvent event) {
        Location location = resolveHopperLocation(event);
        if (location == null) {
            return;
        }

        if (bypassWhenNearbyPlayers && React.hasNearbyPlayer(location, bypassPlayerRadius)) {
            return;
        }

        long now = System.currentTimeMillis();
        ChunkKey key = new ChunkKey(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(now, bucketCapacity));

        if (!bucket.consume(now, bucketCapacity, refillPerSecond, costPerMove)) {
            event.setCancelled(true);
        }
    }

    private Location resolveHopperLocation(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof Hopper source) {
            Block block = source.getBlock();
            return block == null ? null : block.getLocation();
        }

        if (event.getDestination().getHolder() instanceof Hopper destination) {
            Block block = destination.getBlock();
            return block == null ? null : block.getLocation();
        }

        return null;
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefill;
        private long lastUse;

        private Bucket(long now, double capacity) {
            tokens = Math.max(1D, capacity);
            lastRefill = now;
            lastUse = now;
        }

        private boolean consume(long now, double capacity, double refillPerSecond, double cost) {
            double elapsedSeconds = Math.max(0D, (now - lastRefill) / 1000D);
            if (elapsedSeconds > 0) {
                tokens = Math.min(capacity, tokens + (elapsedSeconds * refillPerSecond));
                lastRefill = now;
            }

            lastUse = now;
            if (tokens >= cost) {
                tokens -= cost;
                return true;
            }

            return false;
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
