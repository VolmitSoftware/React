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
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class FeatureRedstoneClockGovernor extends ReactFeature implements Listener {
    public static final String ID = "redstone-clock-governor";
    private int tickIntervalMS = 2000;
    private int windowMS = 1000;
    private int maxTransitionsPerWindow = 12;
    private int cooloffMS = 6000;
    private double bypassWithinPlayerRadius = 16;
    private boolean onlyThrottleWithoutNearbyPlayers = true;
    private transient Map<BlockKey, ClockWindow> windows = new HashMap<>();

    public FeatureRedstoneClockGovernor() {
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
        long expiry = Math.max(2000L, (long) cooloffMS * 2L);
        Iterator<Map.Entry<BlockKey, ClockWindow>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().lastHit > expiry) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(BlockRedstoneEvent event) {
        if (event.getOldCurrent() == event.getNewCurrent()) {
            return;
        }

        long now = System.currentTimeMillis();
        Block block = event.getBlock();
        BlockKey key = new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        ClockWindow window = windows.computeIfAbsent(key, k -> new ClockWindow(now));
        window.update(windowMS, now);
        window.transitions++;

        boolean inCooloff = now < window.throttledUntil;
        if (!inCooloff && window.transitions > maxTransitionsPerWindow) {
            window.throttledUntil = now + cooloffMS;
            inCooloff = true;
        }

        if (!inCooloff) {
            return;
        }

        if (onlyThrottleWithoutNearbyPlayers && React.hasNearbyPlayer(block.getLocation(), bypassWithinPlayerRadius)) {
            return;
        }

        event.setNewCurrent(event.getOldCurrent());
    }

    private static final class ClockWindow {
        private long start;
        private long lastHit;
        private long throttledUntil;
        private int transitions;

        private ClockWindow(long now) {
            start = now;
            lastHit = now;
            throttledUntil = 0;
            transitions = 0;
        }

        private void update(int windowMS, long now) {
            if (now - start > windowMS) {
                start = now;
                transitions = 0;
            }

            lastHit = now;
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
