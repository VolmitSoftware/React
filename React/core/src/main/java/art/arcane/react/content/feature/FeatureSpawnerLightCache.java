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
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.GrowthHost;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Configuration for Spawner Light Cache feature. Caches the most recent sky/block-light snapshot around dark-spawner candidate cells per chunk so spawn-attempt rolls can short-circuit when nothing meets the dark threshold. Operates in measurement-only mode by default and only escalates to cancellation under sustained pressure.")
public class FeatureSpawnerLightCache extends ReactFeature {
  public static final String ID = "spawner-light-cache";
  private static final int CACHE_HORIZONTAL_RADIUS = 4;
  private static final int CACHE_VERTICAL_RADIUS = 1;

  @ConfigDoc(value ="Main evaluation interval for spawner light cache in milliseconds.", impact = "Lower values prune stale chunk snapshots faster but consume more CPU; higher values reduce overhead.")
  private int tickIntervalMS = 2000;
  @ConfigDoc(value ="Bypass radius around players in blocks; spawners inside skip the cache entirely.", impact = "Higher values protect a wider zone around players from any throttling; lower values allow shedding closer to players.")
  private int bypassRadius = 8;
  @ConfigDoc(value ="Maximum block-level light at which a candidate cell counts as a dark-spawner candidate.", impact = "Higher values widen the dark-band considered; lower values match strict vanilla hostile thresholds (<= 7).")
  private int darkLightMax = 7;
  @ConfigDoc(value ="How long a cached snapshot stays valid before being recomputed (ticks).", impact = "Higher values reuse snapshots longer but react to lighting changes slower; lower values rebuild more often.")
  private int invalidationIntervalTicks = 200;
  @ConfigDoc(value ="Maximum cached chunk snapshots before the oldest are evicted.", impact = "Higher values cover more spawner-heavy chunks at higher memory cost; lower values shrink memory.")
  private int maxTrackedChunks = 4096;
  @ConfigDoc(value ="Trigger threshold for engaging cancellation rather than measurement only (incident score).", impact = "Higher values reserve cancellation for severe incidents; lower values engage earlier.")
  private double engageIncidentScore = 60;
  @ConfigDoc(value ="Tick-time threshold for engaging cancellation (milliseconds).", impact = "Higher values delay activation; lower values make this threshold easier to cross.")
  private double engageTickTimeMs = 58;
  @ConfigDoc(value ="Tick-time threshold for releasing back to measurement-only mode (milliseconds).", impact = "Lower values hold cancellation longer for stability; higher values restore vanilla spawn-rolls sooner.")
  private double releaseTickTimeMs = 45;

  private transient Map<UUID, Long2ObjectOpenHashMap<SpawnerLightSnapshot>> snapshotsByWorldChunk;
  private transient final AtomicLong skippedAttempts = new AtomicLong(0L);
  private transient final AtomicLong evaluatedAttempts = new AtomicLong(0L);
  private transient final AtomicLong invalidatedChunks = new AtomicLong(0L);
  private transient volatile boolean engaged;
  private transient volatile long invalidationCounter;
  private transient volatile SpawnHost spawns;
  private transient volatile WorldHost worlds;
  private transient List<Subscription> subscriptions = List.of();

  public FeatureSpawnerLightCache() {
    super(ID);
  }

  @Override
  public void onActivate() {
    snapshotsByWorldChunk = new ConcurrentHashMap<>();
    skippedAttempts.set(0L);
    evaluatedAttempts.set(0L);
    invalidatedChunks.set(0L);
    engaged = false;
    invalidationCounter = 0L;
    ReactPlatform platform = ReactPlatforms.get();
    spawns = platform.spawnHost().orElse(null);
    worlds = platform.worldHost().orElse(null);
    List<Subscription> opened = new ArrayList<>();
    if (spawns != null) {
      opened.add(spawns.onCreatureSpawn(ListenerPriority.LOWEST, true, this::on));
    }
    BlockHost blocks = platform.blockHost().orElse(null);
    if (blocks != null) {
      opened.add(blocks.onPlace(ListenerPriority.MONITOR, true, event -> invalidate(event.location())));
      opened.add(blocks.onBreak(ListenerPriority.MONITOR, true, event -> invalidate(event.location())));
    }
    GrowthHost growth = platform.growthHost().orElse(null);
    if (growth != null) {
      opened.add(growth.onSpread(ListenerPriority.MONITOR, true, event -> invalidate(event.location())));
    }
    subscriptions = opened;
  }

  @Override
  public void onDeactivate() {
    for (Subscription subscription : subscriptions) {
      subscription.cancel();
    }
    subscriptions = List.of();
    if (snapshotsByWorldChunk != null) {
      snapshotsByWorldChunk.clear();
    }
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    updateEngagement();
    pruneStaleSnapshots();
  }

  public long skippedAttemptsPerSecond() {
    return skippedAttempts.get();
  }

  public long evaluatedAttemptsPerSecond() {
    return evaluatedAttempts.get();
  }

  public long readAndResetSkipped() {
    return skippedAttempts.getAndSet(0L);
  }

  public long readAndResetEvaluated() {
    return evaluatedAttempts.getAndSet(0L);
  }

  void on(SpawnHost.Spawn event) {
    if (snapshotsByWorldChunk == null) {
      return;
    }

    if (!SpawnHost.isSpawnerReason(event.reason())) {
      return;
    }

    if (!event.isMonster()) {
      return;
    }

    GameLocation location = event.location();
    if (location == null || location.worldId() == null) {
      return;
    }

    WorldHost presence = worlds;
    if (presence != null && presence.anyPlayerNear(location, bypassRadius)) {
      return;
    }

    evaluatedAttempts.incrementAndGet();

    SpawnerLightSnapshot snapshot = snapshotFor(location);
    if (snapshot == null) {
      return;
    }

    if (snapshot.hasDarkCandidate(location.blockX(), location.blockY(), location.blockZ())) {
      return;
    }

    skippedAttempts.incrementAndGet();
    if (engaged) {
      event.cancel();
    }
  }

  void invalidate(GameLocation block) {
    if (snapshotsByWorldChunk == null || block == null || block.worldId() == null) {
      return;
    }

    UUID worldId = block.worldId();
    Long2ObjectOpenHashMap<SpawnerLightSnapshot> chunkMap = snapshotsByWorldChunk.get(worldId);
    if (chunkMap == null) {
      return;
    }

    long chunkKey = packChunk(block.chunkX(), block.chunkZ());
    synchronized (chunkMap) {
      if (chunkMap.remove(chunkKey) != null) {
        invalidatedChunks.incrementAndGet();
        if (chunkMap.isEmpty()) {
          snapshotsByWorldChunk.remove(worldId, chunkMap);
        }
      }
    }
  }

  private SpawnerLightSnapshot snapshotFor(GameLocation location) {
    SpawnHost host = spawns;
    if (host == null) {
      return null;
    }

    UUID worldId = location.worldId();
    long chunkKey = packChunk(location.chunkX(), location.chunkZ());
    Long2ObjectOpenHashMap<SpawnerLightSnapshot> chunkMap = snapshotsByWorldChunk
        .computeIfAbsent(worldId, ignored -> new Long2ObjectOpenHashMap<>());

    long now = invalidationCounter;
    long maxAge = Math.max(1, invalidationIntervalTicks);
    synchronized (chunkMap) {
      SpawnerLightSnapshot snapshot = chunkMap.get(chunkKey);
      if (snapshot != null && (now - snapshot.builtAt) <= maxAge) {
        return snapshot;
      }
    }

    SpawnerLightSnapshot rebuilt = buildSnapshot(host, location, now);

    synchronized (chunkMap) {
      SpawnerLightSnapshot existing = chunkMap.get(chunkKey);
      if (existing != null && (now - existing.builtAt) <= maxAge) {
        return existing;
      }

      if (chunkMap.size() >= Math.max(64, maxTrackedChunks)) {
        evictOldest(chunkMap);
      }

      chunkMap.put(chunkKey, rebuilt);
      return rebuilt;
    }
  }

  private SpawnerLightSnapshot buildSnapshot(SpawnHost host, GameLocation origin, long now) {
    LongOpenHashSet darkCells = new LongOpenHashSet();
    host.forEachDarkAir(origin, CACHE_HORIZONTAL_RADIUS, CACHE_VERTICAL_RADIUS, darkLightMax,
        (x, y, z) -> darkCells.add(packPos(x, y, z)));
    return new SpawnerLightSnapshot(darkCells, now);
  }

  private void evictOldest(Long2ObjectOpenHashMap<SpawnerLightSnapshot> chunkMap) {
    long oldestKey = Long.MIN_VALUE;
    long oldestStamp = Long.MAX_VALUE;
    Iterator<Long2ObjectOpenHashMap.Entry<SpawnerLightSnapshot>> iterator = chunkMap.long2ObjectEntrySet().fastIterator();
    while (iterator.hasNext()) {
      Long2ObjectOpenHashMap.Entry<SpawnerLightSnapshot> entry = iterator.next();
      long stamp = entry.getValue().builtAt;
      if (stamp < oldestStamp) {
        oldestStamp = stamp;
        oldestKey = entry.getLongKey();
      }
    }

    if (oldestStamp != Long.MAX_VALUE) {
      chunkMap.remove(oldestKey);
    }
  }

  private void pruneStaleSnapshots() {
    invalidationCounter++;
    long now = invalidationCounter;
    long maxAge = Math.max(1, invalidationIntervalTicks) * 4L;

    Iterator<Map.Entry<UUID, Long2ObjectOpenHashMap<SpawnerLightSnapshot>>> worldIterator = snapshotsByWorldChunk.entrySet().iterator();
    while (worldIterator.hasNext()) {
      Map.Entry<UUID, Long2ObjectOpenHashMap<SpawnerLightSnapshot>> worldEntry = worldIterator.next();
      Long2ObjectOpenHashMap<SpawnerLightSnapshot> chunkMap = worldEntry.getValue();
      synchronized (chunkMap) {
        Iterator<Long2ObjectOpenHashMap.Entry<SpawnerLightSnapshot>> chunkIterator = chunkMap.long2ObjectEntrySet().fastIterator();
        while (chunkIterator.hasNext()) {
          if (now - chunkIterator.next().getValue().builtAt > maxAge) {
            chunkIterator.remove();
          }
        }
        if (chunkMap.isEmpty()) {
          worldIterator.remove();
        }
      }
    }
  }

  private void updateEngagement() {
    double tickMs = sample(SamplerTickTime.ID);
    double incident = sample(SamplerIncidentScore.ID);
    if (!engaged) {
      if (tickMs >= engageTickTimeMs || incident >= engageIncidentScore) {
        engaged = true;
      }
      return;
    }

    if (tickMs <= releaseTickTimeMs && incident < engageIncidentScore) {
      engaged = false;
    }
  }

  private static long packChunk(int cx, int cz) {
    return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
  }

  private static long packPos(int x, int y, int z) {
    return ((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFF) << 26) | (z & 0x3FFFFFF);
  }

  private static final class SpawnerLightSnapshot {
    private final LongOpenHashSet darkCells;
    private final long builtAt;

    private SpawnerLightSnapshot(LongOpenHashSet darkCells, long builtAt) {
      this.darkCells = darkCells;
      this.builtAt = builtAt;
    }

    private boolean hasDarkCandidate(int blockX, int blockY, int blockZ) {
      if (darkCells.isEmpty()) {
        return false;
      }
      for (int dy = -CACHE_VERTICAL_RADIUS; dy <= CACHE_VERTICAL_RADIUS; dy++) {
        for (int dx = -CACHE_HORIZONTAL_RADIUS; dx <= CACHE_HORIZONTAL_RADIUS; dx++) {
          for (int dz = -CACHE_HORIZONTAL_RADIUS; dz <= CACHE_HORIZONTAL_RADIUS; dz++) {
            if (darkCells.contains(packPos(blockX + dx, blockY + dy, blockZ + dz))) {
              return true;
            }
          }
        }
      }
      return false;
    }
  }
}
