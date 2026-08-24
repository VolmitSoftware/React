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
import art.arcane.react.util.common.scheduling.J;
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
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Farm Burst Smoother feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureFarmBurstSmoother extends ReactFeature implements Listener {
  public static final String ID = "farm-burst-smoother";
  private static final int SHUTDOWN_BATCH_SIZE = 256;
  private static final int SHUTDOWN_RETRY_PASSES = 2;
  private static final long RETRY_DELAY_MS = 200L;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for farm burst smoother in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 100;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Rolling window length for burst checks (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
  private int burstWindowMS = 1200;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for burst trigger count in farm burst smoother.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private int burstTriggerCount = 72;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum apply delay ticks required by farm burst smoother.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private int minApplyDelayTicks = 2;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum apply delay ticks allowed by farm burst smoother.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private int maxApplyDelayTicks = 16;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum applies allowed per cycle in farm burst smoother.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxAppliesPerCycle = 24;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum pending updates allowed by farm burst smoother.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private int maxPendingUpdates = 2500;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Age at which pending growth is force-applied instead of remaining delayed (milliseconds).", impact = "Higher values preserve smoothing delays longer; lower values force valid cancelled growth sooner.")
  private int stalePendingMS = 15000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum time spent waiting for owner-thread growth application during deactivation (milliseconds).", impact = "Higher values give busy regions longer to preserve cancelled growth; a nonempty remainder fails deactivation instead of being discarded.")
  private int shutdownDrainTimeoutMS = 2000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether farm burst smoother applies only during pressure.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean onlyDuringPressure = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for pressure incident score in farm burst smoother.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double pressureIncidentScore = 42;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold for pressure in farm burst smoother (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double pressureTickMS = 52;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Bypasses farm burst smoother handling for bypass near players.", impact = "Enable this to skip enforcement in matching situations; disable it for strict handling.")
  private boolean bypassNearPlayers = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Bypass player radius used by farm burst smoother (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double bypassPlayerRadius = 10;
  private transient final Object pendingLock = new Object();
  private transient final AtomicBoolean applyQueued = new AtomicBoolean(false);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final BurstState burstState = new BurstState();
  private transient final Map<BlockKey, PendingGrowth> pending = new ConcurrentHashMap<>();
  private transient volatile boolean accepting;

  public FeatureFarmBurstSmoother() {
    super(ID);
  }

  @Override
  public void onActivate() {
    accepting = false;
    long generation = lifecycleGeneration.incrementAndGet();
    for (PendingGrowth growth : pending.values()) {
      growth.retireClaimBefore(generation);
    }
    long now = System.currentTimeMillis();
    burstState.reset(now);
    applyQueued.set(false);
    accepting = true;
  }

  @Override
  public void onDeactivate() {
    accepting = false;
    long generation;
    synchronized (pendingLock) {
      generation = lifecycleGeneration.incrementAndGet();
    }
    applyQueued.set(false);
    for (PendingGrowth growth : pending.values()) {
      growth.retireClaimBefore(generation);
    }
    drainPendingGrowth(generation);
    if (!pending.isEmpty()) {
      throw new IllegalStateException("Farm burst smoother could not apply " + pending.size()
          + " cancelled growth changes before the shutdown deadline; pending changes were retained.");
    }
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    if (!accepting) {
      return;
    }
    long generation = lifecycleGeneration.get();
    if (J.isFoliaThreading()) {
      applyPendingGrowthFolia(generation);
      return;
    }

    if (!applyQueued.compareAndSet(false, true)) {
      return;
    }

    try {
      J.s(() -> {
        try {
          if (accepting && generation == lifecycleGeneration.get()) {
            applyPendingGrowth(generation);
          }
        } finally {
          if (generation == lifecycleGeneration.get()) {
            applyQueued.set(false);
          }
        }
      });
    } catch (Throwable throwable) {
      applyQueued.set(false);
      React.reportError(throwable);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(BlockGrowEvent event) {
    if (!accepting) {
      return;
    }
    Block block = event.getBlock();
    if (!isFarmGrowth(block.getType())) {
      return;
    }

    long now = System.currentTimeMillis();
    if (!burstState.record(now, burstWindowMS, burstTriggerCount)) {
      return;
    }

    if (!shouldSmooth(block.getLocation())) {
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

    if (queueGrowth(key, growth)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(WorldUnloadEvent event) {
    if (event.isCancelled()) {
      return;
    }

    UUID worldId = event.getWorld().getUID();
    synchronized (pendingLock) {
      for (Map.Entry<BlockKey, PendingGrowth> entry : pending.entrySet()) {
        BlockKey key = entry.getKey();
        PendingGrowth growth = entry.getValue();
        if (key.world.equals(worldId) && pending.remove(key, growth)) {
          growth.retireClaim();
        }
      }
    }
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

  private void drainPendingGrowth(long generation) {
    if (pending.isEmpty()) {
      return;
    }

    long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1, shutdownDrainTimeoutMS));
    long deadlineNanos = System.nanoTime() + timeoutNanos;
    if (J.isFoliaThreading()) {
      drainPendingGrowthFolia(generation, deadlineNanos);
      return;
    }
    if (J.isPrimaryThread()) {
      drainPendingGrowthPaper(generation, deadlineNanos);
      return;
    }

    CompletableFuture<Void> completion = new CompletableFuture<>();
    try {
      J.s(() -> {
        try {
          drainPendingGrowthPaper(generation, deadlineNanos);
        } finally {
          completion.complete(null);
        }
      });
    } catch (Throwable throwable) {
      completion.complete(null);
      React.reportError(throwable);
    }
    awaitCompletion(completion, deadlineNanos);
  }

  private void drainPendingGrowthPaper(long generation, long deadlineNanos) {
    for (int pass = 0; pass < SHUTDOWN_RETRY_PASSES && System.nanoTime() < deadlineNanos; pass++) {
      List<PendingRef> snapshot = snapshotPending();
      if (snapshot.isEmpty()) {
        return;
      }
      for (PendingRef ref : snapshot) {
        if (System.nanoTime() >= deadlineNanos || generation != lifecycleGeneration.get()) {
          return;
        }
        applyPendingGrowthAt(ref.key(), ref.growth(), generation, true);
      }
    }
  }

  private void drainPendingGrowthFolia(long generation, long deadlineNanos) {
    for (int pass = 0; pass < SHUTDOWN_RETRY_PASSES && System.nanoTime() < deadlineNanos; pass++) {
      List<PendingRef> snapshot = snapshotPending();
      if (snapshot.isEmpty()) {
        return;
      }

      List<CompletableFuture<Void>> completions = new ArrayList<>(SHUTDOWN_BATCH_SIZE);
      for (PendingRef ref : snapshot) {
        if (System.nanoTime() >= deadlineNanos || generation != lifecycleGeneration.get()) {
          return;
        }
        CompletableFuture<Void> completion = scheduleFoliaGrowth(
            ref.key(),
            ref.growth(),
            generation,
            true);
        if (completion != null) {
          completions.add(completion);
        }
        if (completions.size() >= SHUTDOWN_BATCH_SIZE) {
          if (!awaitCompletions(completions, deadlineNanos)) {
            return;
          }
          completions.clear();
        }
      }
      if (!awaitCompletions(completions, deadlineNanos)) {
        return;
      }
    }
  }

  private List<PendingRef> snapshotPending() {
    List<PendingRef> snapshot = new ArrayList<>(pending.size());
    for (Map.Entry<BlockKey, PendingGrowth> entry : pending.entrySet()) {
      snapshot.add(new PendingRef(entry.getKey(), entry.getValue()));
    }
    return snapshot;
  }

  private boolean awaitCompletions(List<CompletableFuture<Void>> completions, long deadlineNanos) {
    if (completions.isEmpty()) {
      return System.nanoTime() < deadlineNanos;
    }
    CompletableFuture<Void> completion = CompletableFuture.allOf(
        completions.toArray(new CompletableFuture[0]));
    return awaitCompletion(completion, deadlineNanos);
  }

  private boolean awaitCompletion(CompletableFuture<Void> completion, long deadlineNanos) {
    long remainingNanos = deadlineNanos - System.nanoTime();
    if (remainingNanos <= 0L) {
      return false;
    }
    try {
      completion.get(remainingNanos, TimeUnit.NANOSECONDS);
      return true;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    } catch (TimeoutException exception) {
      return false;
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      React.reportError(cause == null ? exception : cause);
      return true;
    }
  }

  private void applyPendingGrowth(long generation) {
    if (pending.isEmpty()) {
      return;
    }

    long now = System.currentTimeMillis();
    int applied = 0;
    int scanned = 0;
    int maxApplies = Math.max(1, maxAppliesPerCycle);
    int maxScan = Math.max(maxApplies * 8, 96);

    for (Map.Entry<BlockKey, PendingGrowth> entry : pending.entrySet()) {
      if (scanned++ >= maxScan || generation != lifecycleGeneration.get()) {
        break;
      }

      PendingGrowth growth = entry.getValue();
      boolean force = isStale(growth, now);
      if (!force && growth.applyAtMS > now) {
        continue;
      }

      GrowthResult result = applyPendingGrowthAt(entry.getKey(), growth, generation, force);
      if (result == GrowthResult.APPLIED && ++applied >= maxApplies) {
        break;
      }
    }
  }

  private void applyPendingGrowthFolia(long generation) {
    if (pending.isEmpty()) {
      return;
    }

    long now = System.currentTimeMillis();
    int scheduled = 0;
    int scanned = 0;
    int maxApplies = Math.max(1, maxAppliesPerCycle);
    int maxScan = Math.max(maxApplies * 8, 96);

    for (Map.Entry<BlockKey, PendingGrowth> entry : pending.entrySet()) {
      if (scanned++ >= maxScan || generation != lifecycleGeneration.get()) {
        break;
      }

      BlockKey key = entry.getKey();
      PendingGrowth growth = entry.getValue();
      boolean force = isStale(growth, now);
      if (!force && growth.applyAtMS > now) {
        continue;
      }

      if (scheduleFoliaGrowth(key, growth, generation, force) != null) {
        scheduled++;
      }
      if (scheduled >= maxApplies) {
        break;
      }
    }
  }

  private CompletableFuture<Void> scheduleFoliaGrowth(
      BlockKey key,
      PendingGrowth growth,
      long generation,
      boolean force) {
    if (generation != lifecycleGeneration.get()) {
      return null;
    }

    World world = Bukkit.getWorld(growth.world);
    if (world == null) {
      scheduleRetry(growth);
      return null;
    }

    GrowthClaim claim = growth.tryClaim(generation);
    if (claim == null) {
      return null;
    }

    Location location = new Location(world, growth.x, growth.y, growth.z);
    Runnable task = () -> {
      try {
        if (generation == lifecycleGeneration.get()) {
          applyPendingGrowthAt(key, growth, generation, force);
        }
      } finally {
        growth.releaseClaim(claim);
      }
    };

    try {
      if (J.isOwnedByCurrentRegion(location)) {
        task.run();
      } else {
        J.s(location, task, 0);
      }
    } catch (Throwable throwable) {
      growth.releaseClaim(claim);
      scheduleRetry(growth);
      React.reportError(throwable);
    }
    return claim.completion();
  }

  private GrowthResult applyPendingGrowthAt(
      BlockKey key,
      PendingGrowth growth,
      long generation,
      boolean force) {
    if (generation != lifecycleGeneration.get()) {
      return GrowthResult.SKIPPED;
    }
    PendingGrowth current = pending.get(key);
    if (current != growth) {
      return GrowthResult.SKIPPED;
    }

    World world = Bukkit.getWorld(growth.world);
    if (world == null) {
      scheduleRetry(growth);
      return GrowthResult.RETRY;
    }

    try {
      Block block = world.getBlockAt(growth.x, growth.y, growth.z);
      if (block.getType() != growth.expectedType) {
        pending.remove(key, growth);
        return GrowthResult.INVALID;
      }

      if (!force && bypassNearPlayers && React.hasNearbyPlayer(block.getLocation(), bypassPlayerRadius)) {
        scheduleRetry(growth);
        return GrowthResult.RETRY;
      }

      block.setBlockData(growth.targetData, false);
      pending.remove(key, growth);
      return GrowthResult.APPLIED;
    } catch (Throwable throwable) {
      scheduleRetry(growth);
      React.reportError(throwable);
      return GrowthResult.RETRY;
    }
  }

  private boolean isStale(PendingGrowth growth, long now) {
    return now - growth.createdAtMS > Math.max(0, stalePendingMS);
  }

  private void scheduleRetry(PendingGrowth growth) {
    growth.applyAtMS = System.currentTimeMillis() + RETRY_DELAY_MS;
  }

  private boolean queueGrowth(BlockKey key, PendingGrowth growth) {
    synchronized (pendingLock) {
      if (!accepting) {
        return false;
      }
      if (!pending.containsKey(key) && pending.size() >= Math.max(0, maxPendingUpdates)) {
        return false;
      }
      pending.put(key, growth);
      return true;
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
    private final AtomicReference<GrowthClaim> claim = new AtomicReference<>();
    @art.arcane.react.util.project.config.ConfigDoc(value = "World identifier used by farm burst smoother internal tracking.", impact = "This is runtime identity data and should normally be left to automatic updates.")
    private final UUID world;
    @art.arcane.react.util.project.config.ConfigDoc(value = "X-axis coordinate used by farm burst smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Y-axis coordinate used by farm burst smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int y;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Z-axis coordinate used by farm burst smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int z;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Runtime reference field for expected type used by farm burst smoother.", impact = "This value is typically populated from live game objects and not intended for manual editing.")
    private final Material expectedType;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Runtime reference field for target data used by farm burst smoother.", impact = "This value is typically populated from live game objects and not intended for manual editing.")
    private final BlockData targetData;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by farm burst smoother to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private final long createdAtMS;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by farm burst smoother to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
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

    private GrowthClaim tryClaim(long generation) {
      GrowthClaim offered = new GrowthClaim(generation, new CompletableFuture<>());
      return claim.compareAndSet(null, offered) ? offered : null;
    }

    private void releaseClaim(GrowthClaim expected) {
      claim.compareAndSet(expected, null);
      expected.completion().complete(null);
    }

    private void retireClaimBefore(long generation) {
      while (true) {
        GrowthClaim current = claim.get();
        if (current == null || current.generation() >= generation) {
          return;
        }
        if (claim.compareAndSet(current, null)) {
          current.completion().complete(null);
          return;
        }
      }
    }

    private void retireClaim() {
      GrowthClaim current = claim.getAndSet(null);
      if (current != null) {
        current.completion().complete(null);
      }
    }
  }

  private enum GrowthResult {
    APPLIED,
    INVALID,
    RETRY,
    SKIPPED
  }

  private record GrowthClaim(long generation, CompletableFuture<Void> completion) {
  }

  private record PendingRef(BlockKey key, PendingGrowth growth) {
  }

  static final class BurstState {
    private long windowStartMS;
    private int windowEvents;
    private long smoothUntilMS;

    synchronized boolean record(long now, int windowMS, int triggerCount) {
      int safeWindowMS = Math.max(0, windowMS);
      if (now - windowStartMS > safeWindowMS) {
        windowStartMS = now;
        windowEvents = 0;
      }

      windowEvents++;
      if (windowEvents >= Math.max(1, triggerCount)) {
        smoothUntilMS = Math.max(smoothUntilMS, now + safeWindowMS);
      }
      return now <= smoothUntilMS;
    }

    synchronized void reset(long now) {
      windowStartMS = now;
      windowEvents = 0;
      smoothUntilMS = 0L;
    }
  }

  private static final class BlockKey {
    @art.arcane.react.util.project.config.ConfigDoc(value = "World identifier used by farm burst smoother internal tracking.", impact = "This is runtime identity data and should normally be left to automatic updates.")
    private final UUID world;
    @art.arcane.react.util.project.config.ConfigDoc(value = "X-axis coordinate used by farm burst smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Y-axis coordinate used by farm burst smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int y;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Z-axis coordinate used by farm burst smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
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
