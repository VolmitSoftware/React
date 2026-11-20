package art.arcane.react.content.feature;

import art.arcane.react.api.rendering.MapComposer;
import art.arcane.react.api.rendering.MapRendererRegistry;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.RendererReactMetrics;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerReactJobsQueue;
import art.arcane.react.content.sampler.SamplerSchedulerBacklog;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.content.sampler.SamplerTicksPerSecond;
import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Draws every core dashboard through plain hosts fed the same data as the Bukkit golden test
 * ({@code DashboardMapsGoldenTest}) and expects the same bytes, so any platform host that reports the same data draws
 * the same map. Also pins the mod wording of the owner impact list.
 */
class DashboardRenderersParityTest {
  private static final String WORLD_KEY = "minecraft:overworld";
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000beef");
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
  private static final Map<String, String> BUKKIT_GOLDEN = ordered(
      "react-metrics", "9ecd087bc796e5bc",
      "react-metrics-wall", "2b041835de06b3b0",
      "chunk-sampler-map", "1ad670efc09f9597",
      "chunk-load-gen-cost-map", "609b559a232edfc1",
      "entity-pressure-heatmap", "e0d2eb0e10bcca98",
      "redstone-activity-heatmap", "9bcc1b16c8c2fa68",
      "hopper-container-throughput-map", "d3a93103d636b5ab",
      "player-impact-overlay", "839b7dd13a6c19fb",
      "player-impact-overlay-framed", "a728cbcfd079fa1c",
      "heatmap-no-world", "f77a609ced4647ae",
      "plugin-event-impact-list-map", "cbe4d63824bb9975",
      "plugin-event-impact-list-map-wall", "cac99e5722bbe340",
      "plugin-event-impact-list-map-empty", "60e398b6dde34ab0"
  );

  @AfterEach
  void tearDown() throws ReflectiveOperationException {
    ReactPlatforms.unbind();
    resetImpactSeries();
  }

  @Test
  void coreRenderersDrawTheBukkitBytesFromPlainHosts() throws Exception {
    Map<String, String> actual = new LinkedHashMap<>();
    FixedAnchors anchors = new FixedAnchors(new MapAnchorHost.MapAnchor(WORLD, 20D, 36D, false, VIEWER));
    FixedChunks chunks = new FixedChunks();
    FixedEvents events = new FixedEvents(Map.of("WorldEdit", 42.5D, "essentials_x", 12D, "Iris", 3.25D, "tiny-plugin", 0.5D),
        Map.of("WorldEdit", 1200, "essentials_x", 300, "Iris", 75, "tiny-plugin", 9), Map.of());
    MapRenderTarget held = new MapRenderTarget(7, WORLD, VIEWER);
    ChunkHeatmapRenderer.Settings settings = ChunkHeatmapRenderer.DEFAULT_SETTINGS;

    RendererReactMetrics metrics = new RendererReactMetrics(DashboardRenderersParityTest::sampler);
    actual.put("react-metrics", hash(metrics, held, null));
    actual.put("react-metrics-wall", hash(metrics, held, new MegamapGrid.MegamapTile(3, 2, 1, 0)));
    actual.put("chunk-sampler-map", hash(new ChunkCostHeatmap(chunks, anchors, settings), held, null));
    actual.put("chunk-load-gen-cost-map", hash(new ChunkLoadGenCostHeatmap(chunks, anchors, settings), held, null));
    actual.put("entity-pressure-heatmap", hash(new EntityPressureHeatmap(chunks, anchors, settings), held, null));
    actual.put("redstone-activity-heatmap", hash(new RedstoneActivityHeatmap(chunks, anchors, settings), held, null));
    actual.put("hopper-container-throughput-map", hash(new HopperThroughputHeatmap(chunks, anchors, settings), held, null));
    actual.put("player-impact-overlay",
        hash(new PlayerImpactHeatmap(chunks, anchors, PlayerImpactHeatmap.DEFAULT_PLAYER_SETTINGS), held, null));
    FixedAnchors framed = new FixedAnchors(new MapAnchorHost.MapAnchor(WORLD, -40D, 8D, true, VIEWER));
    actual.put("player-impact-overlay-framed",
        hash(new PlayerImpactHeatmap(chunks, framed, PlayerImpactHeatmap.DEFAULT_PLAYER_SETTINGS), held, null));
    actual.put("heatmap-no-world", hash(new EntityPressureHeatmap(chunks, new FixedAnchors(null), settings), held, null));

    resetImpactSeries();
    actual.put("plugin-event-impact-list-map", hash(new OwnerEventImpactListRenderer(framed, () -> events, () -> 0), held, null));
    resetImpactSeries();
    actual.put("plugin-event-impact-list-map-wall",
        hash(new OwnerEventImpactListRenderer(framed, () -> events, () -> 0), held, new MegamapGrid.MegamapTile(4, 2, 0, 0)));
    resetImpactSeries();
    FixedEvents idle = new FixedEvents(Map.of(), Map.of(), Map.of());
    actual.put("plugin-event-impact-list-map-empty", hash(new OwnerEventImpactListRenderer(framed, () -> idle, () -> 0), held, null));

    Assertions.assertEquals(BUKKIT_GOLDEN, actual);
  }

  @Test
  void modServersListModsByDisplayName() throws Exception {
    MapRenderTarget held = new MapRenderTarget(7, WORLD, VIEWER);
    FixedAnchors anchors = new FixedAnchors(null);
    FixedEvents mods = new FixedEvents(Map.of("create", 42.5D, "ae2", 12D), Map.of("create", 1200, "ae2", 300),
        Map.of("create", "Create", "ae2", "Applied Energistics 2"));
    String plugin = hash(new OwnerEventImpactListRenderer(anchors, () -> mods, () -> 0), held, null);
    resetImpactSeries();
    ReactPlatforms.bind(new ModdedPlatform());
    String mod = hash(new OwnerEventImpactListRenderer(anchors, () -> mods, () -> 0), held, null);

    Assertions.assertNotEquals(plugin, mod, "the mod list must not reuse plugin wording");
    Assertions.assertEquals("234ed9c2cb545c68", mod);
    List<String> owners = PluginEventImpactSeries.snapshot(mods).entries().stream().map(PluginEventImpactSeries.Entry::plugin).toList();
    Assertions.assertEquals(List.of("Create", "Applied Energistics 2"), owners);
  }

  @Test
  void modDisplayNamesKeepTheirOwnCasing() throws Exception {
    resetImpactSeries();
    FixedEvents mods = new FixedEvents(Map.of("neoforge", 9D, "ae2", 5D, "some_mod", 2D), Map.of(),
        Map.of("neoforge", "NeoForge", "ae2", "AE2"));
    List<String> owners = PluginEventImpactSeries.snapshot(mods).entries().stream().map(PluginEventImpactSeries.Entry::plugin).toList();
    Assertions.assertEquals(List.of("NeoForge", "AE2", "Some Mod"), owners);
  }

  private static String hash(ReactRenderer renderer, MapRenderTarget target, MegamapGrid.MegamapTile tile) throws Exception {
    MapRendererRegistry registry = new MapRendererRegistry();
    registry.register(renderer);
    MapComposer composer = new MapComposer(target.mapId(), target.worldId(), renderer);
    composer.compose(registry, target, tile, null, 0L, 32, 1_000L);
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(composer.published())).substring(0, 16);
  }

  private static Sampler sampler(String id) {
    if (SamplerIncidentScore.ID.equals(id)) {
      return null;
    }
    double value = switch (id) {
      case SamplerTicksPerSecond.ID -> 19.62D;
      case SamplerTickTime.ID -> 38.4D;
      case SamplerReactJobsQueue.ID -> 412D;
      case SamplerSchedulerBacklog.ID -> Double.NaN;
      default -> 1D;
    };
    return new FixedSampler(id, value);
  }

  private static void resetImpactSeries() throws ReflectiveOperationException {
    for (String name : List.of("rollingScore", "lastSeenMS")) {
      Field field = PluginEventImpactSeries.class.getDeclaredField(name);
      field.setAccessible(true);
      ((Map<?, ?>) field.get(null)).clear();
    }
    Field last = PluginEventImpactSeries.class.getDeclaredField("lastUpdateMS");
    last.setAccessible(true);
    last.setLong(null, 0L);
  }

  private static Map<String, String> ordered(String... pairs) {
    Map<String, String> golden = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      golden.put(pairs[i], pairs[i + 1]);
    }
    return golden;
  }

  private record FixedAnchors(MapAnchorHost.MapAnchor anchor) implements MapAnchorHost {
    @Override
    public Optional<MapAnchor> anchor(MapRenderTarget target) {
      return Optional.ofNullable(anchor);
    }

    @Override
    public boolean frameAnchored(MapRenderTarget target) {
      return anchor != null && anchor.frameAnchored();
    }
  }

  private static final class FixedChunks implements ChunkHeatmapHost {
    private static final List<LoadedChunkCoordinate> LOADED = List.of(
        new LoadedChunkCoordinate(0, 0), new LoadedChunkCoordinate(1, 2), new LoadedChunkCoordinate(-2, 3),
        new LoadedChunkCoordinate(4, -1), new LoadedChunkCoordinate(-3, -3), new LoadedChunkCoordinate(2, 4),
        new LoadedChunkCoordinate(-1, 1), new LoadedChunkCoordinate(3, 3));

    @Override
    public Optional<HeatmapWorldRef> heatmapWorld(UUID worldId) {
      return WORLD.equals(worldId)
          ? Optional.of(new HeatmapWorldRef(WORLD, WORLD_KEY, "world", 0, 0, 0D, 0D, 60_000_000D))
          : Optional.empty();
    }

    @Override
    public List<LoadedChunkCoordinate> loadedChunks(UUID worldId, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
      return LOADED;
    }

    @Override
    public double chunkSample(String worldKey, int chunkX, int chunkZ, String samplerId) {
      int salt = Math.floorMod(samplerId.hashCode(), 7) + 1;
      if (Math.floorMod(chunkX + chunkZ + salt, 5) == 0) {
        return 0D;
      }
      return ((Math.abs(chunkX) * 3D) + (Math.abs(chunkZ) * 2D) + 1D) * salt * 0.75D;
    }

    @Override
    public double chunkTotalScore(String worldKey, int chunkX, int chunkZ) {
      return (Math.abs(chunkX) * 1.5D) + (Math.abs(chunkZ) + 0.25D);
    }

    @Override
    public List<PlayerPosition> playersInColumn(UUID worldId, double x, double z, double radiusBlocks) {
      return List.of(
          new PlayerPosition(VIEWER, "alice", 20D, 36D, 0.2D, false, false),
          new PlayerPosition(UUID.fromString("00000000-0000-0000-0000-0000000000bb"), "bob", -30D, 10D, 1.4D, true, false),
          new PlayerPosition(UUID.fromString("00000000-0000-0000-0000-0000000000cc"), "carol", 70D, 60D, 0D, false, true));
    }
  }

  private record FixedEvents(Map<String, Double> time, Map<String, Integer> calls, Map<String, String> names)
      implements EventMeasurement {
    @Override
    public boolean isMeasuring() {
      return true;
    }

    @Override
    public long getWindowPublishedAtMs() {
      return 1L;
    }

    @Override
    public double getWindowSeconds() {
      return 5D;
    }

    @Override
    public void markSamplerActivity() {
    }

    @Override
    public double getEventTimeMsPerSecond() {
      return 0D;
    }

    @Override
    public int getListenerCount() {
      return 0;
    }

    @Override
    public double getCallsPerTick() {
      return 0D;
    }

    @Override
    public Map<String, Double> snapshotPluginEventTimeMS() {
      return new java.util.HashMap<>(time);
    }

    @Override
    public Map<String, Integer> snapshotPluginEventCalls() {
      return new java.util.HashMap<>(calls);
    }

    @Override
    public Map<String, String> ownerDisplayNames() {
      return names;
    }
  }

  private record FixedSampler(String id, double value) implements Sampler {
    @Override
    public String getId() {
      return id;
    }

    @Override
    public double sample() {
      return value;
    }

    @Override
    public String formattedValue(double t) {
      return Double.toString(t);
    }

    @Override
    public String formattedSuffix(double t) {
      return "";
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
  }

  private static final class ModdedPlatform implements ReactPlatform {
    @Override
    public PlatformKind kind() {
      return PlatformKind.MODDED;
    }

    @Override
    public String platformName() {
      return "NeoForge";
    }

    @Override
    public String minecraftVersion() {
      return "26.3";
    }

    @Override
    public File dataFolder() {
      return null;
    }

    @Override
    public PlatformScheduler scheduler() {
      return Mockito.mock(PlatformScheduler.class);
    }

    @Override
    public ExecutorService asyncExecutor() {
      return Mockito.mock(ExecutorService.class);
    }

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void verbose(String message) {
    }

    @Override
    public void reportError(String context, Throwable failure) {
    }
  }
}
