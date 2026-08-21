package art.arcane.react.content.PAPI;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderKeyRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactPlaceholderSourceTest {
  private static final String UNAVAILABLE = "---";

  private static final class FakeSamplers implements ReactPlaceholderSource.SamplerReader {
    private final Map<String, Double> values = new HashMap<>();
    private final Set<String> sampled = new LinkedHashSet<>();

    private FakeSamplers with(String id, double value) {
      values.put(id, value);
      return this;
    }

    @Override
    public Set<String> ids() {
      return values.keySet();
    }

    @Override
    public double sample(String samplerId) {
      sampled.add(samplerId);
      Double value = values.get(samplerId);
      return value == null ? Double.NaN : value;
    }
  }

  private static FakeSamplers everything() {
    return new FakeSamplers()
        .with("ticks-per-second", 20.0D)
        .with("tick-time", 12.3456D)
        .with("tick-ms-p95", 41.5D)
        .with("incident-score", 30.0D)
        .with("top-world-mspt", 7.125D)
        .with("entities", 12345.0D)
        .with("chunks", 8192.0D)
        .with("ground-items", 431.0D)
        .with("memory-used", 3.0D * 1048576.0D)
        .with("memory-free", 5.0D * 1048576.0D)
        .with("per-world-tick-time", 3.5D)
        .with("wormholes-portals", 4.0D);
  }

  private static String resolve(ReactPlaceholderSource source, String path) {
    return source.registry().resolve(null, path);
  }

  private static String resolve(ReactPlaceholderSource source, UUID playerId, String path) {
    return source.registry().resolve(playerId, path);
  }

  private static void warm(ReactPlaceholderSource source, FakeSamplers samplers, String... paths) {
    source.publish(samplers, Map.of());

    for (String path : paths) {
      resolve(source, path);
    }

    source.publish(samplers, Map.of());
  }

  @Test
  void shouldReturnUnavailableForEveryNamedKeyBeforeTheFirstSnapshotIsPublished() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();

    assertEquals(UNAVAILABLE, resolve(source, "tps"));
    assertEquals(UNAVAILABLE, resolve(source, "mspt"));
    assertEquals(UNAVAILABLE, resolve(source, "memory.used"));
    assertEquals("false", resolve(source, PlaceholderKeyRegistry.AVAILABLE));
  }

  @Test
  void shouldReportAvailableTrueOnlyOnceASnapshotHasBeenPublished() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    assertEquals("false", resolve(source, PlaceholderKeyRegistry.AVAILABLE));

    source.publish(everything(), Map.of());

    assertEquals("true", resolve(source, PlaceholderKeyRegistry.AVAILABLE));
  }

  @Test
  void shouldSampleOnlyTheSamplersThatAPlaceholderActuallyAsksFor() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything();

    source.publish(samplers, Map.of());
    assertTrue(samplers.sampled.isEmpty(), "no placeholder was requested yet, so nothing may be sampled");

    resolve(source, "tps");
    samplers.sampled.clear();
    source.publish(samplers, Map.of());

    assertEquals(Set.of("ticks-per-second"), samplers.sampled);
  }

  @Test
  void shouldSampleASamplerOnceEvenWhenSeveralKeysMapOntoIt() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything();

    source.publish(samplers, Map.of());
    resolve(source, "mspt");
    resolve(source, "sampler.tick-time");
    samplers.sampled.clear();
    source.publish(samplers, Map.of());

    assertEquals(Set.of("tick-time"), samplers.sampled);
  }

  @Test
  void shouldPublishTheTpsOfTheTicksPerSecondSampler() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything();
    warm(source, samplers, "tps");

    assertEquals("20.00", resolve(source, "tps"));
  }

  @Test
  void shouldPublishFractionalMetricsWithExactlyTwoDecimalsAndNoGroupingSeparator() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything().with("tick-time", 1234.5678D);
    warm(source, samplers, "mspt", "mspt-p95", "top-world-mspt");

    assertEquals("1234.57", resolve(source, "mspt"));
    assertEquals("41.50", resolve(source, "mspt-p95"));
    assertEquals("7.13", resolve(source, "top-world-mspt"));
  }

  @Test
  void shouldPublishCountMetricsAsExactIntegersWithNoGroupingSeparator() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything();
    warm(source, samplers, "entities", "chunks", "ground-items");

    assertEquals("12345", resolve(source, "entities"));
    assertEquals("8192", resolve(source, "chunks"));
    assertEquals("431", resolve(source, "ground-items"));
  }

  @Test
  void shouldPublishHealthAsTheInverseOfTheIncidentScoreClampedToZeroThroughOneHundred() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();

    FakeSamplers healthy = everything().with("incident-score", 0.0D);
    warm(source, healthy, "health");
    assertEquals("100.00", resolve(source, "health"));

    ReactPlaceholderSource stressed = new ReactPlaceholderSource();
    warm(stressed, everything().with("incident-score", 30.0D), "health");
    assertEquals("70.00", resolve(stressed, "health"));

    ReactPlaceholderSource overloaded = new ReactPlaceholderSource();
    warm(overloaded, everything().with("incident-score", 150.0D), "health");
    assertEquals("0.00", resolve(overloaded, "health"));
  }

  @Test
  void shouldPublishMemoryInWholeMegabytes() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything();
    warm(source, samplers, "memory.used", "memory.free");

    assertEquals("3", resolve(source, "memory.used"));
    assertEquals("5", resolve(source, "memory.free"));
  }

  @Test
  void shouldResolveAnyRegisteredSamplerThroughTheGenericPassthrough() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything();
    warm(source, samplers, "sampler.wormholes-portals", "sampler.tick-ms-p95");

    assertEquals("4", resolve(source, "sampler.wormholes-portals"));
    assertEquals("41.50", resolve(source, "sampler.tick-ms-p95"));
  }

  @Test
  void shouldReturnNullForAnUnknownSamplerIdSoPapiEmitsTheLiteralPlaceholder() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    source.publish(everything(), Map.of());

    assertNull(resolve(source, "sampler.definitely-not-a-sampler"));
  }

  @Test
  void shouldNeverAddAnUnknownSamplerIdToTheDemandSet() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything();
    source.publish(samplers, Map.of());

    for (int attempt = 0; attempt < 5000; attempt++) {
      resolve(source, "sampler.junk-" + attempt);
    }

    assertTrue(source.demandedSamplerIds().isEmpty(),
        "unknown sampler ids must never enter the demand set, and got: " + source.demandedSamplerIds());
  }

  @Test
  void shouldDropDemandForSamplersThatDisappearFromTheRegistry() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything();
    warm(source, samplers, "sampler.wormholes-portals");
    assertTrue(source.demandedSamplerIds().contains("wormholes-portals"));

    FakeSamplers withoutWormholes = new FakeSamplers().with("ticks-per-second", 20.0D);
    source.publish(withoutWormholes, Map.of());

    assertFalse(source.demandedSamplerIds().contains("wormholes-portals"));
    assertNull(resolve(source, "sampler.wormholes-portals"));
  }

  @Test
  void shouldReturnUnavailableWhenASamplerReportsANonFiniteValue() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything().with("tick-time", Double.NaN);
    warm(source, samplers, "mspt", "sampler.tick-time");

    assertEquals(UNAVAILABLE, resolve(source, "mspt"));
    assertEquals(UNAVAILABLE, resolve(source, "sampler.tick-time"));
  }

  @Test
  void shouldReturnUnavailableForWorldMsptWhenNoPlayerIsSupplied() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    source.publish(everything(), Map.of());

    assertEquals(UNAVAILABLE, resolve(source, ReactPlaceholderSource.WORLD_MSPT_KEY));
  }

  @Test
  void shouldReturnUnavailableForWorldMsptWhenThePlayerHasNoTrackedWorld() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    source.publish(everything(), Map.of());

    assertEquals(UNAVAILABLE, resolve(source, UUID.randomUUID(), ReactPlaceholderSource.WORLD_MSPT_KEY));
  }

  @Test
  void shouldReturnThePerWorldMsptOfTheWorldThePlayerIsIn() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    UUID player = UUID.randomUUID();
    UUID overworld = UUID.randomUUID();
    UUID nether = UUID.randomUUID();
    source.trackPlayerWorld(player, overworld);
    source.publish(everything(), Map.of(overworld, 18.5D, nether, 2.25D));

    assertEquals("18.50", resolve(source, player, ReactPlaceholderSource.WORLD_MSPT_KEY));

    source.trackPlayerWorld(player, nether);

    assertEquals("2.25", resolve(source, player, ReactPlaceholderSource.WORLD_MSPT_KEY));
  }

  @Test
  void shouldDemandThePerWorldSamplerAsSoonAsWorldMsptIsRequested() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything();
    source.publish(samplers, Map.of());
    resolve(source, UUID.randomUUID(), ReactPlaceholderSource.WORLD_MSPT_KEY);
    samplers.sampled.clear();
    source.publish(samplers, Map.of());

    assertEquals(Set.of("per-world-tick-time"), samplers.sampled);
  }

  @Test
  void shouldKeepServingTheLastKnownWorldForAGraceWindowAfterThePlayerQuits() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    UUID player = UUID.randomUUID();
    UUID overworld = UUID.randomUUID();
    source.trackPlayerWorld(player, overworld);
    source.publish(everything(), Map.of(overworld, 18.5D));
    source.releasePlayer(player);

    assertEquals("18.50", resolve(source, player, ReactPlaceholderSource.WORLD_MSPT_KEY));
  }

  @Test
  void shouldForgetEveryPlayerAndSnapshotOnClear() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    UUID player = UUID.randomUUID();
    UUID overworld = UUID.randomUUID();
    source.trackPlayerWorld(player, overworld);
    warm(source, everything(), "tps");

    source.clear();

    assertEquals("false", resolve(source, PlaceholderKeyRegistry.AVAILABLE));
    assertEquals(UNAVAILABLE, resolve(source, "tps"));
    assertEquals(UNAVAILABLE, resolve(source, player, ReactPlaceholderSource.WORLD_MSPT_KEY));
  }

  @Test
  void shouldNeverEmitAPercentSignOrAGroupingSeparatorFromAnyPublishedKey() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything()
        .with("entities", 1234567.0D)
        .with("tick-time", 1234.5678D)
        .with("memory-used", 4096.0D * 1048576.0D);
    UUID player = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    source.trackPlayerWorld(player, world);
    List<String> keys = source.registry().keys();

    source.publish(samplers, Map.of(world, 1234.5D));

    for (String key : keys) {
      resolve(source, player, key);
    }

    source.publish(samplers, Map.of(world, 1234.5D));

    for (String key : keys) {
      if (key.endsWith(".*")) {
        continue;
      }

      String value = resolve(source, player, key);
      assertFalse(value.contains("%"), key + " emitted a percent sign: " + value);
      assertFalse(value.contains(","), key + " emitted a grouping separator: " + value);
    }

    assertEquals("1234567", resolve(source, player, "entities"));
    assertEquals("1234.50", resolve(source, player, ReactPlaceholderSource.WORLD_MSPT_KEY));
    assertEquals("4096", resolve(source, player, "memory.used"));
  }

  @Test
  void shouldPublishExactlyTheDocumentedKeySurface() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();

    assertEquals(
        List.of(
            "available",
            "chunks",
            "entities",
            "ground-items",
            "health",
            "memory.free",
            "memory.used",
            "mspt",
            "mspt-p95",
            "sampler.*",
            "top-world-mspt",
            "tps",
            "world.mspt"
        ),
        source.registry().keys()
    );
  }

  @Test
  void shouldNotConfuseANamedKeyWithASamplerIdOfTheSameName() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = everything().with("entities", 7.0D);
    warm(source, samplers, "entities", "sampler.entities");

    assertEquals("7", resolve(source, "entities"));
    assertEquals("7", resolve(source, "sampler.entities"));
    assertNotEquals(resolve(source, "entities"), resolve(source, "sampler.wormholes-portals"));
  }
}
