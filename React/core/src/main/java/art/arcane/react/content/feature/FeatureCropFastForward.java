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

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.GrowthHost;
import art.arcane.react.platform.PlayerPosition;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Configuration for Crop Fast Forward feature. The server grows crops only in chunks within a player's simulation distance, so this feature records how long each loaded chunk spends outside that distance. When a player comes back within the active range, it walks the chunk's crop and sapling blocks once and advances their growth by that time, so players do not see frozen farms after returning. Pending growth is discarded when a chunk unloads. Stays inactive during high-load incidents.")
public class FeatureCropFastForward extends ReactFeature {
  public static final String ID = "crop-fast-forward";
  private static final int MIN_SAMPLED_Y = -64;
  private static final int MAX_SAMPLED_Y = 320;
  private static final int MAX_CHUNKS_SCANNED_PER_PASS = 128;
  private static final int MAX_SIMULATION_RADIUS = 32;

  @ConfigDoc(value = "Main evaluation interval for crop fast forward in milliseconds.", impact = "Lower values check dormant chunks more often and react quicker; higher values reduce overhead.")
  private int tickIntervalMS = 2500;
  @ConfigDoc(value = "Player distance (blocks) from a chunk's center column at which its pending fast-forward is applied. Chunk activity itself follows the world's simulation distance.", impact = "Larger values apply pending growth to farther chunks sooner; smaller values wait until players walk closer.")
  private int activeRange = 64;
  @ConfigDoc(value = "Minimum time (ticks) a chunk must spend outside simulation distance before it fast-forwards. Shorter absences are ignored.", impact = "Higher values skip brief absences; lower values fast-forward even short stretches outside simulation distance.")
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

  private transient Map<UUID, CropActivityLedger> ledgers;
  private transient final AtomicLong blocksAdvanced = new AtomicLong(0L);
  private transient final AtomicLong fastForwards = new AtomicLong(0L);
  private transient final AtomicLong simulatedTicks = new AtomicLong(0L);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient volatile long lastStampPassMs;
  private transient volatile boolean silenced;
  private transient volatile boolean active;
  private transient volatile GrowthHost growth;
  private transient volatile BlockHost blocks;
  private transient Subscription chunkLoads = Subscription.NONE;
  private transient Subscription chunkUnloads = Subscription.NONE;

  public FeatureCropFastForward() {
    super(ID);
  }

  @Override
  public void onActivate() {
    active = false;
    lifecycleGeneration.incrementAndGet();
    ledgers = new ConcurrentHashMap<>();
    lastStampPassMs = 0L;
    blocksAdvanced.set(0L);
    fastForwards.set(0L);
    simulatedTicks.set(0L);
    silenced = false;
    ReactPlatform platform = ReactPlatforms.get();
    growth = platform.growthHost().orElse(null);
    blocks = platform.blockHost().orElse(null);
    chunkLoads = blocks == null ? Subscription.NONE : blocks.onChunkLoad(this::onChunkLoad);
    chunkUnloads = blocks == null ? Subscription.NONE : blocks.onChunkUnload(this::onChunkUnload);
    active = true;
  }

  @Override
  public void onDeactivate() {
    active = false;
    chunkLoads.cancel();
    chunkUnloads.cancel();
    chunkLoads = Subscription.NONE;
    chunkUnloads = Subscription.NONE;
    lifecycleGeneration.incrementAndGet();
    Map<UUID, CropActivityLedger> tracked = ledgers;
    ledgers = null;
    if (tracked != null) {
      tracked.clear();
    }
  }

  @Override
  public int getTickInterval() {
    return Math.max(500, tickIntervalMS);
  }

  @Override
  public void onTick() {
    updateSilencing();
    Map<UUID, CropActivityLedger> tracked = ledgers;
    if (!active || tracked == null) {
      return;
    }

    long generation = lifecycleGeneration.get();
    long now = System.currentTimeMillis();
    seedLoadedChunks(tracked, now);
    stampSimulatedChunks(tracked, now);
    if (!silenced) {
      dispatchWakes(tracked, generation);
    }
    enforceCapacity(tracked);
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

  void onChunkLoad(ChunkRef chunk) {
    Map<UUID, CropActivityLedger> tracked = ledgers;
    if (!active || tracked == null || chunk == null) {
      return;
    }
    ledger(tracked, chunk.worldId())
        .loaded(CropActivityLedger.pack(chunk.chunkX(), chunk.chunkZ()), System.currentTimeMillis());
  }

  void onChunkUnload(ChunkRef chunk) {
    Map<UUID, CropActivityLedger> tracked = ledgers;
    if (!active || tracked == null || chunk == null) {
      return;
    }
    CropActivityLedger ledger = tracked.get(chunk.worldId());
    if (ledger != null) {
      ledger.unloaded(CropActivityLedger.pack(chunk.chunkX(), chunk.chunkZ()));
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

  private void seedLoadedChunks(Map<UUID, CropActivityLedger> tracked, long now) {
    BlockHost host = blocks;
    if (host == null) {
      return;
    }
    List<ChunkRef> targets = host.nextLoadedChunks(MAX_CHUNKS_SCANNED_PER_PASS);
    for (ChunkRef target : targets) {
      ledger(tracked, target.worldId()).seed(CropActivityLedger.pack(target.chunkX(), target.chunkZ()), now);
    }
  }

  private void stampSimulatedChunks(Map<UUID, CropActivityLedger> tracked, long now) {
    GrowthHost host = growth;
    Optional<List<PlayerPosition>> players = host == null ? Optional.empty() : host.simulatingPlayers();
    if (players.isEmpty()) {
      return;
    }

    Map<UUID, LongOpenHashSet> centersByWorld = new HashMap<>();
    for (PlayerPosition player : players.get()) {
      if (player.worldId() == null) {
        continue;
      }
      long center = CropActivityLedger.pack((int) Math.floor(player.x()) >> 4, (int) Math.floor(player.z()) >> 4);
      centersByWorld.computeIfAbsent(player.worldId(), ignored -> new LongOpenHashSet()).add(center);
    }

    long previousPass = lastStampPassMs;
    long warmTicks = Math.max(1, minElapsedTicks);
    long maxTicks = Math.max(0, maxFastForwardTicks);
    for (Map.Entry<UUID, LongOpenHashSet> entry : centersByWorld.entrySet()) {
      if (world(entry.getKey()) == null) {
        continue;
      }
      int radius = Math.max(0, Math.min(MAX_SIMULATION_RADIUS, host.simulationDistance(entry.getKey())));
      CropActivityLedger ledger = ledger(tracked, entry.getKey());
      LongIterator centers = entry.getValue().iterator();
      while (centers.hasNext()) {
        long center = centers.nextLong();
        ledger.stampInside(
            CropActivityLedger.chunkX(center),
            CropActivityLedger.chunkZ(center),
            radius,
            now,
            previousPass,
            warmTicks,
            maxTicks
        );
      }
    }
    lastStampPassMs = now;
  }

  private void dispatchWakes(Map<UUID, CropActivityLedger> tracked, long generation) {
    GrowthHost host = growth;
    int range = Math.max(0, activeRange);
    if (host == null || range <= 0) {
      return;
    }

    boolean folia = J.isFoliaThreading();
    long maxTicks = Math.max(0, maxFastForwardTicks);
    int remaining = MAX_CHUNKS_SCANNED_PER_PASS;
    Iterator<Map.Entry<UUID, CropActivityLedger>> worlds = tracked.entrySet().iterator();
    while (worlds.hasNext() && remaining > 0 && isCurrent(generation)) {
      Map.Entry<UUID, CropActivityLedger> entry = worlds.next();
      UUID worldId = entry.getKey();
      ReactWorld world = world(worldId);
      if (world == null) {
        worlds.remove();
        continue;
      }

      CropActivityLedger ledger = entry.getValue();
      for (long chunk : ledger.pendingChunks(Integer.MAX_VALUE)) {
        if (remaining <= 0) {
          return;
        }
        int chunkX = CropActivityLedger.chunkX(chunk);
        int chunkZ = CropActivityLedger.chunkZ(chunk);
        if (!host.anyPlayerInColumn(worldId, (chunkX << 4) + 8D, (chunkZ << 4) + 8D, range)) {
          continue;
        }
        long elapsedTicks = Math.min(ledger.takePending(chunk), maxTicks);
        if (elapsedTicks <= 0L) {
          continue;
        }
        Runnable fastForward = () -> applyFastForward(worldId, chunkX, chunkZ, elapsedTicks, generation);
        if (folia) {
          J.runChunk(world, chunkX, chunkZ, fastForward);
        } else {
          J.s(fastForward);
        }
        remaining--;
      }
    }
  }

  private void applyFastForward(UUID worldId, int chunkX, int chunkZ, long elapsedTicks, long generation) {
    GrowthHost host = growth;
    BlockHost chunks = blocks;
    if (!isCurrent(generation) || worldId == null || host == null || chunks == null) {
      return;
    }
    if (!chunks.isChunkLoaded(worldId, chunkX, chunkZ)) {
      return;
    }

    int randomTickSpeed = host.randomTickSpeed(worldId);
    if (randomTickSpeed <= 0) {
      return;
    }

    GrowthHost.ChunkBlocks snapshot;
    try {
      snapshot = host.chunk(worldId, chunkX, chunkZ);
    } catch (Throwable ex) {
      reportError(ex);
      return;
    }
    if (snapshot == null) {
      return;
    }

    int minY = Math.max(snapshot.minHeight(), MIN_SAMPLED_Y);
    int maxY = Math.min(snapshot.maxHeight() - 1, MAX_SAMPLED_Y);
    int worldX0 = chunkX << 4;
    int worldZ0 = chunkZ << 4;
    int advancedThisPass = 0;
    int budget = Math.max(1, maxAdvancesPerPass);

    for (int x = 0; x < 16 && advancedThisPass < budget && isCurrent(generation); x++) {
      for (int z = 0; z < 16 && advancedThisPass < budget; z++) {
        int columnTop = Math.min(maxY, snapshot.highestY(x, z));
        for (int y = minY; y <= columnTop && advancedThisPass < budget; y++) {
          String type = snapshot.type(x, y, z);
          if (!isGrowable(type)) {
            continue;
          }

          GameLocation block = new GameLocation(worldId, worldX0 + x, y, worldZ0 + z);
          if (isSapling(type)) {
            if (rollSapling(elapsedTicks, randomTickSpeed) && host.boneMeal(block)) {
              advancedThisPass++;
            }
            continue;
          }

          if (advanceCrop(host, block, type, elapsedTicks, randomTickSpeed)) {
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

  private boolean advanceCrop(GrowthHost host, GameLocation block, String type, long elapsedTicks, int randomTickSpeed) {
    return host.advanceAge(block, (age, maxAge) -> targetAge(age, maxAge, type, elapsedTicks, randomTickSpeed));
  }

  private int targetAge(int age, int maxAge, String type, long elapsedTicks, int randomTickSpeed) {
    if (age >= maxAge) {
      return age;
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
      return age;
    }

    int remainingAge = maxAge - age;
    advances = Math.min(advances, remainingAge);
    if (advances <= 0) {
      return age;
    }

    return Math.min(age + advances, maxAge);
  }

  private boolean rollSapling(long elapsedTicks, int randomTickSpeed) {
    if (saplingGrowthChance <= 0D) {
      return false;
    }
    double sectionShare = randomTickSpeed / 4096D;
    double prob = Math.min(1D, elapsedTicks * sectionShare * saplingGrowthChance);
    return ThreadLocalRandom.current().nextDouble() < prob;
  }

  private double cropGrowthProbability(String type) {
    return switch (type) {
      case "WHEAT", "CARROTS", "POTATOES", "BEETROOTS" -> 1D / 14D;
      case "NETHER_WART" -> 1D / 12D;
      case "COCOA" -> 1D / 5D;
      case "SWEET_BERRY_BUSH" -> 1D / 20D;
      case "SUGAR_CANE", "KELP", "BAMBOO" -> 1D / 16D;
      case "MELON_STEM", "PUMPKIN_STEM" -> 1D / 14D;
      default -> 1D / 16D;
    };
  }

  private boolean isGrowable(String type) {
    return type != null && (isCrop(type) || isSapling(type));
  }

  private boolean isCrop(String type) {
    return switch (type) {
      case "WHEAT",
           "CARROTS",
           "POTATOES",
           "BEETROOTS",
           "NETHER_WART",
           "COCOA",
           "SWEET_BERRY_BUSH",
           "SUGAR_CANE",
           "BAMBOO",
           "KELP",
           "MELON_STEM",
           "PUMPKIN_STEM" -> true;
      default -> false;
    };
  }

  private boolean isSapling(String type) {
    return switch (type) {
      case "OAK_SAPLING",
           "SPRUCE_SAPLING",
           "BIRCH_SAPLING",
           "JUNGLE_SAPLING",
           "ACACIA_SAPLING",
           "DARK_OAK_SAPLING",
           "MANGROVE_PROPAGULE",
           "CHERRY_SAPLING",
           "AZALEA",
           "FLOWERING_AZALEA" -> true;
      default -> false;
    };
  }

  private static ReactWorld world(UUID worldId) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null || worldId == null
        ? null
        : platform.worldHost().flatMap(host -> host.world(worldId)).orElse(null);
  }

  private static void reportError(Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(failure);
    }
  }

  private void enforceCapacity(Map<UUID, CropActivityLedger> tracked) {
    int total = 0;
    for (CropActivityLedger ledger : tracked.values()) {
      total += ledger.size();
    }
    int cap = Math.max(1024, maxTrackedChunks);
    if (total <= cap) {
      return;
    }

    long[] stamps = new long[total];
    int collected = 0;
    for (CropActivityLedger ledger : tracked.values()) {
      collected = ledger.collectStamps(stamps, collected);
    }
    if (collected == 0) {
      return;
    }

    int evictions = Math.min(total - cap, collected);
    long[] sorted = collected == stamps.length ? stamps : Arrays.copyOf(stamps, collected);
    Arrays.sort(sorted);
    long cutoff = sorted[evictions - 1];
    int remaining = evictions;
    for (CropActivityLedger ledger : tracked.values()) {
      if (remaining <= 0) {
        return;
      }
      remaining -= ledger.evictAtOrBefore(cutoff, remaining);
    }
  }

  private static CropActivityLedger ledger(Map<UUID, CropActivityLedger> tracked, UUID worldId) {
    return tracked.computeIfAbsent(worldId, ignored -> new CropActivityLedger());
  }

  private boolean isCurrent(long generation) {
    return active && generation == lifecycleGeneration.get();
  }
}
