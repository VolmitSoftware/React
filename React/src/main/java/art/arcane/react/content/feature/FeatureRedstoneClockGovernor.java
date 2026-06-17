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
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ConfigDescription("Configuration for Redstone Clock Governor feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureRedstoneClockGovernor extends ReactFeature implements Listener {
  public static final String ID = "redstone-clock-governor";

  @ConfigDoc(value = "Main evaluation interval for redstone clock governor in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 2000;
  @ConfigDoc(value = "Rolling enforcement window length used by redstone clock governor in milliseconds.", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
  private int windowMS = 1000;
  @ConfigDoc(value = "Maximum transitions allowed per window in redstone clock governor.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxTransitionsPerWindow = 12;
  @ConfigDoc(value = "Cool-off period after throttling decisions in redstone clock governor in milliseconds.", impact = "Higher values keep throttles active longer; lower values let activity recover sooner.")
  private int cooloffMS = 6000;
  @ConfigDoc(value = "Bypass radius for redstone clock governor around players in blocks.", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double bypassWithinPlayerRadius = 16;
  @ConfigDoc(value = "When true, redstone clock governor only throttles clocks that have no nearby players.", impact = "Enable to protect player-near contraptions; disable to enforce strictly.")
  private boolean onlyThrottleWithoutNearbyPlayers = true;

  private transient Map<UUID, Long2ObjectOpenHashMap<ClockWindow>> windows;

  public FeatureRedstoneClockGovernor() {
    super(ID);
  }

  @Override
  public void onActivate() {
    windows = new ConcurrentHashMap<>();
  }

  @Override
  public void onDeactivate() {
    if (windows != null) {
      windows.clear();
      windows = null;
    }
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    if (windows == null) {
      return;
    }

    long now = System.currentTimeMillis();
    long expiry = Math.max(2000L, (long) cooloffMS * 2L);
    Iterator<Map.Entry<UUID, Long2ObjectOpenHashMap<ClockWindow>>> worldIterator = windows.entrySet().iterator();
    while (worldIterator.hasNext()) {
      Map.Entry<UUID, Long2ObjectOpenHashMap<ClockWindow>> worldEntry = worldIterator.next();
      Long2ObjectOpenHashMap<ClockWindow> blockMap = worldEntry.getValue();
      synchronized (blockMap) {
        ObjectIterator<Long2ObjectOpenHashMap.Entry<ClockWindow>> blockIterator = blockMap.long2ObjectEntrySet().fastIterator();
        while (blockIterator.hasNext()) {
          if (now - blockIterator.next().getValue().lastHit > expiry) {
            blockIterator.remove();
          }
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(BlockRedstoneEvent event) {
    if (windows == null || event.getOldCurrent() == event.getNewCurrent()) {
      return;
    }

    long now = System.currentTimeMillis();
    Block block = event.getBlock();
    UUID worldId = block.getWorld().getUID();
    long blockKey = packBlock(block.getX(), block.getY(), block.getZ());
    Long2ObjectOpenHashMap<ClockWindow> blockMap = windows.computeIfAbsent(worldId, ignored -> new Long2ObjectOpenHashMap<>());
    ClockWindow window;
    synchronized (blockMap) {
      window = blockMap.get(blockKey);
      if (window == null) {
        window = new ClockWindow(now);
        blockMap.put(blockKey, window);
      }
    }
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

  private static long packBlock(int x, int y, int z) {
    return ((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFF) << 26) | (z & 0x3FFFFFF);
  }
}
