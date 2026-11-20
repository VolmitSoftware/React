package art.arcane.react.api.rendering;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.core.integration.ThirdPartyMetricRegistry;
import art.arcane.react.api.metric.ReactModMetric;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.content.feature.FeatureAnimusCostSharePieMap;
import art.arcane.react.content.feature.FeatureAnimusNetworkListMap;
import art.arcane.react.content.feature.FeatureAdaptAbilityImpactListMap;
import art.arcane.react.content.feature.FeatureIrisWorldChunkSharePieMap;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.platform.bukkit.BukkitIntegrationMetricsHost;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pins the bytes Bukkit draws for the integration dashboards, so moving them behind a platform host cannot change
 * what Bukkit servers show.
 */
public class IntegrationDashboardsGoldenTest {
  private static final long SAMPLED_AT = Long.MAX_VALUE / 4L;
  private static final Map<String, String> GOLDEN = ordered(
      "iris-metrics", "61919765285f4be8",
      "iris-pregen-health", "257420b68fba9d8b",
      "iris-world-overview", "f2782c26fb98c2b5",
      "iris-world-overview-wall", "a8655fdd2f55628a",
      "iris-world-pregen", "e88278075b328bc8",
      "wormholes-metrics", "6a0fc2acdeb62674",
      "adapt-metrics", "2a4e8c6d2628a7ae",
      "gloss-metrics", "26f753e31388ee8d",
      "iris-metrics-offline", "f14eb0b1069e9d23",
      "animus-cost-share-pie-map", "c2743796d888328e",
      "animus-network-list-map", "bdda241553a80fd4",
      "animus-network-list-map-wall", "a4b3edc7110b66ca",
      "animus-network-list-map-undeclared", "d2866a5a67abf1c0",
      "adapt-ability-impact-list-map", "32e402d945db665f",
      "adapt-ability-impact-list-map-wall", "c35b22ebd479c26d",
      "adapt-ability-impact-list-map-guard-only", "2398c29681a4d6a0",
      "iris-world-chunk-share-pie-map", "880438e2a217a209"
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
  void bukkitIntegrationDashboardsDrawPinnedBytes() throws Exception {
    UUID worldId = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    UUID viewerId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getName()).thenReturn("iris-world");
    Mockito.when(world.getKey()).thenReturn(org.bukkit.NamespacedKey.minecraft("overworld"));
    Player viewer = Mockito.mock(Player.class);
    Mockito.when(viewer.getWorld()).thenReturn(world);
    Mockito.when(viewer.isOnline()).thenReturn(true);

    RemoteSamplerBridge bridge = Mockito.mock(RemoteSamplerBridge.class);
    Mockito.when(bridge.getSample(Mockito.anyString(), Mockito.anyString())).thenAnswer(invocation -> {
      String key = invocation.getArgument(1);
      if (Math.floorMod(key.hashCode(), 5) == 0) {
        return IntegrationMetricSample.unavailable(IntegrationMetricSchema.descriptor(key), "missing", SAMPLED_AT);
      }
      return IntegrationMetricSample.available(IntegrationMetricSchema.descriptor(key), value(key), SAMPLED_AT);
    });
    IntegrationController controller = Mockito.mock(IntegrationController.class);
    Mockito.when(controller.getRemoteSamplerBridge()).thenReturn(bridge);
    Mockito.when(controller.statusFor("iris")).thenReturn(new IntegrationController.IntegrationStatus(
        "iris", IntegrationController.Health.HEALTHY, "v1", 0L, "engines warm"));
    Mockito.when(controller.statusFor("wormholes")).thenReturn(new IntegrationController.IntegrationStatus(
        "wormholes", IntegrationController.Health.DEGRADED, "v1", 0L, ""));

    MapRenderTarget held = new MapRenderTarget(7, worldId, viewerId);
    Map<String, String> actual = new LinkedHashMap<>();
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getWorld(Mockito.any(UUID.class))).thenReturn(world);
      bukkit.when(() -> Bukkit.getPlayer(Mockito.any(UUID.class))).thenReturn(viewer);
      react.when(() -> React.controller(IntegrationController.class)).thenReturn(controller);
      String scope = WorldIdentity.serialize(world);
      Mockito.when(bridge.getGroup("iris", "world", scope)).thenReturn(group(scope));

      List<RendererIrisMetrics> iris = RendererIrisMetrics.dashboards(BukkitIntegrationMetricsHost.INSTANCE);
      actual.put("iris-metrics", hash(iris.get(0), held, null));
      actual.put("iris-pregen-health", hash(byId(iris, "iris-pregen-health"), held, null));
      List<RendererIrisWorldMetrics> worlds = RendererIrisWorldMetrics.dashboards(BukkitIntegrationMetricsHost.INSTANCE);
      actual.put("iris-world-overview", hash(byId(worlds, "iris-world-overview"), held, null));
      actual.put("iris-world-overview-wall", hash(byId(worlds, "iris-world-overview"), held, new MegamapGrid.MegamapTile(2, 2, 1, 1)));
      Mockito.when(bridge.getGroup("iris", "world", scope)).thenReturn(null);
      actual.put("iris-world-pregen", hash(byId(worlds, "iris-world-pregen"), held, null));
      actual.put("wormholes-metrics", hash(new RendererWormholesMetrics(BukkitIntegrationMetricsHost.INSTANCE), held, null));
      actual.put("adapt-metrics", hash(new RendererAdaptMetrics(BukkitIntegrationMetricsHost.INSTANCE, ReactConfiguration::adaptAbilityOpsMetricKey, ReactConfiguration::adaptAbilityOpsMetricLabel), held, null));
      actual.put("gloss-metrics", hash(new RendererGlossMetrics(BukkitIntegrationMetricsHost.INSTANCE), held, null));
      react.when(() -> React.controller(IntegrationController.class)).thenReturn(null);
      actual.put("iris-metrics-offline", hash(iris.get(0), held, null));
      react.when(() -> React.controller(IntegrationController.class)).thenReturn(controller);

      Mockito.when(bridge.snapshot("adapt")).thenReturn(adaptSamples(true));
      actual.put("adapt-ability-impact-list-map", hash(new FeatureAdaptAbilityImpactListMap(), held, null));
      actual.put("adapt-ability-impact-list-map-wall", hash(new FeatureAdaptAbilityImpactListMap(), held, new MegamapGrid.MegamapTile(4, 2, 1, 0)));
      Mockito.when(bridge.snapshot("adapt")).thenReturn(adaptSamples(false));
      actual.put("adapt-ability-impact-list-map-guard-only", hash(new FeatureAdaptAbilityImpactListMap(), held, null));
      Mockito.when(bridge.groups("iris", "world")).thenReturn(List.of(group("minecraft:overworld"),
          new IntegrationMetricGroup("world", "minecraft:the_nether", "iris_nether", Map.of(), Map.of(
              IntegrationMetricSchema.IRIS_LOADED_CHUNKS, IntegrationMetricSample.available(
                  IntegrationMetricSchema.descriptor(IntegrationMetricSchema.IRIS_LOADED_CHUNKS), 57D, SAMPLED_AT)))));
      actual.put("iris-world-chunk-share-pie-map", hash(new FeatureIrisWorldChunkSharePieMap(), held, null));

      react.when(() -> React.controller(IntegrationController.class)).thenReturn(controller);
      ThirdPartyMetricRegistry metrics = Mockito.mock(ThirdPartyMetricRegistry.class);
      Mockito.when(controller.getThirdPartyMetrics()).thenReturn(metrics);
      Mockito.when(metrics.store()).thenReturn(animusStore());
      resetAnimusCache();
      resetAnimusCache();
      actual.put("animus-cost-share-pie-map", hash(new FeatureAnimusCostSharePieMap(), held, null));
      actual.put("animus-network-list-map", hash(new FeatureAnimusNetworkListMap(), held, null));
      actual.put("animus-network-list-map-wall", hash(new FeatureAnimusNetworkListMap(), held, new MegamapGrid.MegamapTile(3, 2, 0, 0)));
      Mockito.when(metrics.store()).thenReturn(new PublishedMetricStore());
      resetAnimusCache();
      actual.put("animus-network-list-map-undeclared", hash(new FeatureAnimusNetworkListMap(), held, null));
      resetAnimusCache();
    }

    Assertions.assertEquals(GOLDEN, actual);
  }

  public static PublishedMetricStore animusStore() {
    long now = System.currentTimeMillis();
    Map<String, Double> values = new LinkedHashMap<>();
    values.put("animus.cost.presentation_ms", 1.5D);
    values.put("animus.cost.navigation_ms", 4.25D);
    values.put("animus.cost.combat_ms", 2.75D);
    values.put("animus.cost.total_ms", 99D);
    values.put("animus.net.bytes_per_second", 2048D);
    values.put("animus.net.packets_per_second", 120D);
    values.put("animus.net.player_peak.bytes_per_second", 900D);
    values.put("animus.net.kind.move.bytes_per_second", 1200D);
    values.put("animus.net.kind.metadata.bytes_per_second", 800D);
    values.put("animus.net.kind.spawn.bytes_per_second", 300D);
    values.put("animus.pressure.navigation", 40D);
    values.put("animus.pressure.combat", 70D);
    PublishedMetricStore store = new PublishedMetricStore();
    List<ReactModMetric> declared = new java.util.ArrayList<>();
    for (String key : values.keySet()) {
      declared.add(ReactModMetric.gauge(key, key, ""));
    }
    for (ReactModMetric metric : store.declare("animus", declared)) {
      store.publish("animus", metric.key(), values.get(metric.key()), now, now);
    }
    return store;
  }

  public static void resetAnimusCache() throws ReflectiveOperationException {
    Class<?> series = Class.forName("art.arcane.react.content.feature.AnimusMetricSeries");
    java.lang.reflect.Field cached = series.getDeclaredField("cached");
    cached.setAccessible(true);
    java.lang.reflect.Constructor<?> constructor = cached.getType().getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    java.lang.reflect.Method empty = Class.forName("art.arcane.react.content.feature.AnimusMetrics$Snapshot").getDeclaredMethod("empty");
    empty.setAccessible(true);
    cached.set(null, constructor.newInstance(empty.invoke(null), 0L));
  }

  private static Map<String, IntegrationMetricSample> adaptSamples(boolean detailed) {
    Map<String, IntegrationMetricSample> samples = new LinkedHashMap<>();
    samples.put(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS, IntegrationMetricSample.available(
        IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS), 420D, SAMPLED_AT));
    if (!detailed) {
      return samples;
    }
    double[][] abilities = {{12, 3.5, 40, 0.25}, {80, 1.25, 90, 0.5}, {4, 0.75, 4, 0.01}};
    String[] ids = {"pickaxe-veinminer", "agility_wall_jump", "tamingHealthBoost"};
    for (int i = 0; i < ids.length; i++) {
      for (String key : IntegrationMetricSchema.adaptAbilityDetailKeys(ids[i])) {
        double value = switch (IntegrationMetricSchema.adaptAbilitySignal(key)) {
          case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_OPS -> abilities[i][0];
          case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS -> abilities[i][1];
          case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_GUARD_CHECKS -> abilities[i][2];
          default -> abilities[i][3];
        };
        samples.put(key, IntegrationMetricSample.available(IntegrationMetricSchema.descriptor(key), value, SAMPLED_AT));
      }
    }
    return samples;
  }

  private static IntegrationMetricGroup group(String scope) {
    Map<String, IntegrationMetricSample> samples = new LinkedHashMap<>();
    for (String key : List.of(IntegrationMetricSchema.IRIS_ENGINE_ACTIVE, IntegrationMetricSchema.IRIS_LOADED_CHUNKS,
        IntegrationMetricSchema.IRIS_ENTITY_SATURATION, IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND,
        IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL)) {
      samples.put(key, IntegrationMetricSample.available(IntegrationMetricSchema.descriptor(key), value(key), SAMPLED_AT));
    }
    return new IntegrationMetricGroup("world", scope, "Iris Overworld", Map.of(), samples);
  }

  private static double value(String key) {
    return switch (IntegrationMetricSchema.descriptor(key).type()) {
      case INTEGER, LONG -> Math.floorMod(key.hashCode(), 900) + 3;
      default -> (Math.floorMod(key.hashCode(), 400) / 7D) + 0.25D;
    };
  }

  private static <T extends ReactRenderer> T byId(List<T> renderers, String id) {
    for (T renderer : renderers) {
      if (renderer.getId().equals(id)) {
        return renderer;
      }
    }
    throw new IllegalArgumentException(id);
  }

  private static Map<String, String> ordered(String... pairs) {
    Map<String, String> golden = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      golden.put(pairs[i], pairs[i + 1]);
    }
    return golden;
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
