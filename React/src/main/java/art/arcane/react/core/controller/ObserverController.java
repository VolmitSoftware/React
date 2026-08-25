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

package art.arcane.react.core.controller;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.model.SampledServer;
import art.arcane.react.model.SampledWorld;
import art.arcane.react.util.cache.Cache;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import com.google.common.util.concurrent.AtomicDouble;
import io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent;
import io.papermc.paper.event.world.border.WorldBorderCenterChangeEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@EqualsAndHashCode(callSuper = true)
@Data
public class ObserverController extends TickedObject implements IController {
  private static final int INITIAL_CHUNK_SEED_BATCH = 256;
  private transient final SampledServer sampled;
  private transient final Object loadedWorldRotationLock;
  private transient Map<UUID, LoadedWorldChunkIndex> loadedChunksByWorld;
  private transient Set<UUID> loadedWorldRotation;
  private transient Map<UUID, InitialLoadedChunkSeed> initialChunkSeedsByWorld;
  private transient Queue<InitialLoadedChunkSeed> initialChunkSeedQueue;
  private transient Map<UUID, HeatmapWorldRef> heatmapWorldsById;
  private transient volatile List<HeatmapWorldRef> heatmapWorldSnapshot;
  private transient volatile boolean indexingLoadedChunks;

  public ObserverController() {
    super("react", "observer", 1000);
    sampled = new SampledServer();
    loadedWorldRotationLock = new Object();
  }


  @Override
  public void onTick() {
    seedInitialLoadedChunkCoordinates();
  }

  @Override
  public String getName() {
    return "Observer";
  }

  @Override
  public void start() {
    loadedChunksByWorld = new ConcurrentHashMap<>();
    loadedWorldRotation = new LinkedHashSet<>();
    initialChunkSeedsByWorld = new ConcurrentHashMap<>();
    initialChunkSeedQueue = new ConcurrentLinkedQueue<>();
    heatmapWorldsById = new ConcurrentHashMap<>();
    heatmapWorldSnapshot = List.of();
    indexingLoadedChunks = true;
    for (World world : Bukkit.getWorlds()) {
      indexWorld(world);
      Chunk[] loadedChunks = world.getLoadedChunks();
      if (loadedChunks.length > 0) {
        InitialLoadedChunkSeed seed = new InitialLoadedChunkSeed(world.getUID(), loadedChunks);
        initialChunkSeedsByWorld.put(seed.worldId(), seed);
        initialChunkSeedQueue.offer(seed);
      }
    }
  }

  @Override
  public void stop() {
    indexingLoadedChunks = false;
    synchronized (loadedWorldRotationLock) {
      if (loadedChunksByWorld != null) {
        loadedChunksByWorld.clear();
      }
      if (loadedWorldRotation != null) {
        loadedWorldRotation.clear();
      }
      if (initialChunkSeedsByWorld != null) {
        for (InitialLoadedChunkSeed seed : initialChunkSeedsByWorld.values()) {
          seed.retire();
        }
        initialChunkSeedsByWorld.clear();
      }
      if (initialChunkSeedQueue != null) {
        initialChunkSeedQueue.clear();
      }
    }
    if (heatmapWorldsById != null) {
      heatmapWorldsById.clear();
    }
    heatmapWorldSnapshot = List.of();
  }

  @Override
  public void postStart() {

  }

  public SampledChunk absoluteWorst() {
    SampledChunk worst = null;
    double worstTotal = Double.NEGATIVE_INFINITY;
    double worstSub = Double.NEGATIVE_INFINITY;

    for (SampledWorld world : sampled.getWorlds().values()) {
      for (SampledChunk chunk : world.getChunks().values()) {
        double total = chunk.totalScore();
        double sub = chunk.highestSubScore();
        if (total > worstTotal || (total == worstTotal && sub > worstSub)) {
          worst = chunk;
          worstTotal = total;
          worstSub = sub;
        }
      }
    }

    return worst;
  }

  public AtomicDouble get(Block b, Sampler sampler) {
    return get(b.getChunk(), sampler);
  }

  public AtomicDouble get(Chunk c, Sampler sampler) {
    return sampled.getChunk(c).get(sampler.getId());
  }

  public Optional<Double> sample(Chunk c, Sampler s) {
    return sampled.optionalChunk(c).flatMap(i -> i.optional(s.getId())).map(AtomicDouble::get);
  }

  public Optional<Double> sample(World world, int chunkX, int chunkZ, Sampler sampler) {
    return sampled.optionalChunk(world, chunkX, chunkZ)
        .flatMap(chunk -> chunk.optional(sampler.getId()))
        .map(AtomicDouble::get);
  }

  public Optional<Double> sample(String worldKey, int chunkX, int chunkZ, Sampler sampler) {
    return sampled.optionalChunk(worldKey, chunkX, chunkZ)
        .flatMap(chunk -> chunk.optional(sampler.getId()))
        .map(AtomicDouble::get);
  }

  public Optional<SampledChunk> sampledChunk(World world, int chunkX, int chunkZ) {
    return sampled.optionalChunk(world, chunkX, chunkZ);
  }

  public Optional<SampledChunk> sampledChunk(String worldKey, int chunkX, int chunkZ) {
    return sampled.optionalChunk(worldKey, chunkX, chunkZ);
  }

  public Optional<HeatmapWorldRef> heatmapWorld(UUID worldId) {
    if (worldId == null || heatmapWorldsById == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(heatmapWorldsById.get(worldId));
  }

  public Optional<HeatmapWorldRef> heatmapWorld(String requestedWorld) {
    List<HeatmapWorldRef> worlds = heatmapWorldSnapshot;
    if (worlds == null || worlds.isEmpty()) {
      return Optional.empty();
    }
    if (requestedWorld == null || requestedWorld.isBlank()) {
      return Optional.of(defaultHeatmapWorld(worlds));
    }

    String requested = requestedWorld.trim();
    for (HeatmapWorldRef world : worlds) {
      if (requested.equals(world.worldKey())
          || requested.equals(world.worldName())
          || requested.equals(world.worldId().toString())) {
        return Optional.of(world);
      }
    }
    return Optional.empty();
  }

  public List<HeatmapWorldRef> heatmapWorlds() {
    List<HeatmapWorldRef> worlds = heatmapWorldSnapshot;
    return worlds == null ? List.of() : worlds;
  }

  public List<LoadedChunkCoordinate> loadedChunkCoordinatesInRadius(
      World world,
      int centerX,
      int centerZ,
      int radius
  ) {
    return world == null
        ? List.of()
        : loadedChunkCoordinatesInRadius(world.getUID(), centerX, centerZ, radius);
  }

  public List<LoadedChunkCoordinate> loadedChunkCoordinatesInRadius(
      UUID worldId,
      int centerX,
      int centerZ,
      int radius
  ) {
    if (worldId == null || loadedChunksByWorld == null) {
      return List.of();
    }

    LoadedWorldChunkIndex worldIndex = loadedChunksByWorld.get(worldId);
    if (worldIndex == null || worldIndex.isEmpty()) {
      return List.of();
    }
    Map<Long, LoadedChunkRef> chunks = worldIndex.chunks();

    int safeRadius = Math.max(0, radius);
    long diameter = ((long) safeRadius * 2L) + 1L;
    long cells = diameter > 3_037_000_499L ? Long.MAX_VALUE : diameter * diameter;
    List<LoadedChunkCoordinate> result = new ArrayList<>(
        Math.min(chunks.size(), cells > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cells)
    );
    long radiusSquared = (long) safeRadius * safeRadius;
    if (chunks.size() <= cells) {
      for (LoadedChunkRef ref : chunks.values()) {
        long dx = (long) ref.chunkX() - centerX;
        long dz = (long) ref.chunkZ() - centerZ;
        if ((dx * dx) + (dz * dz) <= radiusSquared) {
          result.add(new LoadedChunkCoordinate(ref.chunkX(), ref.chunkZ()));
        }
      }
      return result;
    }

    for (int chunkX = centerX - safeRadius; chunkX <= centerX + safeRadius; chunkX++) {
      for (int chunkZ = centerZ - safeRadius; chunkZ <= centerZ + safeRadius; chunkZ++) {
        long dx = (long) chunkX - centerX;
        long dz = (long) chunkZ - centerZ;
        if ((dx * dx) + (dz * dz) > radiusSquared) {
          continue;
        }

        if (chunks.containsKey(Cache.key(chunkX, chunkZ))) {
          result.add(new LoadedChunkCoordinate(chunkX, chunkZ));
        }
      }
    }
    return result;
  }

  public List<LoadedChunkCoordinate> loadedChunkCoordinatesInBounds(
      UUID worldId,
      int minimumChunkX,
      int maximumChunkX,
      int minimumChunkZ,
      int maximumChunkZ
  ) {
    if (worldId == null
        || loadedChunksByWorld == null
        || minimumChunkX > maximumChunkX
        || minimumChunkZ > maximumChunkZ) {
      return List.of();
    }

    LoadedWorldChunkIndex worldIndex = loadedChunksByWorld.get(worldId);
    if (worldIndex == null || worldIndex.isEmpty()) {
      return List.of();
    }
    Map<Long, LoadedChunkRef> chunks = worldIndex.chunks();
    long width = ((long) maximumChunkX - minimumChunkX) + 1L;
    long height = ((long) maximumChunkZ - minimumChunkZ) + 1L;
    long cells = saturatedMultiply(width, height);
    List<LoadedChunkCoordinate> result = new ArrayList<>(
        Math.min(chunks.size(), cells > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cells)
    );
    if (chunks.size() <= cells) {
      for (LoadedChunkRef ref : chunks.values()) {
        if (ref.chunkX() >= minimumChunkX
            && ref.chunkX() <= maximumChunkX
            && ref.chunkZ() >= minimumChunkZ
            && ref.chunkZ() <= maximumChunkZ) {
          result.add(new LoadedChunkCoordinate(ref.chunkX(), ref.chunkZ()));
        }
      }
      return result;
    }

    for (long chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
      for (long chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
        LoadedChunkRef ref = chunks.get(Cache.key((int) chunkX, (int) chunkZ));
        if (ref != null) {
          result.add(new LoadedChunkCoordinate(ref.chunkX(), ref.chunkZ()));
        }
      }
    }
    return result;
  }

  public List<LoadedChunkTarget> nextLoadedChunkCoordinateBatch(int maximum) {
    if (!indexingLoadedChunks || loadedChunksByWorld == null || maximum <= 0) {
      return List.of();
    }

    List<LoadedChunkTarget> result = new ArrayList<>(Math.min(maximum, 256));
    int worldBudget = Math.min(maximum, loadedChunksByWorld.size());
    for (int inspectedWorlds = 0;
         inspectedWorlds < worldBudget && result.size() < maximum;
         inspectedWorlds++) {
      UUID worldId = rotateWorld();
      if (worldId == null) {
        break;
      }

      LoadedWorldChunkIndex worldIndex = loadedChunksByWorld.get(worldId);
      if (worldIndex == null) {
        continue;
      }
      worldIndex.appendNextCoordinates(worldId, maximum - result.size(), result);
    }
    return result;
  }

  public LoadedChunkCursor openLoadedChunkCursor() {
    if (!indexingLoadedChunks || loadedChunksByWorld == null) {
      return LoadedChunkCursor.empty();
    }
    List<CursorWorld> worlds = new ArrayList<>();
    for (Map.Entry<UUID, LoadedWorldChunkIndex> entry : loadedChunksByWorld.entrySet()) {
      worlds.add(new CursorWorld(entry.getKey(), entry.getValue()));
    }
    worlds.sort(Comparator.comparing(cursorWorld -> cursorWorld.worldId().toString()));
    return new LoadedChunkCursor(worlds);
  }

  public LoadedChunkCursor openLoadedChunkCursor(UUID worldId) {
    if (!indexingLoadedChunks || loadedChunksByWorld == null || worldId == null) {
      return LoadedChunkCursor.empty();
    }
    LoadedWorldChunkIndex worldIndex = loadedChunksByWorld.get(worldId);
    return worldIndex == null
        ? LoadedChunkCursor.empty()
        : new LoadedChunkCursor(List.of(new CursorWorld(worldId, worldIndex)));
  }

  public boolean isLoadedChunkCoordinateIndexReady() {
    return indexingLoadedChunks
        && initialChunkSeedsByWorld != null
        && initialChunkSeedsByWorld.isEmpty();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkLoadEvent event) {
    indexLoadedChunk(event.getChunk());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ChunkUnloadEvent event) {
    removeLoadedChunk(event.getChunk());
    sampled.removeChunk(event.getChunk());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(WorldLoadEvent event) {
    indexWorld(event.getWorld());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(SpawnChangeEvent event) {
    indexWorld(event.getWorld());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(WorldBorderCenterChangeEvent event) {
    indexWorld(
        event.getWorld(),
        event.getNewCenter(),
        event.getWorldBorder().getSize()
    );
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(WorldBorderBoundsChangeEvent event) {
    indexWorld(
        event.getWorld(),
        event.getWorldBorder().getCenter(),
        event.getNewSize()
    );
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(WorldUnloadEvent event) {
    if (event.isCancelled()) {
      return;
    }
    removeWorldChunkIndex(event.getWorld().getUID());
    if (heatmapWorldsById != null) {
      heatmapWorldsById.remove(event.getWorld().getUID());
      publishHeatmapWorldSnapshot();
    }
    sampled.removeWorld(event.getWorld());
  }

  private HeatmapWorldRef defaultHeatmapWorld(List<HeatmapWorldRef> worlds) {
    HeatmapWorldRef selected = worlds.get(0);
    int selectedLoadedChunks = loadedChunkCount(selected.worldId());
    for (int index = 1; index < worlds.size(); index++) {
      HeatmapWorldRef candidate = worlds.get(index);
      int candidateLoadedChunks = loadedChunkCount(candidate.worldId());
      if (candidateLoadedChunks > selectedLoadedChunks) {
        selected = candidate;
        selectedLoadedChunks = candidateLoadedChunks;
      }
    }
    return selected;
  }

  private int loadedChunkCount(UUID worldId) {
    if (loadedChunksByWorld == null) {
      return 0;
    }
    LoadedWorldChunkIndex worldIndex = loadedChunksByWorld.get(worldId);
    return worldIndex == null ? 0 : worldIndex.size();
  }

  private void indexWorld(World world) {
    if (world == null || heatmapWorldsById == null) {
      return;
    }
    WorldBorder border = world.getWorldBorder();
    Location borderCenter = border == null ? null : border.getCenter();
    double borderSize = border == null ? 0D : border.getSize();
    indexWorld(world, borderCenter, borderSize);
  }

  private void indexWorld(World world, Location borderCenter, double borderSize) {
    if (world == null || heatmapWorldsById == null) {
      return;
    }
    Location spawn = world.getSpawnLocation();
    HeatmapWorldRef snapshot = new HeatmapWorldRef(
        world.getUID(),
        WorldIdentity.serialize(world),
        world.getName(),
        spawn.getBlockX() >> 4,
        spawn.getBlockZ() >> 4,
        borderCenter == null ? 0D : borderCenter.getX(),
        borderCenter == null ? 0D : borderCenter.getZ(),
        Double.isFinite(borderSize) && borderSize >= 0D ? borderSize : 0D
    );
    heatmapWorldsById.put(snapshot.worldId(), snapshot);
    publishHeatmapWorldSnapshot();
  }

  private void publishHeatmapWorldSnapshot() {
    if (heatmapWorldsById == null || heatmapWorldsById.isEmpty()) {
      heatmapWorldSnapshot = List.of();
      return;
    }
    List<HeatmapWorldRef> worlds = new ArrayList<>(heatmapWorldsById.values());
    worlds.sort(Comparator.comparing(HeatmapWorldRef::worldKey));
    heatmapWorldSnapshot = List.copyOf(worlds);
  }

  private long saturatedMultiply(long left, long right) {
    if (left <= 0L || right <= 0L) {
      return 0L;
    }
    if (left > Long.MAX_VALUE / right) {
      return Long.MAX_VALUE;
    }
    return left * right;
  }

  private void indexLoadedChunk(Chunk chunk) {
    if (!indexingLoadedChunks || chunk == null || chunk.getWorld() == null || loadedChunksByWorld == null) {
      return;
    }

    UUID worldId = chunk.getWorld().getUID();
    int chunkX = chunk.getX();
    int chunkZ = chunk.getZ();
    indexLoadedChunkCoordinate(worldId, chunkX, chunkZ, true, null);
  }

  private void removeLoadedChunk(Chunk chunk) {
    if (chunk == null || chunk.getWorld() == null || loadedChunksByWorld == null) {
      return;
    }

    UUID worldId = chunk.getWorld().getUID();
    long chunkKey = Cache.key(chunk.getX(), chunk.getZ());
    synchronized (loadedWorldRotationLock) {
      InitialLoadedChunkSeed seed = initialChunkSeedsByWorld == null
          ? null
          : initialChunkSeedsByWorld.get(worldId);
      if (seed != null) {
        seed.retireCoordinate(chunkKey);
      }
      LoadedWorldChunkIndex worldIndex = loadedChunksByWorld.get(worldId);
      if (worldIndex != null) {
        worldIndex.remove(chunkKey);
      }
    }
  }

  private void indexLoadedChunkCoordinate(
      UUID worldId,
      int chunkX,
      int chunkZ,
      boolean authoritativeLoad,
      InitialLoadedChunkSeed expectedSeed
  ) {
    long chunkKey = Cache.key(chunkX, chunkZ);
    synchronized (loadedWorldRotationLock) {
      if (!indexingLoadedChunks || loadedChunksByWorld == null || loadedWorldRotation == null) {
        return;
      }
      InitialLoadedChunkSeed seed = initialChunkSeedsByWorld == null
          ? null
          : initialChunkSeedsByWorld.get(worldId);
      if (expectedSeed != null && seed != expectedSeed) {
        return;
      }
      if (authoritativeLoad) {
        if (seed != null) {
          seed.restoreCoordinate(chunkKey);
        }
      } else if (seed == null || seed.retiredCoordinate(chunkKey)) {
        return;
      }
      LoadedWorldChunkIndex worldIndex = loadedChunksByWorld.computeIfAbsent(
          worldId,
          ignored -> new LoadedWorldChunkIndex()
      );
      loadedWorldRotation.add(worldId);
      worldIndex.put(chunkKey, new LoadedChunkRef(chunkX, chunkZ));
    }
  }

  private void seedInitialLoadedChunkCoordinates() {
    Queue<InitialLoadedChunkSeed> seedQueue = initialChunkSeedQueue;
    Map<UUID, InitialLoadedChunkSeed> seedsByWorld = initialChunkSeedsByWorld;
    if (!indexingLoadedChunks
        || seedQueue == null
        || seedQueue.isEmpty()
        || seedsByWorld == null) {
      return;
    }

    int remaining = INITIAL_CHUNK_SEED_BATCH;
    List<InitialLoadedChunkSeed> requeue = new ArrayList<>();
    while (remaining > 0) {
      InitialLoadedChunkSeed seed = seedQueue.poll();
      if (seed == null) {
        break;
      }
      if (seedsByWorld.get(seed.worldId()) != seed) {
        seed.retire();
        continue;
      }

      InitialChunkSeedWindow window = seed.claim(remaining);
      Chunk[] chunks = window.chunks();
      for (int index = window.start(); index < window.end(); index++) {
        Chunk chunk = chunks[index];
        if (chunk != null) {
          indexLoadedChunkCoordinate(
              seed.worldId(),
              chunk.getX(),
              chunk.getZ(),
              false,
              seed
          );
        }
      }
      remaining -= window.size();
      if (seed.hasRemaining()) {
        requeue.add(seed);
      } else {
        seedsByWorld.remove(seed.worldId(), seed);
        seed.retire();
      }
    }

    for (InitialLoadedChunkSeed seed : requeue) {
      if (seedsByWorld.get(seed.worldId()) == seed) {
        seedQueue.offer(seed);
      } else {
        seed.retire();
      }
    }
  }

  private UUID rotateWorld() {
    synchronized (loadedWorldRotationLock) {
      if (loadedWorldRotation == null || loadedWorldRotation.isEmpty()) {
        return null;
      }
      Iterator<UUID> iterator = loadedWorldRotation.iterator();
      UUID worldId = iterator.next();
      iterator.remove();
      loadedWorldRotation.add(worldId);
      return worldId;
    }
  }

  private void removeWorldChunkIndex(UUID worldId) {
    synchronized (loadedWorldRotationLock) {
      InitialLoadedChunkSeed seed = initialChunkSeedsByWorld == null
          ? null
          : initialChunkSeedsByWorld.remove(worldId);
      if (seed != null) {
        seed.retire();
      }
      if (loadedChunksByWorld != null) {
        loadedChunksByWorld.remove(worldId);
      }
      if (loadedWorldRotation != null) {
        loadedWorldRotation.remove(worldId);
      }
    }
  }

  public record LoadedChunkCoordinate(int chunkX, int chunkZ) {
  }

  public record LoadedChunkTarget(UUID worldId, int chunkX, int chunkZ) {
  }

  public static final class LoadedChunkCursor {
    private static final int MAX_BATCH = 256;

    private final List<CursorWorld> worlds;
    private final int estimatedTotal;
    private int worldIndex;
    private Iterator<LoadedChunkRef> chunkIterator;

    private LoadedChunkCursor(List<CursorWorld> worlds) {
      this.worlds = List.copyOf(worlds);
      long total = 0L;
      for (CursorWorld world : worlds) {
        total += world.index().size();
      }
      estimatedTotal = (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static LoadedChunkCursor empty() {
      return new LoadedChunkCursor(List.of());
    }

    public synchronized List<LoadedChunkTarget> next(int maximum) {
      int limit = Math.max(0, Math.min(maximum, MAX_BATCH));
      if (limit == 0 || worldIndex >= worlds.size()) {
        return List.of();
      }
      List<LoadedChunkTarget> result = new ArrayList<>(limit);
      while (result.size() < limit && worldIndex < worlds.size()) {
        CursorWorld world = worlds.get(worldIndex);
        if (chunkIterator == null) {
          chunkIterator = world.index().chunks().values().iterator();
        }
        while (result.size() < limit && chunkIterator.hasNext()) {
          LoadedChunkRef chunk = chunkIterator.next();
          result.add(new LoadedChunkTarget(world.worldId(), chunk.chunkX(), chunk.chunkZ()));
        }
        if (!chunkIterator.hasNext()) {
          chunkIterator = null;
          worldIndex++;
        }
      }
      return result;
    }

    public int estimatedTotal() {
      return estimatedTotal;
    }
  }

  private record LoadedChunkRef(int chunkX, int chunkZ) {
  }

  private record CursorWorld(UUID worldId, LoadedWorldChunkIndex index) {
  }

  private static final class LoadedWorldChunkIndex {
    private final Map<Long, LoadedChunkRef> chunks;
    private final Set<Long> rotation;

    private LoadedWorldChunkIndex() {
      chunks = new ConcurrentHashMap<>();
      rotation = new LinkedHashSet<>();
    }

    private Map<Long, LoadedChunkRef> chunks() {
      return chunks;
    }

    private int size() {
      return chunks.size();
    }

    private boolean isEmpty() {
      return chunks.isEmpty();
    }

    private synchronized void put(long chunkKey, LoadedChunkRef ref) {
      chunks.put(chunkKey, ref);
      rotation.remove(chunkKey);
      rotation.add(chunkKey);
    }

    private synchronized void remove(long chunkKey) {
      chunks.remove(chunkKey);
      rotation.remove(chunkKey);
    }

    private synchronized void appendNextCoordinates(
        UUID worldId,
        int maximum,
        List<LoadedChunkTarget> result
    ) {
      int count = Math.min(maximum, rotation.size());
      for (int index = 0; index < count; index++) {
        Iterator<Long> iterator = rotation.iterator();
        long chunkKey = iterator.next();
        iterator.remove();
        rotation.add(chunkKey);
        LoadedChunkRef ref = chunks.get(chunkKey);
        if (ref != null) {
          result.add(new LoadedChunkTarget(worldId, ref.chunkX(), ref.chunkZ()));
        }
      }
    }
  }

  private static final class InitialLoadedChunkSeed {
    private final UUID worldId;
    private final Set<Long> retiredCoordinates;
    private Chunk[] loadedChunks;
    private int nextIndex;

    private InitialLoadedChunkSeed(UUID worldId, Chunk[] loadedChunks) {
      this.worldId = worldId;
      this.loadedChunks = loadedChunks;
      retiredCoordinates = new LinkedHashSet<>();
    }

    private UUID worldId() {
      return worldId;
    }

    private synchronized InitialChunkSeedWindow claim(int maximum) {
      if (loadedChunks == null || maximum <= 0 || nextIndex >= loadedChunks.length) {
        return InitialChunkSeedWindow.EMPTY;
      }
      int start = nextIndex;
      int end = start + Math.min(maximum, loadedChunks.length - start);
      nextIndex = end;
      return new InitialChunkSeedWindow(loadedChunks, start, end);
    }

    private synchronized boolean hasRemaining() {
      return loadedChunks != null && nextIndex < loadedChunks.length;
    }

    private synchronized void retireCoordinate(long chunkKey) {
      retiredCoordinates.add(chunkKey);
    }

    private synchronized void restoreCoordinate(long chunkKey) {
      retiredCoordinates.remove(chunkKey);
    }

    private synchronized boolean retiredCoordinate(long chunkKey) {
      return retiredCoordinates.contains(chunkKey);
    }

    private synchronized void retire() {
      loadedChunks = null;
      nextIndex = 0;
      retiredCoordinates.clear();
    }
  }

  private record InitialChunkSeedWindow(Chunk[] chunks, int start, int end) {
    private static final InitialChunkSeedWindow EMPTY = new InitialChunkSeedWindow(new Chunk[0], 0, 0);

    private int size() {
      return end - start;
    }
  }
}
