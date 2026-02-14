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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class FeatureFarmBurstSmoother extends ReactFeature implements Listener {
    public static final String ID = "farm-burst-smoother";
    private int tickIntervalMS = 100;
    private int burstWindowMS = 1200;
    private int burstTriggerCount = 72;
    private int minApplyDelayTicks = 2;
    private int maxApplyDelayTicks = 16;
    private int maxAppliesPerCycle = 24;
    private int maxPendingUpdates = 2500;
    private int stalePendingMS = 15000;
    private boolean onlyDuringPressure = true;
    private double pressureIncidentScore = 42;
    private double pressureTickMS = 52;
    private boolean bypassNearPlayers = true;
    private double bypassPlayerRadius = 10;
    private transient Map<BlockKey, PendingGrowth> pending = new ConcurrentHashMap<>();
    private transient volatile long windowStartMS;
    private transient volatile int windowEvents;
    private transient volatile long smoothUntilMS;

    public FeatureFarmBurstSmoother() {
        super(ID);
    }

    @Override
    public void onActivate() {
        pending = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        windowStartMS = now;
        smoothUntilMS = now;
        windowEvents = 0;
    }

    @Override
    public void onDeactivate() {
        pending.clear();
    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    @Override
    public void onTick() {
        pruneOverflow();
        J.s(this::applyPendingGrowth);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(BlockGrowEvent event) {
        Block block = event.getBlock();
        if (!isFarmGrowth(block.getType())) {
            return;
        }

        long now = System.currentTimeMillis();
        rollWindow(now);
        windowEvents++;

        if (windowEvents >= burstTriggerCount) {
            smoothUntilMS = now + burstWindowMS;
        }

        if (now > smoothUntilMS) {
            return;
        }

        if (!shouldSmooth(block.getLocation())) {
            return;
        }

        if (pending.size() >= maxPendingUpdates) {
            return;
        }

        int minDelay = Math.max(1, minApplyDelayTicks);
        int maxDelay = Math.max(minDelay, maxApplyDelayTicks);
        int delayTicks = ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1);
        BlockData data = event.getNewState().getBlockData().clone();
        BlockKey key = new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        PendingGrowth growth = new PendingGrowth(
                key.world,
                key.x,
                key.y,
                key.z,
                block.getType(),
                data,
                now + (delayTicks * 50L),
                now
        );

        pending.put(key, growth);
        event.setCancelled(true);
    }

    private boolean shouldSmooth(Location location) {
        if (bypassNearPlayers && React.hasNearbyPlayer(location, bypassPlayerRadius)) {
            return false;
        }

        if (!onlyDuringPressure) {
            return true;
        }

        return sample(SamplerTickTime.ID) >= pressureTickMS
                || sample(SamplerIncidentScore.ID) >= pressureIncidentScore;
    }

    private double sample(String id) {
        var sampler = React.sampler(id);
        return sampler == null ? 0D : sampler.sample();
    }

    private void rollWindow(long now) {
        if (now - windowStartMS > burstWindowMS) {
            windowStartMS = now;
            windowEvents = 0;
        }
    }

    private void applyPendingGrowth() {
        if (pending.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        int applied = 0;
        int scanned = 0;
        int maxApplies = Math.max(1, maxAppliesPerCycle);
        int maxScan = Math.max(maxApplies * 8, 96);
        List<BlockKey> remove = new ArrayList<>();

        for (Map.Entry<BlockKey, PendingGrowth> entry : pending.entrySet()) {
            if (scanned++ >= maxScan) {
                break;
            }

            PendingGrowth growth = entry.getValue();
            if (now - growth.createdAtMS > stalePendingMS) {
                remove.add(entry.getKey());
                continue;
            }

            if (growth.applyAtMS > now) {
                continue;
            }

            World world = Bukkit.getWorld(growth.world);
            if (world == null) {
                remove.add(entry.getKey());
                continue;
            }

            Block block = world.getBlockAt(growth.x, growth.y, growth.z);
            if (block.getType() != growth.expectedType) {
                remove.add(entry.getKey());
                continue;
            }

            if (bypassNearPlayers && React.hasNearbyPlayer(block.getLocation(), bypassPlayerRadius)) {
                growth.applyAtMS = now + 200L;
                continue;
            }

            block.setBlockData(growth.targetData, false);
            remove.add(entry.getKey());
            applied++;

            if (applied >= maxApplies) {
                break;
            }
        }

        for (BlockKey key : remove) {
            pending.remove(key);
        }
    }

    private void pruneOverflow() {
        int overflow = pending.size() - maxPendingUpdates;
        if (overflow <= 0) {
            return;
        }

        for (Map.Entry<BlockKey, PendingGrowth> entry : pending.entrySet()) {
            pending.remove(entry.getKey(), entry.getValue());
            overflow--;
            if (overflow <= 0) {
                return;
            }
        }
    }

    private boolean isFarmGrowth(Material material) {
        return switch (material) {
            case WHEAT,
                 CARROTS,
                 POTATOES,
                 BEETROOTS,
                 NETHER_WART,
                 COCOA,
                 SWEET_BERRY_BUSH,
                 CACTUS,
                 SUGAR_CANE,
                 BAMBOO,
                 KELP,
                 KELP_PLANT,
                 MELON_STEM,
                 PUMPKIN_STEM -> true;
            default -> false;
        };
    }

    private static final class PendingGrowth {
        private final UUID world;
        private final int x;
        private final int y;
        private final int z;
        private final Material expectedType;
        private final BlockData targetData;
        private volatile long applyAtMS;
        private final long createdAtMS;

        private PendingGrowth(UUID world, int x, int y, int z, Material expectedType, BlockData targetData, long applyAtMS, long createdAtMS) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.expectedType = expectedType;
            this.targetData = targetData;
            this.applyAtMS = applyAtMS;
            this.createdAtMS = createdAtMS;
        }
    }

    private static final class BlockKey {
        private final UUID world;
        private final int x;
        private final int y;
        private final int z;

        private BlockKey(UUID world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof BlockKey key)) {
                return false;
            }

            return x == key.x && y == key.y && z == key.z && world.equals(key.world);
        }

        @Override
        public int hashCode() {
            int result = world.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}
