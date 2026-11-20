package art.arcane.react.content.feature;

import art.arcane.react.React;
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
import art.arcane.react.api.rendering.IntegrationDashboardsGoldenTest;
import art.arcane.react.core.controller.EventController;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.core.integration.ThirdPartyMetricRegistry;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.controller.NearbyPlayerIndexController.PlayerViewSnapshot;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.model.SampledServer;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pins the bytes Bukkit draws for React's own dashboards and feature maps, so moving them behind platform hosts
 * cannot change what Bukkit servers show.
 */
class DashboardMapsGoldenTest {
  private static final String WORLD_KEY = "minecraft:overworld";
  private static final Map<String, String> GOLDEN = ordered(
      "react-metrics", "ec31c42062c791ee",
      "react-metrics-wall", "aeb3e5421c55396a",
      "chunk-sampler-map", "30b0af5f0c870077",
      "chunk-load-gen-cost-map", "e3e2f481cddb3e45",
      "entity-pressure-heatmap", "745d6d4d957d6fa9",
      "redstone-activity-heatmap", "06ab588300a4b465",
      "hopper-container-throughput-map", "442666ed320ec021",
      "player-impact-overlay", "1fee47c0b47f1f3d",
      "player-impact-overlay-framed", "b7f570131f0c4ae9",
      "heatmap-no-world", "f77a609ced4647ae",
      "plugin-event-impact-list-map", "cbe4d63824bb9975",
      "plugin-event-impact-list-map-wall", "cac99e5722bbe340",
      "plugin-event-impact-list-map-empty", "60e398b6dde34ab0",
      "plugin-event-impact-pie-map", "43a5f7b6bec8dfa3",
      "plugin-event-impact-pie-map-wall", "ab4af14f3df8b033",
      "tick-spike-origin-replay-map", "0f60933dcf71e485",
      "tick-spike-origin-replay-map-wall", "536f40c7b91bbcbc",
      "animus-runtime-pressure-overlay", "9c32007fb7620a3c",
      "adapt-runtime-pressure-overlay", "d45a6fdcc6c8d4fc",
      "iris-generation-pressure-overlay", "6d462d65f9a79675",
      "iris-generation-pressure-overlay-unmanaged", "4447730387f75218"
  );

  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void bukkitDashboardsDrawPinnedBytes() throws Exception {
    Map<String, String> actual = new LinkedHashMap<>();
    UUID worldId = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    UUID viewerId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    World world = Mockito.mock(World.class);
    Player viewer = Mockito.mock(Player.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getMaxHeight()).thenReturn(320);
    Mockito.when(world.getSpawnLocation()).thenReturn(new Location(world, 40D, 64D, -24D));
    Mockito.when(viewer.getWorld()).thenReturn(world);
    Mockito.when(viewer.getLocation()).thenReturn(new Location(world, 20D, 64D, 36D));
    Mockito.when(viewer.getUniqueId()).thenReturn(viewerId);
    Mockito.when(viewer.isOnline()).thenReturn(true);

    MapController mapController = Mockito.mock(MapController.class);
    Mockito.when(mapController.resolveMapAnchor(Mockito.any(MapRenderTarget.class)))
        .thenAnswer(invocation -> new Location(world, 20D, 64D, 36D));
    ObserverController observer = observer(worldId);
    NearbyPlayerIndexController players = Mockito.mock(NearbyPlayerIndexController.class);
    Mockito.when(players.playerSnapshotsInColumn(Mockito.eq(worldId), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
        .thenReturn(List.of(
            new PlayerViewSnapshot(viewerId, "alice", worldId, 20D, 64D, 36D, 0.2D, false, false),
            new PlayerViewSnapshot(UUID.fromString("00000000-0000-0000-0000-0000000000bb"), "bob", worldId, -30D, 70D, 10D, 1.4D, true, false),
            new PlayerViewSnapshot(UUID.fromString("00000000-0000-0000-0000-0000000000cc"), "carol", worldId, 70D, 64D, 60D, 0D, false, true)
        ));
    EventController events = Mockito.mock(EventController.class);
    Mockito.when(events.isMeasuring()).thenReturn(true);
    Mockito.when(events.getWindowSeconds()).thenReturn(5D);
    Mockito.when(events.snapshotPluginEventTimeMS()).thenAnswer(invocation -> new java.util.HashMap<>(Map.of(
        "WorldEdit", 42.5D, "essentials_x", 12D, "Iris", 3.25D, "tiny-plugin", 0.5D)));
    Mockito.when(events.snapshotPluginEventCalls()).thenAnswer(invocation -> new java.util.HashMap<>(Map.of(
        "WorldEdit", 1200, "essentials_x", 300, "Iris", 75, "tiny-plugin", 9)));

    MapRenderTarget held = new MapRenderTarget(7, worldId, viewerId);
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getPlayer(Mockito.any(UUID.class))).thenReturn(viewer);
      bukkit.when(() -> Bukkit.getWorld(Mockito.any(UUID.class))).thenReturn(world);
      react.when(() -> React.controller(MapController.class)).thenReturn(mapController);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(players);
      react.when(() -> React.controller(EventController.class)).thenReturn(events);
      react.when(() -> React.sampler(Mockito.anyString())).thenAnswer(DashboardMapsGoldenTest::sampler);

      RendererReactMetrics metrics = new RendererReactMetrics(React::sampler);
      actual.put("react-metrics", hash(metrics, held, null));
      actual.put("react-metrics-wall", hash(metrics, held, new MegamapGrid.MegamapTile(3, 2, 1, 0)));
      actual.put("chunk-sampler-map", hash(new FeatureChunkSamplerMap(), held, null));
      actual.put("chunk-load-gen-cost-map", hash(new FeatureChunkLoadGenCostMap(), held, null));
      actual.put("entity-pressure-heatmap", hash(new FeatureEntityPressureHeatmap(), held, null));
      actual.put("redstone-activity-heatmap", hash(new FeatureRedstoneActivityHeatmap(), held, null));
      actual.put("hopper-container-throughput-map", hash(new FeatureHopperContainerThroughputMap(), held, null));
      actual.put("player-impact-overlay", hash(new FeaturePlayerImpactOverlay(), held, null));

      resetImpactSeries();
      actual.put("plugin-event-impact-pie-map", hash(new FeaturePluginEventImpactPieMap(), held, null));
      resetImpactSeries();
      actual.put("plugin-event-impact-pie-map-wall",
          hash(new FeaturePluginEventImpactPieMap(), held, new MegamapGrid.MegamapTile(2, 2, 1, 0)));
      resetImpactSeries();
      IntegrationController integration = Mockito.mock(IntegrationController.class);
      ThirdPartyMetricRegistry thirdParty = Mockito.mock(ThirdPartyMetricRegistry.class);
      Mockito.when(integration.getThirdPartyMetrics()).thenReturn(thirdParty);
      Mockito.when(thirdParty.store()).thenReturn(IntegrationDashboardsGoldenTest.animusStore());
      react.when(() -> React.controller(IntegrationController.class)).thenReturn(integration);
      IntegrationDashboardsGoldenTest.resetAnimusCache();
      actual.put("animus-runtime-pressure-overlay", hash(new FeatureAnimusRuntimePressureOverlay(), held, null));
      IntegrationDashboardsGoldenTest.resetAnimusCache();
      RemoteSamplerBridge bridge = Mockito.mock(RemoteSamplerBridge.class);
      Mockito.when(integration.getRemoteSamplerBridge()).thenReturn(bridge);
      Mockito.when(bridge.valueOr("adapt", IntegrationMetricSchema.ADAPT_SESSION_LOAD, 0D)).thenReturn(37.5D);
      Mockito.when(bridge.valueOr("adapt", IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET, 0D)).thenReturn(12D);
      Mockito.when(bridge.getSample("adapt", IntegrationMetricSchema.ADAPT_SESSION_LOAD)).thenReturn(IntegrationMetricSample.available(
          IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_SESSION_LOAD), 37.5D, 0L));
      Mockito.when(bridge.getSample("adapt", IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET)).thenReturn(IntegrationMetricSample.available(
          IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET), 12D, 0L));
      actual.put("adapt-runtime-pressure-overlay", hash(new FeatureAdaptRuntimePressureOverlay(), held, null));
      Mockito.when(bridge.getGroup("iris", "world", WORLD_KEY)).thenReturn(
          new IntegrationMetricGroup("world", WORLD_KEY, "Overworld", Map.of(), Map.of()));
      actual.put("iris-generation-pressure-overlay", hash(new FeatureIrisGenerationPressureOverlay(), held, null));
      Mockito.when(bridge.getGroup("iris", "world", WORLD_KEY)).thenReturn(null);
      actual.put("iris-generation-pressure-overlay-unmanaged", hash(new FeatureIrisGenerationPressureOverlay(), held, null));
      FeatureTickSpikeOriginReplayMap replay = seededReplay(worldId);
      actual.put("tick-spike-origin-replay-map", hash(replay, held, null));
      actual.put("tick-spike-origin-replay-map-wall", hash(replay, held, new MegamapGrid.MegamapTile(2, 2, 0, 1)));

      Mockito.when(mapController.hasFrameAnchor(Mockito.any(MapRenderTarget.class))).thenReturn(true);
      Mockito.when(mapController.resolveMapAnchor(Mockito.any(MapRenderTarget.class)))
          .thenAnswer(invocation -> new Location(world, -40D, 64D, 8D));
      actual.put("player-impact-overlay-framed", hash(new FeaturePlayerImpactOverlay(), held, null));

      bukkit.when(() -> Bukkit.getWorld(Mockito.any(UUID.class))).thenReturn(null);
      bukkit.when(() -> Bukkit.getPlayer(Mockito.any(UUID.class))).thenReturn(null);
      Mockito.when(mapController.resolveMapAnchor(Mockito.any(MapRenderTarget.class))).thenReturn(null);
      actual.put("heatmap-no-world", hash(new FeatureEntityPressureHeatmap(), held, null));

      resetImpactSeries();
      actual.put("plugin-event-impact-list-map", hash(new FeaturePluginEventImpactListMap(), held, null));
      resetImpactSeries();
      actual.put("plugin-event-impact-list-map-wall",
          hash(new FeaturePluginEventImpactListMap(), held, new MegamapGrid.MegamapTile(4, 2, 0, 0)));
      resetImpactSeries();
      Mockito.when(events.snapshotPluginEventTimeMS()).thenAnswer(invocation -> new java.util.HashMap<>());
      Mockito.when(events.snapshotPluginEventCalls()).thenAnswer(invocation -> new java.util.HashMap<>());
      actual.put("plugin-event-impact-list-map-empty", hash(new FeaturePluginEventImpactListMap(), held, null));
      resetImpactSeries();
    }

    Assertions.assertEquals(GOLDEN, actual);
  }

  private static ObserverController observer(UUID worldId) {
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.getSampled()).thenReturn(Mockito.mock(SampledServer.class));
    Mockito.when(observer.heatmapWorld(worldId)).thenReturn(Optional.of(
        new HeatmapWorldRef(worldId, WORLD_KEY, "world", 0, 0, 0D, 0D, 60_000_000D)));
    List<LoadedChunkCoordinate> loaded = List.of(
        new LoadedChunkCoordinate(0, 0), new LoadedChunkCoordinate(1, 2), new LoadedChunkCoordinate(-2, 3),
        new LoadedChunkCoordinate(4, -1), new LoadedChunkCoordinate(-3, -3), new LoadedChunkCoordinate(2, 4),
        new LoadedChunkCoordinate(-1, 1), new LoadedChunkCoordinate(3, 3));
    Mockito.when(observer.loadedChunkCoordinatesInBounds(Mockito.eq(worldId), Mockito.anyInt(), Mockito.anyInt(),
        Mockito.anyInt(), Mockito.anyInt())).thenReturn(loaded);
    Mockito.when(observer.sample(Mockito.eq(WORLD_KEY), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Sampler.class)))
        .thenAnswer(DashboardMapsGoldenTest::chunkSample);
    Mockito.when(observer.sampledChunk(Mockito.eq(WORLD_KEY), Mockito.anyInt(), Mockito.anyInt()))
        .thenAnswer(invocation -> {
          int x = invocation.getArgument(1);
          int z = invocation.getArgument(2);
          SampledChunk chunk = new SampledChunk(worldId, WORLD_KEY, x, z);
          chunk.get("redstone").set(Math.abs(x) * 1.5D);
          chunk.get("entities").set(Math.abs(z) + 0.25D);
          return Optional.of(chunk);
        });
    return observer;
  }

  private static Optional<Double> chunkSample(InvocationOnMock invocation) {
    int x = invocation.getArgument(1);
    int z = invocation.getArgument(2);
    Sampler sampler = invocation.getArgument(3);
    int salt = Math.floorMod(sampler.getId().hashCode(), 7) + 1;
    if (Math.floorMod(x + z + salt, 5) == 0) {
      return Optional.empty();
    }
    return Optional.of(((Math.abs(x) * 3D) + (Math.abs(z) * 2D) + 1D) * salt * 0.75D);
  }

  private static Sampler sampler(InvocationOnMock invocation) {
    String id = invocation.getArgument(0);
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

  private static FeatureTickSpikeOriginReplayMap seededReplay(UUID worldId) throws ReflectiveOperationException {
    FeatureTickSpikeOriginReplayMap replay = new FeatureTickSpikeOriginReplayMap();
    replay.onActivate();
    Field field = FeatureTickSpikeOriginReplayMap.class.getDeclaredField("replay");
    field.setAccessible(true);
    TickSpikeReplay heat = (TickSpikeReplay) field.get(replay);
    int[][] cells = {{0, 0, 40}, {1, 2, 22}, {-2, 3, 9}, {4, -1, 31}, {-3, -3, 4}, {2, 4, 15}, {-1, 1, 27}, {3, 3, 2}};
    for (int[] cell : cells) {
      heat.addHeat(worldId, cell[0], cell[1], cell[2], 1_000L);
    }
    return replay;
  }

  private static void resetImpactSeries() throws ReflectiveOperationException {
    Class<?> series = Class.forName("art.arcane.react.content.feature.PluginEventImpactSeries");
    for (String name : List.of("rollingScore", "lastSeenMS")) {
      Field field = series.getDeclaredField(name);
      field.setAccessible(true);
      ((Map<?, ?>) field.get(null)).clear();
    }
    Field last = series.getDeclaredField("lastUpdateMS");
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

  private static String hash(ReactRenderer renderer, MapRenderTarget target, MegamapGrid.MegamapTile tile) throws Exception {
    MapRendererRegistry registry = new MapRendererRegistry();
    registry.register(renderer);
    MapComposer composer = new MapComposer(target.mapId(), target.worldId(), renderer);
    composer.compose(registry, target, tile, null, 0L, 32, 1_000L);
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(composer.published());
    return HexFormat.of().formatHex(digest).substring(0, 16);
  }
}
