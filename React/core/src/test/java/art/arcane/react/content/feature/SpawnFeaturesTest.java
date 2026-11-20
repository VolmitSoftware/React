package art.arcane.react.content.feature;

import art.arcane.react.content.tweak.TweakEntityHardstop;
import art.arcane.react.content.tweak.TweakSpawnerPlayerRadius;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.WorldHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

class SpawnFeaturesTest {
  private static final UUID WORLD = UUID.randomUUID();
  private final FakeSpawnHost spawns = new FakeSpawnHost();
  private final WorldHost worlds = Mockito.mock(WorldHost.class);

  @BeforeEach
  void bind() {
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    Mockito.when(platform.spawnHost()).thenReturn(Optional.of(spawns));
    Mockito.when(platform.worldHost()).thenReturn(Optional.of(worlds));
    ReactPlatforms.unbind();
    ReactPlatforms.bind(platform);
  }

  @AfterEach
  void unbind() {
    ReactPlatforms.unbind();
  }

  @Test
  void reasonHelpersMatchBukkitGroups() {
    Assertions.assertTrue(SpawnHost.isSpawnerReason("SPAWNER"));
    Assertions.assertTrue(SpawnHost.isSpawnerReason("TRIAL_SPAWNER"));
    Assertions.assertFalse(SpawnHost.isSpawnerReason("NATURAL"));
    Assertions.assertTrue(SpawnHost.isNaturalReason("RAID"));
    Assertions.assertTrue(SpawnHost.isNaturalReason("PATROL"));
    Assertions.assertFalse(SpawnHost.isNaturalReason("BREEDING"));
    Assertions.assertFalse(SpawnHost.isNaturalReason(null));
  }

  @Test
  void burstLimiterCancelsSpawnerSpawnsPastTheChunkCapAndBacksOffOnce() {
    FeatureSpawnBurstLimiter limiter = new FeatureSpawnBurstLimiter();
    limiter.onActivate();
    try {
      GameLocation at = new GameLocation(WORLD, 8, 64, 8);
      int cancelled = 0;
      for (int index = 0; index < 20; index++) {
        if (!spawns.creatures.dispatch(FakeSpawnHost.spawn(at, "SPAWNER", true))) {
          cancelled++;
        }
      }
      Assertions.assertEquals(10, cancelled);
      Assertions.assertEquals(1, spawns.backoffs.size());
    } finally {
      limiter.onDeactivate();
    }
    Assertions.assertTrue(spawns.creatures.isEmpty());
  }

  @Test
  void burstLimiterKeepsChunksApartAndSkipsNamedOrUnmanagedSpawns() {
    FeatureSpawnBurstLimiter limiter = new FeatureSpawnBurstLimiter();
    limiter.onActivate();
    try {
      for (int index = 0; index < 15; index++) {
        Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(new GameLocation(WORLD, 8, 64, 8), "NATURAL", true)));
        Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(new GameLocation(WORLD, 40, 64, 8), "NATURAL", true)));
      }
      Assertions.assertFalse(spawns.creatures.dispatch(FakeSpawnHost.spawn(new GameLocation(WORLD, 8, 64, 8), "NATURAL", true)));
      Assertions.assertTrue(spawns.creatures.dispatch(
          new FakeSpawnHost.TestSpawn(new GameLocation(WORLD, 8, 64, 8), "NATURAL", true, true)));
      for (int index = 0; index < 40; index++) {
        Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(new GameLocation(WORLD, 72, 64, 8), "BREEDING", false)));
      }
    } finally {
      limiter.onDeactivate();
    }
  }

  @Test
  void spawnerRadiusCancelsSpawnerMonstersWithoutNearbyPlayers() {
    TweakSpawnerPlayerRadius radius = new TweakSpawnerPlayerRadius();
    GameLocation near = new GameLocation(WORLD, 0, 64, 0);
    GameLocation far = new GameLocation(WORLD, 500, 64, 0);
    Mockito.when(worlds.anyPlayerNear(near, 32D)).thenReturn(true);
    radius.onActivate();
    try {
      Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(near, "SPAWNER", true)));
      Assertions.assertFalse(spawns.creatures.dispatch(FakeSpawnHost.spawn(far, "TRIAL_SPAWNER", true)));
      Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(far, "SPAWNER", false)));
      Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(far, "NATURAL", true)));
    } finally {
      radius.onDeactivate();
    }
  }

  @Test
  void hardstopRejectsCrowdedChunksAndCachesTheCount() {
    TweakEntityHardstop hardstop = new TweakEntityHardstop();
    spawns.chunkCounts.put(FakeSpawnHost.key(0, 0), 100);
    spawns.chunkCounts.put(FakeSpawnHost.key(1, 0), 3);
    hardstop.onActivate();
    try {
      Assertions.assertFalse(spawns.creatures.dispatch(FakeSpawnHost.spawn(new GameLocation(WORLD, 1, 64, 1), "NATURAL", true)));
      Assertions.assertFalse(spawns.creatures.dispatch(FakeSpawnHost.spawn(new GameLocation(WORLD, 2, 64, 2), "NATURAL", true)));
      Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(new GameLocation(WORLD, 17, 64, 1), "NATURAL", true)));
      Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(new GameLocation(WORLD, 18, 64, 1), "NATURAL", true)));
      Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(new GameLocation(WORLD, 500, 64, 1), "NATURAL", true)));
      Assertions.assertEquals(3, spawns.counts);
    } finally {
      hardstop.onDeactivate();
    }
    Assertions.assertTrue(spawns.creatures.isEmpty());
    Assertions.assertTrue(spawns.entities.isEmpty());
  }

  @Test
  void lightCacheCountsSkippedSpawnerAttemptsAndCancelsOnlyWhenEngaged() throws Exception {
    FeatureSpawnerLightCache cache = new FeatureSpawnerLightCache();
    cache.onActivate();
    try {
      GameLocation bright = new GameLocation(WORLD, 8, 64, 8);
      Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(bright, "SPAWNER", true)));
      Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(bright, "SPAWNER", true)));
      Assertions.assertEquals(2, cache.readAndResetEvaluated());
      Assertions.assertEquals(2, cache.readAndResetSkipped());
      Assertions.assertEquals(1, spawns.scans);

      Field engaged = FeatureSpawnerLightCache.class.getDeclaredField("engaged");
      engaged.setAccessible(true);
      engaged.setBoolean(cache, true);
      Assertions.assertFalse(spawns.creatures.dispatch(FakeSpawnHost.spawn(bright, "SPAWNER", true)));

      spawns.darkCells.add(FakeSpawnHost.cell(10, 64, 9));
      cache.invalidate(new GameLocation(WORLD, 3, 70, 3));
      Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(bright, "SPAWNER", true)));
      Assertions.assertEquals(2, spawns.scans);
      Assertions.assertTrue(spawns.creatures.dispatch(FakeSpawnHost.spawn(bright, "NATURAL", true)));
    } finally {
      cache.onDeactivate();
    }
  }
}
