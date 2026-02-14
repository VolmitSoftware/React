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

@art.arcane.react.util.config.ConfigDescription("Configuration for Hopper Token Bucket feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureHopperTokenBucket extends ReactFeature implements Listener {
    public static final String ID = "hopper-token-bucket";
    @art.arcane.react.util.config.ConfigDoc(value = "Main evaluation interval for hopper token bucket in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
    private int tickIntervalMS = 3000;
    @art.arcane.react.util.config.ConfigDoc(value = "Bucket capacity limit used by hopper token bucket.", impact = "Higher values increase buffered work or burst allowance; lower values tighten throttling.")
    private double bucketCapacity = 120;
    @art.arcane.react.util.config.ConfigDoc(value = "Token refill rate used by hopper token bucket.", impact = "Higher values replenish budget faster; lower values enforce stricter sustained throttling.")
    private double refillPerSecond = 55;
    @art.arcane.react.util.config.ConfigDoc(value = "Token cost charged per hopper move in hopper token bucket.", impact = "Higher values drain budget faster and throttle sooner; lower values allow more moves before throttling.")
    private double costPerMove = 1;
    @art.arcane.react.util.config.ConfigDoc(value = "Bypasses hopper token bucket handling for bypass when nearby players.", impact = "Enable this to skip enforcement in matching situations; disable it for strict handling.")
    private boolean bypassWhenNearbyPlayers = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Bypass player radius used by hopper token bucket (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
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
        @art.arcane.react.util.config.ConfigDoc(value = "Tokens limit used by hopper token bucket.", impact = "Higher values increase buffered work or burst allowance; lower values tighten throttling.")
        private double tokens;
        @art.arcane.react.util.config.ConfigDoc(value = "Internal timestamp used by hopper token bucket to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
        private long lastRefill;
        @art.arcane.react.util.config.ConfigDoc(value = "Internal timestamp used by hopper token bucket to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
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
        @art.arcane.react.util.config.ConfigDoc(value = "World identifier used by hopper token bucket internal tracking.", impact = "This is runtime identity data and should normally be left to automatic updates.")
        private final UUID world;
        @art.arcane.react.util.config.ConfigDoc(value = "X-axis coordinate used by hopper token bucket internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
        private final int x;
        @art.arcane.react.util.config.ConfigDoc(value = "Z-axis coordinate used by hopper token bucket internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
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
