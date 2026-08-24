package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Iris Biome Chunk Share Pie Map feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIrisBiomeChunkSharePieMap extends FeatureIrisChunkSharePieBase implements CapabilityGatedFeature {
  public static final String ID = "iris-biome-chunk-share-pie-map";
  private static final int SAMPLE_INTERVAL_MS = 1000;
  private static final int MAX_SAMPLES_PER_PASS = 32;
  private static final int MAX_LIFECYCLE_SAMPLES_PER_PASS = 16;
  private static final int MAX_LIFECYCLE_POLLS_PER_PASS = 64;
  private static final int MAX_QUEUED_LIFECYCLE_SAMPLES = 8192;
  private static final int MAX_IN_FLIGHT_SAMPLES = 128;

  private transient volatile SamplingState samplingState;

  public FeatureIrisBiomeChunkSharePieMap() {
    super(ID);
  }

  @Override
  public void onActivate() {
    SamplingState previous = samplingState;
    samplingState = new SamplingState();
    if (previous != null) {
      previous.retire();
    }
  }

  @Override
  public void onDeactivate() {
    SamplingState retired = samplingState;
    samplingState = null;
    if (retired != null) {
      retired.retire();
    }
  }

  @Override
  public int getTickInterval() {
    return SAMPLE_INTERVAL_MS;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("iris");
  }

  @Override
  public void onTick() {
    SamplingState state = samplingState;
    if (state == null || !state.beginPass()) {
      return;
    }

    try {
      sampleNextBatch(state);
    } finally {
      state.finishPass();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {
    SamplingState state = samplingState;
    Chunk chunk = event.getChunk();
    if (state == null || chunk == null || chunk.getWorld() == null) {
      return;
    }

    ChunkCoordinate coordinate = new ChunkCoordinate(
        chunk.getWorld().getUID(),
        chunk.getX(),
        chunk.getZ()
    );
    state.removeCoordinate(coordinate);
    state.offerLifecycleSample(coordinate);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onChunkUnload(ChunkUnloadEvent event) {
    SamplingState state = samplingState;
    Chunk chunk = event.getChunk();
    if (state == null || chunk == null || chunk.getWorld() == null) {
      return;
    }

    state.removeCoordinate(new ChunkCoordinate(
        chunk.getWorld().getUID(),
        chunk.getX(),
        chunk.getZ()
    ));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onWorldLoad(WorldLoadEvent event) {
    SamplingState state = samplingState;
    World world = event.getWorld();
    if (state != null && world != null) {
      state.advanceWorld(world.getUID());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onWorldUnload(WorldUnloadEvent event) {
    if (event.isCancelled()) {
      return;
    }

    SamplingState state = samplingState;
    World world = event.getWorld();
    if (state != null && world != null) {
      state.advanceWorld(world.getUID());
    }
  }

  @Override
  protected TextKey title() {
    return RendererMessages.TITLE_BIOME_CHUNK_SHARE;
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(66, 138, 94);
  }

  @Override
  protected Map<String, Long> collectBuckets(Player viewer) {
    Map<String, Long> counts = newCounterMap();
    if (viewer == null) {
      return counts;
    }

    World world = targetWorld(viewer);
    SamplingState state = samplingState;
    if (world == null || state == null) {
      return counts;
    }

    counts.putAll(state.snapshot(world.getUID()));
    return counts;
  }

  void sampleNextBatch() {
    SamplingState state = samplingState;
    if (state != null) {
      sampleNextBatch(state);
    }
  }

  Map<String, Long> bucketSnapshot(UUID worldId) {
    SamplingState state = samplingState;
    return state == null ? Map.of() : state.snapshot(worldId);
  }

  private void sampleNextBatch(SamplingState state) {
    if (!isCurrent(state)) {
      return;
    }

    int scheduled = scheduleLifecycleSamples(state);
    if (scheduled >= MAX_SAMPLES_PER_PASS || !state.hasInFlightCapacity()) {
      return;
    }

    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null) {
      return;
    }

    List<ObserverController.LoadedChunkTarget> targets = observer.nextLoadedChunkCoordinateBatch(
        MAX_SAMPLES_PER_PASS
    );
    for (ObserverController.LoadedChunkTarget target : targets) {
      if (target == null || scheduled >= MAX_SAMPLES_PER_PASS || !isCurrent(state)) {
        break;
      }

      ChunkCoordinate coordinate = new ChunkCoordinate(
          target.worldId(),
          target.chunkX(),
          target.chunkZ()
      );
      if (scheduleSample(state, state.request(coordinate))) {
        scheduled++;
      }
    }
  }

  private int scheduleLifecycleSamples(SamplingState state) {
    int scheduled = 0;
    int polled = 0;
    while (scheduled < MAX_LIFECYCLE_SAMPLES_PER_PASS
        && polled < MAX_LIFECYCLE_POLLS_PER_PASS
        && isCurrent(state)
        && state.hasInFlightCapacity()) {
      SamplingRequest request = state.pollLifecycleSample();
      if (request == null) {
        if (state.lifecycleQueueEmpty()) {
          break;
        }
        polled++;
        continue;
      }

      polled++;
      if (state.isCurrent(request) && scheduleSample(state, request)) {
        scheduled++;
      }
    }
    return scheduled;
  }

  private boolean scheduleSample(SamplingState state, SamplingRequest request) {
    if (!isCurrent(state) || !state.isCurrent(request) || !state.startRequest(request)) {
      return false;
    }

    ChunkCoordinate coordinate = request.coordinate();
    World world = Bukkit.getWorld(coordinate.worldId());
    if (world == null) {
      state.removeWorldDistribution(coordinate.worldId());
      state.finishRequest(request);
      return false;
    }

    boolean scheduled = J.runChunk(
        world,
        coordinate.chunkX(),
        coordinate.chunkZ(),
        () -> sampleOnOwner(state, request, world)
    );
    if (!scheduled) {
      state.finishRequest(request);
    }
    return scheduled;
  }

  private void sampleOnOwner(SamplingState state, SamplingRequest request, World world) {
    try {
      if (!isCurrent(state) || !state.isRunning(request)) {
        return;
      }

      ChunkCoordinate coordinate = request.coordinate();
      if (!world.isChunkLoaded(coordinate.chunkX(), coordinate.chunkZ())) {
        state.removeCoordinate(coordinate);
        return;
      }

      Chunk chunk = world.getChunkAt(coordinate.chunkX(), coordinate.chunkZ());
      int sampleY = deterministicSampleY(world);
      String biome = labelForChunkBiome(chunk, sampleY);
      if (isCurrent(state) && state.isRunning(request)) {
        state.record(coordinate, biome);
      }
    } finally {
      state.finishRequest(request);
    }
  }

  private int deterministicSampleY(World world) {
    int minimum = world.getMinHeight();
    int maximum = Math.max(minimum, world.getMaxHeight() - 1);
    return Math.max(minimum, Math.min(maximum, world.getSeaLevel()));
  }

  private boolean isCurrent(SamplingState state) {
    return state != null && state.active() && samplingState == state;
  }

  private record ChunkCoordinate(UUID worldId, int chunkX, int chunkZ) {
  }

  private record SamplingRequest(ChunkCoordinate coordinate, long worldEpoch) {
  }

  private static final class SamplingState {
    private final Map<UUID, WorldBiomeDistribution> distributions;
    private final Map<UUID, Long> worldEpochs;
    private final Queue<SamplingRequest> lifecycleQueue;
    private final Set<SamplingRequest> queuedLifecycleSamples;
    private final AtomicInteger queuedLifecycleCount;
    private final Set<SamplingRequest> inFlightSamples;
    private final AtomicInteger inFlightCount;
    private final AtomicBoolean samplingPass;
    private volatile boolean active;

    private SamplingState() {
      distributions = new ConcurrentHashMap<>();
      worldEpochs = new ConcurrentHashMap<>();
      lifecycleQueue = new ConcurrentLinkedQueue<>();
      queuedLifecycleSamples = ConcurrentHashMap.newKeySet();
      queuedLifecycleCount = new AtomicInteger();
      inFlightSamples = ConcurrentHashMap.newKeySet();
      inFlightCount = new AtomicInteger();
      samplingPass = new AtomicBoolean();
      active = true;
    }

    private boolean active() {
      return active;
    }

    private boolean beginPass() {
      return active && samplingPass.compareAndSet(false, true);
    }

    private void finishPass() {
      samplingPass.set(false);
    }

    private SamplingRequest request(ChunkCoordinate coordinate) {
      return new SamplingRequest(coordinate, worldEpoch(coordinate.worldId()));
    }

    private long worldEpoch(UUID worldId) {
      return worldEpochs.getOrDefault(worldId, 0L);
    }

    private boolean isCurrent(SamplingRequest request) {
      return active && request.worldEpoch() == worldEpoch(request.coordinate().worldId());
    }

    private boolean isRunning(SamplingRequest request) {
      return isCurrent(request) && inFlightSamples.contains(request);
    }

    private void offerLifecycleSample(ChunkCoordinate coordinate) {
      if (!active) {
        return;
      }

      SamplingRequest request = request(coordinate);
      if (!queuedLifecycleSamples.add(request)) {
        return;
      }
      if (!reserve(queuedLifecycleCount, MAX_QUEUED_LIFECYCLE_SAMPLES)) {
        queuedLifecycleSamples.remove(request);
        return;
      }
      if (!active) {
        queuedLifecycleSamples.remove(request);
        release(queuedLifecycleCount);
        return;
      }
      lifecycleQueue.offer(request);
    }

    private SamplingRequest pollLifecycleSample() {
      SamplingRequest request = lifecycleQueue.poll();
      if (request == null) {
        return null;
      }
      release(queuedLifecycleCount);
      return queuedLifecycleSamples.remove(request) ? request : null;
    }

    private boolean lifecycleQueueEmpty() {
      return lifecycleQueue.isEmpty();
    }

    private boolean startRequest(SamplingRequest request) {
      if (!active || !isCurrent(request) || !inFlightSamples.add(request)) {
        return false;
      }
      if (!reserve(inFlightCount, MAX_IN_FLIGHT_SAMPLES)) {
        inFlightSamples.remove(request);
        return false;
      }
      return true;
    }

    private void finishRequest(SamplingRequest request) {
      if (inFlightSamples.remove(request)) {
        release(inFlightCount);
      }
    }

    private boolean hasInFlightCapacity() {
      return inFlightCount.get() < MAX_IN_FLIGHT_SAMPLES;
    }

    private void record(ChunkCoordinate coordinate, String biome) {
      if (!active) {
        return;
      }
      distributions.computeIfAbsent(
          coordinate.worldId(),
          ignored -> new WorldBiomeDistribution()
      ).record(chunkKey(coordinate.chunkX(), coordinate.chunkZ()), biome);
    }

    private void removeCoordinate(ChunkCoordinate coordinate) {
      SamplingRequest request = request(coordinate);
      queuedLifecycleSamples.remove(request);
      finishRequest(request);
      WorldBiomeDistribution distribution = distributions.get(coordinate.worldId());
      if (distribution != null) {
        distribution.remove(chunkKey(coordinate.chunkX(), coordinate.chunkZ()));
      }
    }

    private Map<String, Long> snapshot(UUID worldId) {
      WorldBiomeDistribution distribution = distributions.get(worldId);
      return distribution == null ? Map.of() : distribution.snapshot();
    }

    private void advanceWorld(UUID worldId) {
      worldEpochs.merge(worldId, 1L, Long::sum);
      removeWorldDistribution(worldId);
    }

    private void removeWorldDistribution(UUID worldId) {
      distributions.remove(worldId);
    }

    private void retire() {
      active = false;
      samplingPass.set(false);
      distributions.clear();
      worldEpochs.clear();
      lifecycleQueue.clear();
      queuedLifecycleSamples.clear();
      queuedLifecycleCount.set(0);
      inFlightSamples.clear();
      inFlightCount.set(0);
    }

    private static boolean reserve(AtomicInteger count, int maximum) {
      while (true) {
        int current = count.get();
        if (current >= maximum) {
          return false;
        }
        if (count.compareAndSet(current, current + 1)) {
          return true;
        }
      }
    }

    private static void release(AtomicInteger count) {
      while (true) {
        int current = count.get();
        if (current <= 0 || count.compareAndSet(current, current - 1)) {
          return;
        }
      }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
      return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
  }

  private static final class WorldBiomeDistribution {
    private final Map<Long, String> biomeByChunk;
    private final Map<String, Long> countsByBiome;

    private WorldBiomeDistribution() {
      biomeByChunk = new HashMap<>();
      countsByBiome = new HashMap<>();
    }

    private synchronized void record(long chunkKey, String biome) {
      String normalized = biome == null ? "" : biome.trim();
      String previous = biomeByChunk.put(chunkKey, normalized);
      if (normalized.equals(previous)) {
        return;
      }
      if (previous != null) {
        decrement(previous);
      }
      countsByBiome.merge(normalized, 1L, Long::sum);
    }

    private synchronized void remove(long chunkKey) {
      String previous = biomeByChunk.remove(chunkKey);
      if (previous != null) {
        decrement(previous);
      }
    }

    private synchronized Map<String, Long> snapshot() {
      return new LinkedHashMap<>(countsByBiome);
    }

    private void decrement(String biome) {
      long next = countsByBiome.getOrDefault(biome, 0L) - 1L;
      if (next <= 0L) {
        countsByBiome.remove(biome);
      } else {
        countsByBiome.put(biome, next);
      }
    }
  }
}
