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

@art.arcane.react.util.config.ConfigDescription("Configuration for Redstone Clock Governor feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureRedstoneClockGovernor extends ReactFeature implements Listener {
  public static final String ID = "redstone-clock-governor";
  @art.arcane.react.util.config.ConfigDoc(value = "Main evaluation interval for redstone clock governor in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 2000;
  @art.arcane.react.util.config.ConfigDoc(value = "Rolling enforcement window length used by redstone clock governor (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
  private int windowMS = 1000;
  @art.arcane.react.util.config.ConfigDoc(value = "Maximum transitions allowed per window in redstone clock governor.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxTransitionsPerWindow = 12;
  @art.arcane.react.util.config.ConfigDoc(value = "Cool-off period after throttling decisions in redstone clock governor (milliseconds).", impact = "Higher values keep throttles active longer; lower values let activity recover sooner.")
  private int cooloffMS = 6000;
  @art.arcane.react.util.config.ConfigDoc(value = "Bypass within player radius used by redstone clock governor (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double bypassWithinPlayerRadius = 16;
  @art.arcane.react.util.config.ConfigDoc(value = "Controls whether redstone clock governor applies only throttle without nearby players.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
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
    @art.arcane.react.util.config.ConfigDoc(value = "Internal timestamp used by redstone clock governor to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long start;
    @art.arcane.react.util.config.ConfigDoc(value = "Internal timestamp used by redstone clock governor to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long lastHit;
    @art.arcane.react.util.config.ConfigDoc(value = "Internal timestamp used by redstone clock governor to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long throttledUntil;
    @art.arcane.react.util.config.ConfigDoc(value = "Internal counter used by redstone clock governor while tracking burst activity.", impact = "Primarily runtime state; React updates this automatically during live evaluation.")
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
    @art.arcane.react.util.config.ConfigDoc(value = "World identifier used by redstone clock governor internal tracking.", impact = "This is runtime identity data and should normally be left to automatic updates.")
    private final UUID world;
    @art.arcane.react.util.config.ConfigDoc(value = "X-axis coordinate used by redstone clock governor internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.config.ConfigDoc(value = "Y-axis coordinate used by redstone clock governor internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int y;
    @art.arcane.react.util.config.ConfigDoc(value = "Z-axis coordinate used by redstone clock governor internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
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
