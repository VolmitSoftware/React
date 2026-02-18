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

@art.arcane.react.util.config.ConfigDescription("Configuration for Farm Burst Smoother feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureFarmBurstSmoother extends ReactFeature implements Listener {
  public static final String ID = "farm-burst-smoother";
  @art.arcane.react.util.config.ConfigDoc(value = "Main evaluation interval for farm burst smoother in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 100;
  @art.arcane.react.util.config.ConfigDoc(value = "Rolling window length for burst checks (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
  private int burstWindowMS = 1200;
  @art.arcane.react.util.config.ConfigDoc(value = "Trigger threshold for burst trigger count in farm burst smoother.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private int burstTriggerCount = 72;
  @art.arcane.react.util.config.ConfigDoc(value = "Minimum apply delay ticks required by farm burst smoother.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private int minApplyDelayTicks = 2;
  @art.arcane.react.util.config.ConfigDoc(value = "Maximum apply delay ticks allowed by farm burst smoother.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private int maxApplyDelayTicks = 16;
  @art.arcane.react.util.config.ConfigDoc(value = "Maximum applies allowed per cycle in farm burst smoother.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxAppliesPerCycle = 24;
  @art.arcane.react.util.config.ConfigDoc(value = "Maximum pending updates allowed by farm burst smoother.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private int maxPendingUpdates = 2500;
  @art.arcane.react.util.config.ConfigDoc(value = "Stale pending duration used by farm burst smoother (milliseconds).", impact = "Higher values keep state active longer; lower values expire or apply changes sooner.")
  private int stalePendingMS = 15000;
  @art.arcane.react.util.config.ConfigDoc(value = "Controls whether farm burst smoother applies only during pressure.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean onlyDuringPressure = true;
  @art.arcane.react.util.config.ConfigDoc(value = "Trigger threshold for pressure incident score in farm burst smoother.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double pressureIncidentScore = 42;
  @art.arcane.react.util.config.ConfigDoc(value = "Tick-time threshold for pressure in farm burst smoother (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double pressureTickMS = 52;
  @art.arcane.react.util.config.ConfigDoc(value = "Bypasses farm burst smoother handling for bypass near players.", impact = "Enable this to skip enforcement in matching situations; disable it for strict handling.")
  private boolean bypassNearPlayers = true;
  @art.arcane.react.util.config.ConfigDoc(value = "Bypass player radius used by farm burst smoother (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
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
    if (J.isFoliaThreading()) {
      applyPendingGrowthFolia();
      return;
    }

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

  private void applyPendingGrowthFolia() {
    if (pending.isEmpty()) {
      return;
    }

    long now = System.currentTimeMillis();
    int scheduled = 0;
    int scanned = 0;
    int maxApplies = Math.max(1, maxAppliesPerCycle);
    int maxScan = Math.max(maxApplies * 8, 96);
    List<BlockKey> remove = new ArrayList<>();

    for (Map.Entry<BlockKey, PendingGrowth> entry : pending.entrySet()) {
      if (scanned++ >= maxScan) {
        break;
      }

      BlockKey key = entry.getKey();
      PendingGrowth growth = entry.getValue();
      if (now - growth.createdAtMS > stalePendingMS) {
        remove.add(key);
        continue;
      }

      if (growth.applyAtMS > now) {
        continue;
      }

      World world = Bukkit.getWorld(growth.world);
      if (world == null) {
        remove.add(key);
        continue;
      }

      Location location = new Location(world, growth.x, growth.y, growth.z);
      J.s(location, () -> applyPendingGrowthAt(key, growth), 0);
      scheduled++;
      if (scheduled >= maxApplies) {
        break;
      }
    }

    for (BlockKey key : remove) {
      pending.remove(key);
    }
  }

  private void applyPendingGrowthAt(BlockKey key, PendingGrowth growth) {
    PendingGrowth current = pending.get(key);
    if (current != growth) {
      return;
    }

    long now = System.currentTimeMillis();
    if (now - growth.createdAtMS > stalePendingMS) {
      pending.remove(key, growth);
      return;
    }

    World world = Bukkit.getWorld(growth.world);
    if (world == null) {
      pending.remove(key, growth);
      return;
    }

    Block block = world.getBlockAt(growth.x, growth.y, growth.z);
    if (block.getType() != growth.expectedType) {
      pending.remove(key, growth);
      return;
    }

    if (bypassNearPlayers && React.hasNearbyPlayer(block.getLocation(), bypassPlayerRadius)) {
      growth.applyAtMS = now + 200L;
      return;
    }

    block.setBlockData(growth.targetData, false);
    pending.remove(key, growth);
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
    @art.arcane.react.util.config.ConfigDoc(value = "World identifier used by farm burst smoother internal tracking.", impact = "This is runtime identity data and should normally be left to automatic updates.")
    private final UUID world;
    @art.arcane.react.util.config.ConfigDoc(value = "X-axis coordinate used by farm burst smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.config.ConfigDoc(value = "Y-axis coordinate used by farm burst smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int y;
    @art.arcane.react.util.config.ConfigDoc(value = "Z-axis coordinate used by farm burst smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int z;
    @art.arcane.react.util.config.ConfigDoc(value = "Runtime reference field for expected type used by farm burst smoother.", impact = "This value is typically populated from live game objects and not intended for manual editing.")
    private final Material expectedType;
    @art.arcane.react.util.config.ConfigDoc(value = "Runtime reference field for target data used by farm burst smoother.", impact = "This value is typically populated from live game objects and not intended for manual editing.")
    private final BlockData targetData;
    @art.arcane.react.util.config.ConfigDoc(value = "Internal timestamp used by farm burst smoother to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private final long createdAtMS;
    @art.arcane.react.util.config.ConfigDoc(value = "Internal timestamp used by farm burst smoother to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private volatile long applyAtMS;

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
    @art.arcane.react.util.config.ConfigDoc(value = "World identifier used by farm burst smoother internal tracking.", impact = "This is runtime identity data and should normally be left to automatic updates.")
    private final UUID world;
    @art.arcane.react.util.config.ConfigDoc(value = "X-axis coordinate used by farm burst smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.config.ConfigDoc(value = "Y-axis coordinate used by farm burst smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int y;
    @art.arcane.react.util.config.ConfigDoc(value = "Z-axis coordinate used by farm burst smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
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
