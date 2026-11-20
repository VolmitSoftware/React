package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.platform.bukkit.BukkitMapTargets;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.ChunkGenerator;

import java.lang.reflect.Method;
import java.util.Locale;

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

/**
 * Samples one biome per loaded chunk for the Iris biome share map on Bukkit. Iris worlds report Iris biome names through
 * the engine; other worlds report the vanilla biome. Sampling runs between {@link #start()} and {@link #stop()}.
 */
public class IrisBiomeChunkSampler implements Listener {
  private static final Object IRIS_REFLECTION_LOCK = new Object();
  private static final Map<Class<?>, Method> accessEngineMethods = new ConcurrentHashMap<>();
  private static final Set<Class<?>> accessEngineMethodMisses = ConcurrentHashMap.newKeySet();
  private static final Map<Class<?>, Method> engineBiomeMethods = new ConcurrentHashMap<>();
  private static final Set<Class<?>> engineBiomeMethodMisses = ConcurrentHashMap.newKeySet();
  private static final Map<Class<?>, Method> biomeNameMethods = new ConcurrentHashMap<>();
  private static final Set<Class<?>> biomeNameMethodMisses = ConcurrentHashMap.newKeySet();
  private static final Map<Class<?>, Method> biomeLoadKeyMethods = new ConcurrentHashMap<>();
  private static final Set<Class<?>> biomeLoadKeyMethodMisses = ConcurrentHashMap.newKeySet();
  private static volatile boolean irisAccessLookupAttempted = false;
  private static volatile Method irisAccessMethod = null;
  private static final int MAX_SAMPLES_PER_PASS = 32;
  private static final int MAX_LIFECYCLE_SAMPLES_PER_PASS = 16;
  private static final int MAX_LIFECYCLE_POLLS_PER_PASS = 64;
  private static final int MAX_QUEUED_LIFECYCLE_SAMPLES = 8192;
  private static final int MAX_IN_FLIGHT_SAMPLES = 128;

  private volatile SamplingState samplingState;

  public synchronized void start() {
    SamplingState previous = samplingState;
    samplingState = new SamplingState();
    if (previous != null) {
      previous.retire();
    } else if (React.instance != null) {
      React.instance.registerListener(this);
    }
  }

  public synchronized void stop() {
    SamplingState retired = samplingState;
    samplingState = null;
    if (retired != null) {
      retired.retire();
      if (React.instance != null) {
        React.instance.unregisterListener(this);
      }
    }
  }

  public void tick() {
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

  public Map<String, Long> buckets(MapRenderTarget target) {
    return collectBuckets(target, BukkitMapTargets.player(target));
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

  private Map<String, Long> collectBuckets(MapRenderTarget target, Player viewer) {
    Map<String, Long> counts = SharePieRenderer.newCounterMap();
    if (viewer == null) {
      return counts;
    }

    World world = targetWorld(target, viewer);
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

  private boolean isLikelyIrisWorld(World world) {
    if (world == null) {
      return false;
    }

    try {
      ChunkGenerator generator = world.getGenerator();
      if (generator != null) {
        String className = generator.getClass().getName().toLowerCase(Locale.ROOT);
        if (className.contains("iris")) {
          return true;
        }
      }
    } catch (Throwable ignored) {
    }

    return WorldIdentity.serialize(world).toLowerCase(Locale.ROOT).contains("iris");
  }

  private World targetWorld(MapRenderTarget target, Player viewer) {
    try {
      if (BukkitMapTargets.world(target) != null) {
        return BukkitMapTargets.world(target);
      }
    } catch (Throwable ignored) {
    }

    Location anchor = anchorLocation(target, viewer);
    if (anchor != null && anchor.getWorld() != null) {
      return anchor.getWorld();
    }

    return viewer == null ? null : viewer.getWorld();
  }

  private Location anchorLocation(MapRenderTarget target, Player viewer) {
    MapController controller = React.controller(MapController.class);
    if (controller == null) {
      return viewer == null ? null : viewer.getLocation();
    }

    return controller.resolveMapAnchor(target);
  }

  protected String labelForChunkBiome(Chunk chunk, int y) {
    if (chunk == null || chunk.getWorld() == null) {
      return ReactLanguage.raw(RendererMessages.PIE_UNKNOWN);
    }

    World world = chunk.getWorld();
    if (isLikelyIrisWorld(world)) {
      String irisBiome = resolveIrisBiomeLabel(world, (chunk.getX() << 4) + 8, y, (chunk.getZ() << 4) + 8);
      if (irisBiome != null && !irisBiome.isBlank()) {
        return SharePieRenderer.displayName(irisBiome);
      }
    }

    try {
      return SharePieRenderer.displayName(chunk.getBlock(8, y, 8).getBiome().getKey().getKey());
    } catch (Throwable ignored) {
      return ReactLanguage.raw(RendererMessages.PIE_UNKNOWN);
    }
  }

  private String resolveIrisBiomeLabel(World world, int x, int y, int z) {
    Method access = irisAccessMethod();
    if (access == null || world == null) {
      return null;
    }

    try {
      Object provider = access.invoke(null, world);
      if (provider == null) {
        return null;
      }

      Method getEngine = cachedMethod(accessEngineMethods, accessEngineMethodMisses, provider.getClass(), "getEngine");
      if (getEngine == null) {
        return null;
      }

      Object engine = getEngine.invoke(provider);
      if (engine == null) {
        return null;
      }

      Method getBiome = cachedMethod(engineBiomeMethods, engineBiomeMethodMisses, engine.getClass(), "getBiome", int.class, int.class, int.class);
      if (getBiome == null) {
        return null;
      }

      Object biome = getBiome.invoke(engine, x, y, z);
      if (biome == null) {
        return null;
      }

      Method getName = cachedMethod(biomeNameMethods, biomeNameMethodMisses, biome.getClass(), "getName");
      Method getLoadKey = cachedMethod(biomeLoadKeyMethods, biomeLoadKeyMethodMisses, biome.getClass(), "getLoadKey");
      String name = invokeString(biome, getName);
      if (name != null && !name.isBlank()) {
        return name;
      }

      String loadKey = invokeString(biome, getLoadKey);
      if (loadKey != null && !loadKey.isBlank()) {
        return loadKey;
      }

      return null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private Method irisAccessMethod() {
    Method known = irisAccessMethod;
    if (known != null) {
      return known;
    }

    if (irisAccessLookupAttempted) {
      return null;
    }

    synchronized (IRIS_REFLECTION_LOCK) {
      if (!irisAccessLookupAttempted) {
        irisAccessLookupAttempted = true;
        try {
          Class<?> toolbelt = Class.forName("art.arcane.iris.world.IrisToolbelt");
          Method method = toolbelt.getMethod("access", World.class);
          method.setAccessible(true);
          irisAccessMethod = method;
        } catch (Throwable ignored) {
          irisAccessMethod = null;
        }
      }
      return irisAccessMethod;
    }
  }

  private Method cachedMethod(
      Map<Class<?>, Method> cache,
      Set<Class<?>> misses,
      Class<?> owner,
      String methodName,
      Class<?>... params
  ) {
    if (owner == null || misses.contains(owner)) {
      return null;
    }

    Method cached = cache.get(owner);
    if (cached != null) {
      return cached;
    }

    try {
      Method method = owner.getMethod(methodName, params);
      method.setAccessible(true);
      cache.put(owner, method);
      return method;
    } catch (Throwable ignored) {
      misses.add(owner);
      return null;
    }
  }

  private String invokeString(Object target, Method method) {
    if (target == null || method == null) {
      return null;
    }

    try {
      Object value = method.invoke(target);
      return value == null ? null : value.toString();
    } catch (Throwable ignored) {
      return null;
    }
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
