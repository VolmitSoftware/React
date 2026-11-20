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

import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.GrowthHost;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Leaf Decay feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureFastLeafDecay extends ReactFeature {
  public static final String ID = "fast-leaf-decay";
  private static final long FAILURE_LOG_INTERVAL_MS = 10_000L;
  private final transient Set<IBlock> search = ConcurrentHashMap.newKeySet();
  private final transient AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private final transient AtomicLong cooldownUntilMS = new AtomicLong(0L);
  private final transient AtomicLong lastFailureLogMS = new AtomicLong(0L);
  private transient Cache<IChunk, GrowthHost.ChunkBlocks> snapshot;
  private transient volatile boolean active = true;
  private transient volatile GrowthHost growth;
  private transient Subscription decays = Subscription.NONE;
  private transient Subscription breaks = Subscription.NONE;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Leaf decay radius used by fast leaf decay (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private int leafDecayDistance = 6;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Leaf decay radius used by fast leaf decay (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private int leafDecayRadius = 5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum async ms allowed by fast leaf decay.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxAsyncMS = 10;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum sync spike ms allowed by fast leaf decay.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxSyncSpikeMS = 10;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for fast leaf decay in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 250;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cooldown for decay trigger cooldown in fast leaf decay (milliseconds).", impact = "Higher values reduce repeat frequency; lower values allow reactions more often.")
  private int decayTriggerCooldownMS = 250;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum leaf-decay roots claimed per evaluation.", impact = "Higher values admit larger bursts; lower values bound each evaluation more tightly.")
  private int decayTickSpread = 20;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Probability setting for sound chance in fast leaf decay.", impact = "Higher values make this effect occur more often; lower values make it rarer.")
  private double soundChance = 0.25;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Audio tuning value for sound volume in fast leaf decay.", impact = "Adjust this to balance audibility and tone for player feedback events.")
  private double soundVolume = 0.26;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Audio tuning value for sound pitch in fast leaf decay.", impact = "Adjust this to balance audibility and tone for player feedback events.")
  private double soundPitch = 0.2;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast leaf decay applies force decay persistent.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean forceDecayPersistent = false;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast leaf decay applies play sounds.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean playSounds = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast leaf decay applies fast block changes.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean fastBlockChanges = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Text value for decay sound used by fast leaf decay.", impact = "Adjust this to match your naming, routing, or permission conventions.")
  private String decaySound = "minecraft:block.azalea_leaves.fall";

  public FeatureFastLeafDecay() {
    super(ID);
  }

  @Override
  public void onActivate() {
    active = false;
    lifecycleGeneration.incrementAndGet();
    search.clear();
    snapshot = Caffeine.newBuilder()
        .maximumSize(4096L)
        .expireAfterWrite(500L, TimeUnit.MILLISECONDS)
        .build();
    cooldownUntilMS.set(0L);
    ReactPlatform platform = ReactPlatforms.get();
    growth = platform.growthHost().orElse(null);
    decays = platform.growthHost()
        .map(host -> host.onLeavesDecay(ListenerPriority.MONITOR, true, this::onLeavesDecay))
        .orElse(Subscription.NONE);
    breaks = platform.blockHost()
        .map(host -> host.onBreak(ListenerPriority.MONITOR, true, this::onBlockBreak))
        .orElse(Subscription.NONE);
    active = true;
  }

  @Override
  public void onDeactivate() {
    active = false;
    decays.cancel();
    breaks.cancel();
    decays = Subscription.NONE;
    breaks = Subscription.NONE;
    lifecycleGeneration.incrementAndGet();
    search.clear();
    if (snapshot != null) {
      snapshot.invalidateAll();
    }
  }

  @Override
  public int getTickInterval() {
    return Math.max(1, tickIntervalMS);
  }

  public int getLeafDecayDistance() {
    return leafDecayDistance;
  }

  public int getLeafDecayRadius() {
    return leafDecayRadius;
  }

  public boolean isLeaf(int leaf) {
    return leaf != GrowthHost.NOT_LEAVES;
  }

  public boolean shouldDecay(int leaf) {
    return isLeaf(leaf)
        && (forceDecayPersistent || (leaf & GrowthHost.PERSISTENT_LEAVES) == 0)
        && (leaf & 0xFF) >= getLeafDecayDistance();
  }

  public void addBlockForDecay(IBlock block, int leaf) {
    GrowthHost host = growthHost();
    if (host == null) {
      return;
    }
    GameLocation location = block.location();

    if (shouldDecay(host.leaf(location))) {
      if (playSounds && ThreadLocalRandom.current().nextDouble() < soundChance) {
        host.playSound(location, decaySound, (float) soundVolume, (float) soundPitch);
      }

      host.breakNaturally(location, fastBlockChanges);
    }
  }

  public GrowthHost.ChunkBlocks snap(IChunk chunk) {
    GrowthHost host = growthHost();
    return host == null ? null : snapshot.get(chunk, (k) -> host.chunk(k.world, k.x, k.z));
  }

  public void decay(GameLocation block) {
    if (active) {
      search.add(new IBlock(block.worldId(), block.blockX(), block.blockY(), block.blockZ()));
    }
  }

  public void checkDecay(GameLocation block) {
    GrowthHost host = growthHost();
    if (host != null && isLeaf(host.leaf(block))) {
      decay(block);
    }
  }

  void onLeavesDecay(BlockHost.BlockEvent e) {
    if (claimDecayTrigger()) {
      checkDecay(e.location());
    }
  }

  void onBlockBreak(BlockHost.BlockEvent e) {
    GrowthHost host = growthHost();
    if (host == null) {
      return;
    }
    GameLocation block = e.location();
    int leaf = host.leaf(block);
    if (isLeaf(leaf) && (leaf & GrowthHost.PERSISTENT_LEAVES) != 0) {
      checkDecay(block);
    }
  }

  private static ReactWorld world(UUID worldId) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null || worldId == null
        ? null
        : platform.worldHost().flatMap(host -> host.world(worldId)).orElse(null);
  }

  private GrowthHost growthHost() {
    GrowthHost host = growth;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.growthHost().orElse(null);
      growth = host;
    }
    return host;
  }

  @Override
  public void onTick() {
    long generation = lifecycleGeneration.get();
    if (!isCurrent(generation) || search.isEmpty()) {
      return;
    }

    PrecisionStopwatch p = PrecisionStopwatch.start();
    List<IBlock> claimedRoots = new ArrayList<>();

    try {
      Map<IChunk, List<IBlock>> rootsByChunk = new HashMap<>();
      int rootBudget = Math.max(1, decayTickSpread);
      int roots = 0;
      for (IBlock root : search) {
        if (!isCurrent(generation)
            || roots >= rootBudget
            || (roots > 0 && p.getMilliseconds() > Math.max(0D, maxAsyncMS))) {
          break;
        }
        if (!search.remove(root)) {
          continue;
        }

        claimedRoots.add(root);
        indexRootChunks(rootsByChunk, root);
        roots++;
      }

      if (!isCurrent(generation)) {
        return;
      }
      SyncBudget budget = new SyncBudget(maxSyncSpikeMS);
      for (Map.Entry<IChunk, List<IBlock>> entry : rootsByChunk.entrySet()) {
        if (!isCurrent(generation)) {
          return;
        }
        IChunk chunk = entry.getKey();
        List<IBlock> chunkRoots = List.copyOf(entry.getValue());
        ReactWorld world = world(chunk.world);
        if (world == null) {
          continue;
        }
        if (!J.runChunk(world, chunk.x, chunk.z, () -> scanChunk(chunk, chunkRoots, generation, budget))) {
          search.addAll(chunkRoots);
        }
      }
    } catch (Throwable ex) {
      search.addAll(claimedRoots);
      reportScanFailure("Fast leaf decay scan failed", ex);
    }
  }

  void indexRootChunks(Map<IChunk, List<IBlock>> rootsByChunk, IBlock root) {
    int radius = Math.max(0, getLeafDecayRadius());
    int minChunkX = (root.x - radius) >> 4;
    int maxChunkX = (root.x + radius) >> 4;
    int minChunkZ = (root.z - radius) >> 4;
    int maxChunkZ = (root.z + radius) >> 4;
    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        IChunk chunk = new IChunk(root.world, chunkX, chunkZ);
        rootsByChunk.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(root);
      }
    }
  }

  private void scanChunk(IChunk chunk, List<IBlock> roots, long generation, SyncBudget budget) {
    if (!isCurrent(generation)) {
      return;
    }

    try {
      GrowthHost.ChunkBlocks chunkSnapshot = snap(chunk);
      if (chunkSnapshot == null) {
        return;
      }
      LongOpenHashSet visited = new LongOpenHashSet();
      for (int rootIndex = 0; rootIndex < roots.size(); rootIndex++) {
        IBlock root = roots.get(rootIndex);
        if (!scanRootInChunk(root, chunk, chunkSnapshot, visited, budget, generation)) {
          if (isCurrent(generation)) {
            for (int retryIndex = rootIndex; retryIndex < roots.size(); retryIndex++) {
              search.add(roots.get(retryIndex));
            }
          }
          return;
        }
      }
    } catch (Throwable ex) {
      if (isCurrent(generation)) {
        search.addAll(roots);
      }
      reportScanFailure("Fast leaf decay chunk scan failed", ex);
    }
  }

  private void reportScanFailure(String context, Throwable failure) {
    long now = System.currentTimeMillis();
    long last = lastFailureLogMS.get();
    if (now - last < FAILURE_LOG_INTERVAL_MS || !lastFailureLogMS.compareAndSet(last, now)) {
      return;
    }
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.warn(context, failure);
    }
  }

  boolean scanRootInChunk(
      IBlock root,
      IChunk chunk,
      GrowthHost.ChunkBlocks chunkSnapshot,
      LongOpenHashSet visited,
      SyncBudget budget,
      long generation
  ) {
    int radius = Math.max(0, getLeafDecayRadius());
    int chunkMinX = chunk.x << 4;
    int chunkMinZ = chunk.z << 4;
    int minX = Math.max(root.x - radius, chunkMinX);
    int maxX = Math.min(root.x + radius, chunkMinX + 15);
    int minY = Math.max(chunkSnapshot.minHeight(), root.y - radius);
    int maxY = Math.min(chunkSnapshot.maxHeight() - 1, root.y + radius);
    int minZ = Math.max(root.z - radius, chunkMinZ);
    int maxZ = Math.min(root.z + radius, chunkMinZ + 15);
    int checked = 0;
    long checkpointNanos = System.nanoTime();

    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          if ((checked++ & 63) == 0) {
            long nowNanos = System.nanoTime();
            if (!isCurrent(generation) || !budget.charge(nowNanos - checkpointNanos)) {
              return false;
            }
            checkpointNanos = nowNanos;
          }

          if (!visited.add(packBlock(x, y, z))) {
            continue;
          }

          int data = chunkSnapshot.leaf(x & 15, y, z & 15);
          if (shouldDecay(data) && isCurrent(generation)) {
            addBlockForDecay(new IBlock(root.world, x, y, z), data);
          }
        }
      }
    }

    budget.record(System.nanoTime() - checkpointNanos);
    return true;
  }

  private boolean isCurrent(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  private boolean claimDecayTrigger() {
    long cooldownMS = Math.max(0L, decayTriggerCooldownMS);
    long now = System.currentTimeMillis();
    long current = cooldownUntilMS.get();
    while (now >= current) {
      if (cooldownUntilMS.compareAndSet(current, now + cooldownMS)) {
        return true;
      }
      current = cooldownUntilMS.get();
    }
    return false;
  }

  private static long packBlock(int x, int y, int z) {
    return ((long) (x & 0x3FFFFFF) << 38)
        | ((long) (z & 0x3FFFFFF) << 12)
        | (y & 0xFFFL);
  }

  static final class SyncBudget {
    private final long maxNanos;
    private final AtomicLong consumedNanos = new AtomicLong(0L);

    SyncBudget(double maxMilliseconds) {
      maxNanos = Math.max(100_000L, (long) (Math.max(0D, maxMilliseconds) * 1_000_000D));
    }

    boolean charge(long elapsedNanos) {
      return consumedNanos.addAndGet(Math.max(0L, elapsedNanos)) <= maxNanos;
    }

    void record(long elapsedNanos) {
      consumedNanos.addAndGet(Math.max(0L, elapsedNanos));
    }
  }

  @EqualsAndHashCode
  @AllArgsConstructor
  @Data
  public static class IBlock {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Runtime reference field for world used by fast leaf decay.", impact = "This value is typically populated from live game objects and not intended for manual editing.")
    private final UUID world;
    @art.arcane.react.util.project.config.ConfigDoc(value = "X-axis coordinate used by fast leaf decay internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Y-axis coordinate used by fast leaf decay internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int y;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Z-axis coordinate used by fast leaf decay internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int z;

    public GameLocation location() {
      return new GameLocation(world, x, y, z);
    }

    public IChunk chunk() {
      return new IChunk(world, x >> 4, z >> 4);
    }
  }

  @EqualsAndHashCode
  @AllArgsConstructor
  @Data
  public static class IChunk {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Runtime reference field for world used by fast leaf decay.", impact = "This value is typically populated from live game objects and not intended for manual editing.")
    private final UUID world;
    @art.arcane.react.util.project.config.ConfigDoc(value = "X-axis coordinate used by fast leaf decay internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Z-axis coordinate used by fast leaf decay internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int z;
  }
}
