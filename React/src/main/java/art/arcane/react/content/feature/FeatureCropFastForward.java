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
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Configuration for Crop Fast Forward feature. When a chunk transitions from dormant (no nearby player for a long stretch) to active (player approached or chunk reloaded), this feature walks crop and sapling blocks once and advances their growth by an amount proportional to the dormant duration so players do not see frozen farms after returning. Stays inactive during high-load incidents.")
public class FeatureCropFastForward extends ReactFeature implements Listener {
  public static final String ID = "crop-fast-forward";
  private static final int MIN_SAMPLED_Y = -64;
  private static final int MAX_SAMPLED_Y = 320;

  @ConfigDoc(value = "Main evaluation interval for crop fast forward in milliseconds.", impact = "Lower values check dormant chunks more often and react quicker; higher values reduce overhead.")
  private int tickIntervalMS = 2500;
  @ConfigDoc(value = "Player proximity range (blocks) that classifies a chunk as active. Within this range, dormant tracking resets and any pending fast-forward fires.", impact = "Larger values fast-forward farther chunks earlier; smaller values defer until players walk closer.")
  private int activeRange = 64;
  @ConfigDoc(value = "Minimum dormant duration (ticks) before a wake triggers fast-forward. Below this, the chunk is treated as still warm.", impact = "Higher values skip cheap recent wakes; lower values fast-forward even brief dormancy stretches.")
  private int minElapsedTicks = 200;
  @ConfigDoc(value = "Maximum dormant ticks fed into the growth math. Caps runaway fast-forwards after extended absence.", impact = "Higher values allow more catch-up but compress more vanilla growth into one pass; lower values clamp the wake correction.")
  private int maxFastForwardTicks = 24000;
  @ConfigDoc(value = "Incident score above which fast-forward stops engaging. This feature stays quiet during high-load incidents (opposite polarity to most React features).", impact = "Higher values keep fast-forward running during light incidents; lower values silence it sooner.")
  private double engageOnIncident = 30;
  @ConfigDoc(value = "Tick-time above which fast-forward stops engaging (milliseconds). Stay quiet under load.", impact = "Higher values keep fast-forward running during heavier load; lower values silence it sooner.")
  private double engageOnTickMs = 50;
  @ConfigDoc(value = "Incident score below which fast-forward is allowed to resume after a previous silencing.", impact = "Higher values resume sooner after a quieting; lower values hold fast-forward off longer for stability.")
  private double releaseOnIncident = 22;
  @ConfigDoc(value = "Tick-time below which fast-forward is allowed to resume (milliseconds).", impact = "Higher values resume sooner after a quieting; lower values hold fast-forward off longer.")
  private double releaseOnTickMs = 42;
  @ConfigDoc(value = "Maximum chunks tracked across all worlds before the oldest tracked chunks are evicted.", impact = "Higher values cover more terrain with more memory; lower values trade coverage for memory.")
  private int maxTrackedChunks = 32768;
  @ConfigDoc(value = "Maximum block-state updates applied per fast-forward pass to bound the cost of a single wake.", impact = "Higher values fast-forward larger farms in one pass; lower values bound worst-case wake cost.")
  private int maxAdvancesPerPass = 1024;
  @ConfigDoc(value = "Probability per random tick that a sapling rolls to grow into a tree under vanilla rules; used for proportional roll math.", impact = "Higher values make saplings more likely to fast-grow after dormant stretches; lower values match stricter sapling growth chances.")
  private double saplingGrowthChance = 0.142D;

  private transient Map<UUID, Long2LongOpenHashMap> lastActiveByWorld;
  private transient final AtomicLong blocksAdvanced = new AtomicLong(0L);
  private transient final AtomicLong fastForwards = new AtomicLong(0L);
  private transient final AtomicLong simulatedTicks = new AtomicLong(0L);
  private transient volatile boolean silenced;

  public FeatureCropFastForward() {
    super(ID);
  }

  @Override
  public void onActivate() {
    lastActiveByWorld = new ConcurrentHashMap<>();
    blocksAdvanced.set(0L);
    fastForwards.set(0L);
    simulatedTicks.set(0L);
    silenced = false;
  }

  @Override
  public void onDeactivate() {
    if (lastActiveByWorld != null) {
      lastActiveByWorld.clear();
    }
  }

  @Override
  public int getTickInterval() {
    return Math.max(500, tickIntervalMS);
  }

  @Override
  public void onTick() {
    updateSilencing();
    if (!silenced && lastActiveByWorld != null) {
      J.s(this::scanForWakes);
    }
    enforceCapacity();
  }

  public long readAndResetBlocksAdvanced() {
    return blocksAdvanced.getAndSet(0L);
  }

  public long readAndResetFastForwards() {
    return fastForwards.getAndSet(0L);
  }

  public long readAndResetSimulatedTicks() {
    return simulatedTicks.getAndSet(0L);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ChunkLoadEvent event) {
    if (lastActiveByWorld == null) {
      return;
    }
    Chunk chunk = event.getChunk();
    if (chunk == null) {
      return;
    }
    long now = System.currentTimeMillis();
    Long2LongOpenHashMap map = lastActiveByWorld.computeIfAbsent(chunk.getWorld().getUID(), ignored -> new Long2LongOpenHashMap());
    long key = packChunk(chunk.getX(), chunk.getZ());
    synchronized (map) {
      map.put(key, now);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ChunkUnloadEvent event) {
    if (lastActiveByWorld == null) {
      return;
    }
    Chunk chunk = event.getChunk();
    if (chunk == null) {
      return;
    }
    Long2LongOpenHashMap map = lastActiveByWorld.get(chunk.getWorld().getUID());
    if (map == null) {
      return;
    }
    long key = packChunk(chunk.getX(), chunk.getZ());
    synchronized (map) {
      map.remove(key);
      if (map.isEmpty()) {
        lastActiveByWorld.remove(chunk.getWorld().getUID(), map);
      }
    }
  }

  private void updateSilencing() {
    double tickMs = sample(SamplerTickTime.ID);
    double incident = sample(SamplerIncidentScore.ID);
    if (!silenced) {
      if (tickMs >= engageOnTickMs || incident >= engageOnIncident) {
        silenced = true;
      }
      return;
    }
    if (tickMs <= releaseOnTickMs && incident < releaseOnIncident) {
      silenced = false;
    }
  }

  private void scanForWakes() {
    if (silenced || lastActiveByWorld == null) {
      return;
    }

    long now = System.currentTimeMillis();
    int range = Math.max(0, activeRange);
    for (World world : Bukkit.getWorlds()) {
      Long2LongOpenHashMap map = lastActiveByWorld.get(world.getUID());
      if (map == null || map.isEmpty()) {
        continue;
      }

      Chunk[] loaded;
      try {
        loaded = world.getLoadedChunks();
      } catch (Throwable ex) {
        React.reportError(ex);
        continue;
      }
      if (loaded == null || loaded.length == 0) {
        continue;
      }

      for (Chunk chunk : loaded) {
        if (chunk == null) {
          continue;
        }
        long key = packChunk(chunk.getX(), chunk.getZ());
        boolean active = hasNearbyPlayerInChunk(chunk, range);
        long previousActive;
        synchronized (map) {
          previousActive = map.get(key);
          if (active) {
            map.put(key, now);
          } else if (previousActive == 0L) {
            map.put(key, now);
          }
        }

        if (!active || previousActive == 0L) {
          continue;
        }

        long elapsedMs = now - previousActive;
        long elapsedTicks = Math.min(elapsedMs / 50L, (long) Math.max(0, maxFastForwardTicks));
        if (elapsedTicks < Math.max(1, minElapsedTicks)) {
          continue;
        }

        dispatchFastForward(chunk, elapsedTicks);
      }
    }
  }

  private boolean hasNearbyPlayerInChunk(Chunk chunk, int range) {
    if (range <= 0) {
      return false;
    }
    World world = chunk.getWorld();
    if (world == null) {
      return false;
    }
    int centerX = (chunk.getX() << 4) + 8;
    int centerZ = (chunk.getZ() << 4) + 8;
    int centerY = world.getMinHeight() + ((world.getMaxHeight() - world.getMinHeight()) >> 1);
    Location anchor = new Location(world, centerX, centerY, centerZ);
    return React.hasNearbyPlayer(anchor, range);
  }

  private void dispatchFastForward(Chunk chunk, long elapsedTicks) {
    World world = chunk.getWorld();
    int chunkX = chunk.getX();
    int chunkZ = chunk.getZ();
    if (J.isFoliaThreading()) {
      J.runChunk(world, chunkX, chunkZ, () -> applyFastForward(world, chunkX, chunkZ, elapsedTicks));
      return;
    }
    J.s(() -> applyFastForward(world, chunkX, chunkZ, elapsedTicks));
  }

  private void applyFastForward(World world, int chunkX, int chunkZ, long elapsedTicks) {
    if (world == null) {
      return;
    }
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return;
    }

    int randomTickSpeed = readRandomTickSpeed(world);
    if (randomTickSpeed <= 0) {
      return;
    }

    Chunk chunk;
    try {
      chunk = world.getChunkAt(chunkX, chunkZ);
    } catch (Throwable ex) {
      React.reportError(ex);
      return;
    }
    if (chunk == null) {
      return;
    }

    ChunkSnapshot snapshot;
    try {
      snapshot = chunk.getChunkSnapshot(true, false, false);
    } catch (Throwable ex) {
      React.reportError(ex);
      return;
    }
    if (snapshot == null) {
      return;
    }

    int minY = Math.max(world.getMinHeight(), MIN_SAMPLED_Y);
    int maxY = Math.min(world.getMaxHeight() - 1, MAX_SAMPLED_Y);
    int worldX0 = chunkX << 4;
    int worldZ0 = chunkZ << 4;
    int advancedThisPass = 0;
    int budget = Math.max(1, maxAdvancesPerPass);

    for (int x = 0; x < 16 && advancedThisPass < budget; x++) {
      for (int z = 0; z < 16 && advancedThisPass < budget; z++) {
        int columnTop = Math.min(maxY, snapshot.getHighestBlockYAt(x, z));
        if (columnTop < minY) {
          continue;
        }
        for (int y = minY; y <= columnTop && advancedThisPass < budget; y++) {
          Block block;
          try {
            block = world.getBlockAt(worldX0 + x, y, worldZ0 + z);
          } catch (Throwable ex) {
            continue;
          }
          if (block == null) {
            continue;
          }
          Material type = block.getType();
          if (!isGrowable(type)) {
            continue;
          }

          if (isSapling(type)) {
            if (rollSapling(elapsedTicks, randomTickSpeed)) {
              if (applySaplingGrowth(block)) {
                advancedThisPass++;
              }
            }
            continue;
          }

          if (advanceCrop(block, type, elapsedTicks, randomTickSpeed)) {
            advancedThisPass++;
          }
        }
      }
    }

    if (advancedThisPass > 0) {
      blocksAdvanced.addAndGet(advancedThisPass);
      fastForwards.incrementAndGet();
      simulatedTicks.addAndGet(elapsedTicks);
    }
  }

  private boolean advanceCrop(Block block, Material type, long elapsedTicks, int randomTickSpeed) {
    BlockData data = block.getBlockData();
    if (!(data instanceof Ageable ageable)) {
      return false;
    }
    int age = ageable.getAge();
    int maxAge = ageable.getMaximumAge();
    if (age >= maxAge) {
      return false;
    }

    double perRandomTickProb = cropGrowthProbability(type);
    double sectionShare = randomTickSpeed / 4096D;
    double expectedAdvances = elapsedTicks * sectionShare * perRandomTickProb;
    int advances = (int) Math.floor(expectedAdvances);
    double leftover = expectedAdvances - advances;
    if (leftover > 0D && ThreadLocalRandom.current().nextDouble() < leftover) {
      advances++;
    }
    if (advances <= 0) {
      return false;
    }

    int remainingAge = maxAge - age;
    advances = Math.min(advances, remainingAge);
    if (advances <= 0) {
      return false;
    }

    int targetAge = Math.min(age + advances, maxAge);
    if (targetAge == age) {
      return false;
    }

    ageable.setAge(targetAge);
    try {
      block.setBlockData(ageable, false);
    } catch (Throwable ex) {
      React.reportError(ex);
      return false;
    }
    return true;
  }

  private boolean rollSapling(long elapsedTicks, int randomTickSpeed) {
    if (saplingGrowthChance <= 0D) {
      return false;
    }
    double sectionShare = randomTickSpeed / 4096D;
    double prob = Math.min(1D, elapsedTicks * sectionShare * saplingGrowthChance);
    return ThreadLocalRandom.current().nextDouble() < prob;
  }

  private boolean applySaplingGrowth(Block block) {
    try {
      BlockState before = block.getState();
      boolean grew = block.applyBoneMeal(BlockFace.UP);
      if (!grew) {
        return false;
      }
      return block.getType() != before.getType();
    } catch (Throwable ex) {
      return false;
    }
  }

  private int readRandomTickSpeed(World world) {
    try {
      Integer value = world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
      return value == null ? 3 : Math.max(0, value);
    } catch (Throwable ex) {
      return 3;
    }
  }

  private double cropGrowthProbability(Material type) {
    return switch (type) {
      case WHEAT, CARROTS, POTATOES, BEETROOTS -> 1D / 14D;
      case NETHER_WART -> 1D / 12D;
      case COCOA -> 1D / 5D;
      case SWEET_BERRY_BUSH -> 1D / 20D;
      case SUGAR_CANE, KELP, BAMBOO -> 1D / 16D;
      case MELON_STEM, PUMPKIN_STEM -> 1D / 14D;
      default -> 1D / 16D;
    };
  }

  private boolean isGrowable(Material type) {
    return isCrop(type) || isSapling(type);
  }

  private boolean isCrop(Material type) {
    return switch (type) {
      case WHEAT,
           CARROTS,
           POTATOES,
           BEETROOTS,
           NETHER_WART,
           COCOA,
           SWEET_BERRY_BUSH,
           SUGAR_CANE,
           BAMBOO,
           KELP,
           MELON_STEM,
           PUMPKIN_STEM -> true;
      default -> false;
    };
  }

  private boolean isSapling(Material type) {
    return switch (type) {
      case OAK_SAPLING,
           SPRUCE_SAPLING,
           BIRCH_SAPLING,
           JUNGLE_SAPLING,
           ACACIA_SAPLING,
           DARK_OAK_SAPLING,
           MANGROVE_PROPAGULE,
           CHERRY_SAPLING,
           AZALEA,
           FLOWERING_AZALEA -> true;
      default -> false;
    };
  }

  private void enforceCapacity() {
    if (lastActiveByWorld == null) {
      return;
    }
    int total = 0;
    for (Long2LongOpenHashMap map : lastActiveByWorld.values()) {
      synchronized (map) {
        total += map.size();
      }
    }
    int cap = Math.max(1024, maxTrackedChunks);
    if (total <= cap) {
      return;
    }

    int overflow = total - cap;
    long[] timestamps = new long[total];
    int collected = 0;
    for (Long2LongOpenHashMap map : lastActiveByWorld.values()) {
      synchronized (map) {
        Iterator<Long2LongOpenHashMap.Entry> iterator = map.long2LongEntrySet().fastIterator();
        while (iterator.hasNext() && collected < timestamps.length) {
          timestamps[collected++] = iterator.next().getLongValue();
        }
      }
    }
    if (collected == 0) {
      return;
    }

    int evictions = Math.min(overflow, collected);
    long[] sorted = collected == timestamps.length ? timestamps : Arrays.copyOf(timestamps, collected);
    Arrays.sort(sorted);
    long cutoff = sorted[evictions - 1];
    int remaining = evictions;

    Iterator<Map.Entry<UUID, Long2LongOpenHashMap>> worldIterator = lastActiveByWorld.entrySet().iterator();
    while (worldIterator.hasNext() && remaining > 0) {
      Map.Entry<UUID, Long2LongOpenHashMap> entry = worldIterator.next();
      Long2LongOpenHashMap map = entry.getValue();
      synchronized (map) {
        Iterator<Long2LongOpenHashMap.Entry> iterator = map.long2LongEntrySet().fastIterator();
        while (iterator.hasNext() && remaining > 0) {
          Long2LongOpenHashMap.Entry mapEntry = iterator.next();
          if (mapEntry.getLongValue() <= cutoff) {
            iterator.remove();
            remaining--;
          }
        }
        if (map.isEmpty()) {
          worldIterator.remove();
        }
      }
    }
  }

  private static long packChunk(int cx, int cz) {
    return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
  }
}
