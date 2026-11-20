package art.arcane.react.content.feature;

import art.arcane.react.api.feature.PressureGate;
import art.arcane.react.platform.ExplosionHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

class ExplosionPacketClusteringTest {
  @Test
  void spatialIndexMatchesTheOriginalHeadRadiusClustering() {
    Random random = new Random(42L);
    List<FeatureExplosionPacketBatching.PendingExplosion> explosions = new ArrayList<>();
    Map<FeatureExplosionPacketBatching.PendingExplosion, Point> points = new IdentityHashMap<>();
    for (int i = 0; i < 512; i++) {
      Point point = new Point(
          random.nextDouble(-400D, 400D),
          random.nextDouble(-64D, 320D),
          random.nextDouble(-400D, 400D)
      );
      FeatureExplosionPacketBatching.PendingExplosion explosion = explosion(point);
      explosions.add(explosion);
      points.put(explosion, point);
    }

    List<List<FeatureExplosionPacketBatching.PendingExplosion>> expected = bruteForce(explosions, points, 12D);
    List<List<FeatureExplosionPacketBatching.PendingExplosion>> actual =
        FeatureExplosionPacketBatching.clusterExplosions(explosions, 12D);

    Assertions.assertEquals(asIdentitySets(expected), asIdentitySets(actual));
  }

  @Test
  void clusteringRemainsHeadCenteredRatherThanTransitive() {
    FeatureExplosionPacketBatching.PendingExplosion first = explosion(new Point(0D, 64D, 0D));
    FeatureExplosionPacketBatching.PendingExplosion second = explosion(new Point(10D, 64D, 0D));
    FeatureExplosionPacketBatching.PendingExplosion third = explosion(new Point(20D, 64D, 0D));

    List<List<FeatureExplosionPacketBatching.PendingExplosion>> clusters =
        FeatureExplosionPacketBatching.clusterExplosions(List.of(first, second, third), 12D);

    Assertions.assertEquals(2, clusters.size());
    Assertions.assertEquals(identitySet(List.of(first, second)), identitySet(clusters.get(0)));
    Assertions.assertEquals(identitySet(List.of(third)), identitySet(clusters.get(1)));
  }

  @Test
  void bufferDrainsOnlyExplosionsWhoseVanillaPacketsWereSuppressed() {
    FeatureExplosionPacketBatching.PendingWorldBuffer buffer =
        new FeatureExplosionPacketBatching.PendingWorldBuffer(null);
    FeatureExplosionPacketBatching.PendingExplosion suppressed = explosion(new Point(10.75D, 64.5D, -2.25D));
    FeatureExplosionPacketBatching.PendingExplosion vanilla = explosion(new Point(30.25D, 70D, 40.75D));

    Assertions.assertEquals(FeatureExplosionPacketBatching.AddResult.ACCEPTED, buffer.tryAdd(suppressed, 64));
    Assertions.assertEquals(FeatureExplosionPacketBatching.AddResult.ACCEPTED, buffer.tryAdd(vanilla, 64));
    Assertions.assertTrue(buffer.markSuppressed(10.25D, 64.9D, -2.9D));
    Assertions.assertFalse(buffer.markSuppressed(10.25D, 64.9D, -2.9D));
    Assertions.assertFalse(buffer.markSuppressed(100D, 64D, 100D));

    Assertions.assertEquals(identitySet(List.of(suppressed)), identitySet(buffer.drainSuppressed()));
    Assertions.assertTrue(buffer.drainSuppressed().isEmpty());
    Assertions.assertEquals(
        FeatureExplosionPacketBatching.AddResult.RETIRED,
        buffer.tryAdd(explosion(new Point(1D, 2D, 3D)), 64)
    );
  }

  @Test
  void bufferCapacityFallsBackToVanillaWithoutCreatingCandidate() {
    FeatureExplosionPacketBatching.PendingWorldBuffer buffer =
        new FeatureExplosionPacketBatching.PendingWorldBuffer(null);
    FeatureExplosionPacketBatching.PendingExplosion accepted = explosion(new Point(1D, 2D, 3D));
    FeatureExplosionPacketBatching.PendingExplosion rejected = explosion(new Point(4D, 5D, 6D));

    Assertions.assertEquals(FeatureExplosionPacketBatching.AddResult.ACCEPTED, buffer.tryAdd(accepted, 1));
    Assertions.assertEquals(FeatureExplosionPacketBatching.AddResult.FULL, buffer.tryAdd(rejected, 1));
    Assertions.assertFalse(buffer.markSuppressed(4D, 5D, 6D));
    Assertions.assertTrue(buffer.markSuppressed(1D, 2D, 3D));
    Assertions.assertEquals(identitySet(List.of(accepted)), identitySet(buffer.drainSuppressed()));
  }

  @Test
  void sameCellCandidatesAreConsumedOnePacketAtATime() {
    FeatureExplosionPacketBatching.PendingWorldBuffer buffer =
        new FeatureExplosionPacketBatching.PendingWorldBuffer(null);
    FeatureExplosionPacketBatching.PendingExplosion first = explosion(new Point(1.1D, 2.1D, 3.1D));
    FeatureExplosionPacketBatching.PendingExplosion second = explosion(new Point(1.9D, 2.9D, 3.9D));
    Assertions.assertEquals(FeatureExplosionPacketBatching.AddResult.ACCEPTED, buffer.tryAdd(first, 2));
    Assertions.assertEquals(FeatureExplosionPacketBatching.AddResult.ACCEPTED, buffer.tryAdd(second, 2));

    Assertions.assertTrue(buffer.markSuppressed(1.5D, 2.5D, 3.5D));
    Assertions.assertTrue(buffer.markSuppressed(1.5D, 2.5D, 3.5D));
    Assertions.assertFalse(buffer.markSuppressed(1.5D, 2.5D, 3.5D));
    Assertions.assertEquals(identitySet(List.of(first, second)), identitySet(buffer.drainSuppressed()));
  }

  @Test
  void fourThousandClustersCapThousandRecipientFanoutAndFlushWithinSixtyFourTicks() {
    FeatureExplosionPacketBatching feature = new FeatureExplosionPacketBatching();
    UUID world = UUID.randomUUID();
    TestBridge bridge = new TestBridge();
    bridge.loaded.add(world);
    List<FeatureExplosionPacketBatching.MergedCluster> clusters = new ArrayList<>(4096);
    for (int i = 0; i < 4096; i++) {
      clusters.add(new FeatureExplosionPacketBatching.MergedCluster(i * 32D, 64D, 0D, 1));
    }

    feature.retainClusters(world, clusters);

    for (int tick = 1; tick <= 64; tick++) {
      int before = bridge.sent.get();
      int processed = feature.drainRetainedBroadcasts(bridge, 64);
      Assertions.assertEquals(64, processed);
      Assertions.assertEquals(64, bridge.sent.get() - before);
      if (tick < 64) {
        Assertions.assertEquals(4096 - (tick * 64), feature.retainedClusterCount());
      }
    }

    Assertions.assertEquals(0, feature.retainedClusterCount());
    Assertions.assertEquals(4096, bridge.sent.get());
    Assertions.assertEquals(4096L, feature.readAndResetClusters());
    Assertions.assertEquals(4096L, feature.readAndResetExplosions());
    Assertions.assertEquals(4096L, feature.readAndResetMergedBroadcastsSent());
  }

  @Test
  void suppressionDebtSaturationLeavesAdditionalCandidatesVanilla() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = new FeatureExplosionPacketBatching();
    setField(feature, "acceptingCandidates", true);
    setField(feature, "maxSuppressedExplosionDebt", 2);
    FeatureExplosionPacketBatching.PendingWorldBuffer buffer =
        new FeatureExplosionPacketBatching.PendingWorldBuffer(null);
    FeatureExplosionPacketBatching.PendingExplosion first = explosion(new Point(1.1D, 64D, 1.1D));
    FeatureExplosionPacketBatching.PendingExplosion second = explosion(new Point(2.1D, 64D, 2.1D));
    FeatureExplosionPacketBatching.PendingExplosion vanilla = explosion(new Point(3.1D, 64D, 3.1D));
    buffer.tryAdd(first, 3);
    buffer.tryAdd(second, 3);
    buffer.tryAdd(vanilla, 3);

    Assertions.assertTrue(feature.admitSuppression(buffer, 1.1D, 64D, 1.1D, 1));
    Assertions.assertTrue(feature.admitSuppression(buffer, 2.1D, 64D, 2.1D, 1));
    Assertions.assertFalse(feature.admitSuppression(buffer, 3.1D, 64D, 3.1D, 1));
    setField(feature, "maxSuppressedExplosionDebt", 3);
    Assertions.assertFalse(feature.admitSuppression(buffer, 3.1D, 64D, 3.1D, 1));

    Assertions.assertEquals(2, feature.suppressedExplosionDebt());
    Assertions.assertEquals(identitySet(List.of(first, second)), identitySet(buffer.drainSuppressed()));
  }

  @Test
  void failedBroadcastRetainsDebtAndRetriesTheSameCluster() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = new FeatureExplosionPacketBatching();
    setField(feature, "acceptingCandidates", true);
    setField(feature, "maxSuppressedExplosionDebt", 1);
    UUID world = UUID.randomUUID();
    FeatureExplosionPacketBatching.PendingWorldBuffer buffer =
        new FeatureExplosionPacketBatching.PendingWorldBuffer(world);
    FeatureExplosionPacketBatching.PendingExplosion explosion = explosion(new Point(1D, 64D, 1D));
    buffer.tryAdd(explosion, 1);
    Assertions.assertTrue(feature.admitSuppression(buffer, 1D, 64D, 1D, 1));
    feature.retainClusters(world, List.of(new FeatureExplosionPacketBatching.MergedCluster(1D, 64D, 1D, 1)));
    TestBridge bridge = new TestBridge();
    bridge.loaded.add(world);
    bridge.results.add(false);

    Assertions.assertEquals(0, feature.drainRetainedBroadcasts(bridge, 64));
    Assertions.assertEquals(1, feature.retainedClusterCount());
    Assertions.assertEquals(1, feature.suppressedExplosionDebt());
    Assertions.assertEquals(0L, feature.readAndResetClusters());

    Assertions.assertEquals(1, feature.drainRetainedBroadcasts(bridge, 64));
    Assertions.assertEquals(0, feature.retainedClusterCount());
    Assertions.assertEquals(0, feature.suppressedExplosionDebt());
    Assertions.assertEquals(1L, feature.readAndResetClusters());
    Assertions.assertEquals(2, bridge.attempts.get());
  }

  @Test
  void shutdownDrainsPastTheTickBudgetWithoutRetiringDebt() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = new FeatureExplosionPacketBatching();
    setField(feature, "acceptingCandidates", true);
    setField(feature, "maxSuppressedExplosionDebt", 5);
    setField(feature, "maxMergedBroadcastsPerTick", 2);
    UUID world = UUID.randomUUID();
    FeatureExplosionPacketBatching.PendingWorldBuffer buffer =
        new FeatureExplosionPacketBatching.PendingWorldBuffer(world);
    List<FeatureExplosionPacketBatching.MergedCluster> clusters = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      FeatureExplosionPacketBatching.PendingExplosion explosion = explosion(new Point(i, 64D, 0D));
      buffer.tryAdd(explosion, 5);
      Assertions.assertTrue(feature.admitSuppression(buffer, i, 64D, 0D, 1));
      clusters.add(new FeatureExplosionPacketBatching.MergedCluster(i, 64D, 0D, 1));
    }
    feature.retainClusters(world, clusters);
    TestBridge bridge = new TestBridge();
    bridge.loaded.add(world);
    setField(feature, "explosions", bridge);

    feature.onDeactivate();

    Assertions.assertEquals(5, bridge.sent.get());
    Assertions.assertEquals(0, feature.retainedClusterCount());
    Assertions.assertEquals(0, feature.suppressedExplosionDebt());
    Assertions.assertEquals(0L, feature.bypassedCount());
    Assertions.assertEquals(5L, feature.readAndResetMergedBroadcastsSent());
  }

  @Test
  void shutdownFailureRetainsDebtAndRetriesExactlyOnce() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = featureWithRetainedDebt(1, 1000);
    UUID world = UUID.randomUUID();
    feature.retainClusters(world, List.of(new FeatureExplosionPacketBatching.MergedCluster(1D, 64D, 1D, 1)));
    TestBridge bridge = new TestBridge();
    bridge.loaded.add(world);
    bridge.results.add(false);
    setField(feature, "explosions", bridge);

    IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, feature::onDeactivate);
    Assertions.assertTrue(failure.getMessage().contains("retained 1 merged broadcasts"));
    Assertions.assertEquals(1, feature.retainedClusterCount());
    Assertions.assertEquals(1, feature.suppressedExplosionDebt());
    Assertions.assertEquals(0L, feature.bypassedCount());
    Assertions.assertEquals(0, bridge.uninstalls.get());

    Assertions.assertDoesNotThrow(feature::onDeactivate);

    Assertions.assertEquals(2, bridge.attempts.get());
    Assertions.assertEquals(1, bridge.uninstalls.get());
    Assertions.assertEquals(0, feature.retainedClusterCount());
    Assertions.assertEquals(0, feature.suppressedExplosionDebt());
    Assertions.assertEquals(1L, feature.readAndResetMergedBroadcastsSent());
  }

  @Test
  void shutdownTimeoutRetainsDebtWithoutAttemptingOrRelabelingIt() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = featureWithRetainedDebt(1, 0);
    UUID world = UUID.randomUUID();
    feature.retainClusters(world, List.of(new FeatureExplosionPacketBatching.MergedCluster(1D, 64D, 1D, 1)));
    TestBridge bridge = new TestBridge();
    bridge.loaded.add(world);
    setField(feature, "explosions", bridge);

    IllegalStateException timeout = Assertions.assertThrows(IllegalStateException.class, feature::onDeactivate);
    Assertions.assertTrue(timeout.getMessage().contains("timed out"));
    Assertions.assertEquals(1, feature.retainedClusterCount());
    Assertions.assertEquals(1, feature.suppressedExplosionDebt());
    Assertions.assertEquals(0L, feature.bypassedCount());
    Assertions.assertEquals(0, bridge.attempts.get());

    setField(feature, "shutdownDrainTimeoutMS", 1000);
    Assertions.assertDoesNotThrow(feature::onDeactivate);

    Assertions.assertEquals(1, bridge.attempts.get());
    Assertions.assertEquals(0, feature.retainedClusterCount());
    Assertions.assertEquals(0, feature.suppressedExplosionDebt());
    Assertions.assertEquals(0L, feature.bypassedCount());
  }

  @Test
  void confirmedMissingWorldReleasesOnlyItsDebtAndDoesNotBlockLaterWorlds() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = featureWithRetainedDebt(6, 1000);
    UUID missingWorldId = UUID.randomUUID();
    UUID availableWorld = UUID.randomUUID();
    feature.retainClusters(missingWorldId, List.of(
        new FeatureExplosionPacketBatching.MergedCluster(1D, 64D, 1D, 2),
        new FeatureExplosionPacketBatching.MergedCluster(2D, 64D, 2D, 3)
    ));
    feature.retainClusters(availableWorld, List.of(new FeatureExplosionPacketBatching.MergedCluster(3D, 64D, 3D, 1)));
    TestBridge bridge = new TestBridge();
    bridge.loaded.add(availableWorld);
    bridge.unloaded.add(missingWorldId);
    bridge.results.add(false);
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);

    try (MockedStatic<ReactPlatforms> platforms = Mockito.mockStatic(ReactPlatforms.class)) {
      platforms.when(ReactPlatforms::getOrNull).thenReturn(platform);

      Assertions.assertEquals(0, feature.drainRetainedBroadcasts(bridge, 64));
      Assertions.assertEquals(1, feature.retainedClusterCount());
      Assertions.assertEquals(1, feature.suppressedExplosionDebt());
      Assertions.assertEquals(5L, feature.readAndResetUnavailableWorldSuppressedExplosions());
      Mockito.verify(platform, Mockito.times(1)).reportError(Mockito.anyString(), Mockito.any(Throwable.class));

      Assertions.assertEquals(1, feature.drainRetainedBroadcasts(bridge, 64));
    }

    Assertions.assertEquals(0, feature.retainedClusterCount());
    Assertions.assertEquals(0, feature.suppressedExplosionDebt());
    Assertions.assertEquals(1L, feature.readAndResetMergedBroadcastsSent());
    Assertions.assertEquals(2, bridge.attempts.get());
  }

  @Test
  void unresolvedWorldRetainsDebtUntilThePlatformCanConfirmItsDisposition() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = featureWithRetainedDebt(1, 1000);
    feature.retainClusters(UUID.randomUUID(), List.of(new FeatureExplosionPacketBatching.MergedCluster(1D, 64D, 1D, 1)));
    TestBridge bridge = new TestBridge();
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);

    try (MockedStatic<ReactPlatforms> platforms = Mockito.mockStatic(ReactPlatforms.class)) {
      platforms.when(ReactPlatforms::getOrNull).thenReturn(platform);

      Assertions.assertEquals(0, feature.drainRetainedBroadcasts(bridge, 64));
      Assertions.assertEquals(1, feature.retainedClusterCount());
      Assertions.assertEquals(1, feature.suppressedExplosionDebt());
      Assertions.assertEquals(0L, feature.readAndResetUnavailableWorldSuppressedExplosions());
      Mockito.verifyNoInteractions(platform);
    }

    Assertions.assertEquals(0, bridge.attempts.get());
  }

  @Test
  void confirmedMissingWorldDebtCanDeactivateAndReactivateCleanly() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = featureWithRetainedDebt(2, 1000);
    UUID missingWorldId = UUID.randomUUID();
    feature.retainClusters(missingWorldId, List.of(new FeatureExplosionPacketBatching.MergedCluster(1D, 64D, 1D, 2)));
    TestBridge bridge = new TestBridge();
    bridge.unloaded.add(missingWorldId);
    setField(feature, "explosions", bridge);
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    Mockito.when(platform.explosionHost()).thenReturn(Optional.of(bridge));

    try (MockedStatic<ReactPlatforms> platforms = Mockito.mockStatic(ReactPlatforms.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      platforms.when(ReactPlatforms::getOrNull).thenReturn(platform);
      platforms.when(ReactPlatforms::get).thenReturn(platform);

      Assertions.assertDoesNotThrow(feature::onDeactivate);
      Assertions.assertEquals(0, feature.retainedClusterCount());
      Assertions.assertEquals(0, feature.suppressedExplosionDebt());
      Mockito.verify(platform, Mockito.times(1)).reportError(Mockito.anyString(), Mockito.any(Throwable.class));

      Assertions.assertDoesNotThrow(feature::onActivate);
      Assertions.assertTrue(feature.isBridgeActive());
      Assertions.assertTrue(feature.isSuppressorActive());
      Assertions.assertEquals(2L, feature.readAndResetUnavailableWorldSuppressedExplosions());
      Assertions.assertEquals(0L, feature.readAndResetUnavailableWorldSuppressedExplosions());
    }

    Assertions.assertEquals(1, bridge.uninstalls.get());
    Assertions.assertEquals(1, bridge.installs.get());
  }

  @Test
  void explosionsNearPlayersKeepVanillaPacketsAndOthersBecomeCandidates() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = new FeatureExplosionPacketBatching();
    setField(feature, "acceptingCandidates", true);
    PressureGate gate = (PressureGate) getField(feature, "gate");
    gate.update(1L, true, false, 0L, 0L);
    Assertions.assertTrue(gate.update(2L, true, false, 0L, 0L));
    UUID world = UUID.randomUUID();
    art.arcane.react.platform.WorldHost worlds = Mockito.mock(art.arcane.react.platform.WorldHost.class);
    Mockito.when(worlds.anyPlayerNear(Mockito.eq(new GameLocation(world, 0D, 64D, 0D)), Mockito.anyDouble())).thenReturn(true);
    setField(feature, "worlds", worlds);

    feature.onExplode(new TestExplosion(new GameLocation(world, 0D, 64D, 0D)));
    feature.onExplode(new TestExplosion(new GameLocation(world, 100D, 64D, 0D)));

    Assertions.assertEquals(1L, feature.bypassedCount());
    Assertions.assertTrue(feature.admitSuppression(
        (FeatureExplosionPacketBatching.PendingWorldBuffer) ((Map<?, ?>) getField(feature, "pending")).get(world),
        100D, 64D, 0D, 3));
    Assertions.assertEquals(3L, feature.readAndResetSuppressedPackets());
  }

  private static FeatureExplosionPacketBatching featureWithRetainedDebt(
      int debt,
      int shutdownDrainTimeoutMS
  ) throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = new FeatureExplosionPacketBatching();
    setField(feature, "acceptingCandidates", true);
    setField(feature, "maxSuppressedExplosionDebt", debt);
    setField(feature, "shutdownDrainTimeoutMS", shutdownDrainTimeoutMS);
    FeatureExplosionPacketBatching.PendingWorldBuffer buffer =
        new FeatureExplosionPacketBatching.PendingWorldBuffer(null);
    for (int index = 0; index < debt; index++) {
      FeatureExplosionPacketBatching.PendingExplosion explosion = explosion(new Point(index, 64D, 0D));
      buffer.tryAdd(explosion, debt);
      Assertions.assertTrue(feature.admitSuppression(buffer, index, 64D, 0D, 1));
    }
    return feature;
  }

  private static FeatureExplosionPacketBatching.PendingExplosion explosion(Point point) {
    return new FeatureExplosionPacketBatching.PendingExplosion(point.x, point.y, point.z);
  }

  private static List<List<FeatureExplosionPacketBatching.PendingExplosion>> bruteForce(
      List<FeatureExplosionPacketBatching.PendingExplosion> explosions,
      Map<FeatureExplosionPacketBatching.PendingExplosion, Point> points,
      double radius
  ) {
    boolean[] consumed = new boolean[explosions.size()];
    List<List<FeatureExplosionPacketBatching.PendingExplosion>> clusters = new ArrayList<>();
    double radiusSquared = radius * radius;
    for (int headIndex = 0; headIndex < explosions.size(); headIndex++) {
      if (consumed[headIndex]) {
        continue;
      }

      FeatureExplosionPacketBatching.PendingExplosion head = explosions.get(headIndex);
      Point headPoint = points.get(head);
      List<FeatureExplosionPacketBatching.PendingExplosion> cluster = new ArrayList<>();
      cluster.add(head);
      consumed[headIndex] = true;
      for (int candidateIndex = headIndex + 1; candidateIndex < explosions.size(); candidateIndex++) {
        if (consumed[candidateIndex]) {
          continue;
        }

        FeatureExplosionPacketBatching.PendingExplosion candidate = explosions.get(candidateIndex);
        Point candidatePoint = points.get(candidate);
        double dx = candidatePoint.x - headPoint.x;
        double dy = candidatePoint.y - headPoint.y;
        double dz = candidatePoint.z - headPoint.z;
        if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
          consumed[candidateIndex] = true;
          cluster.add(candidate);
        }
      }
      clusters.add(cluster);
    }
    return clusters;
  }

  private static List<Set<FeatureExplosionPacketBatching.PendingExplosion>> asIdentitySets(
      List<List<FeatureExplosionPacketBatching.PendingExplosion>> clusters
  ) {
    List<Set<FeatureExplosionPacketBatching.PendingExplosion>> sets = new ArrayList<>(clusters.size());
    for (List<FeatureExplosionPacketBatching.PendingExplosion> cluster : clusters) {
      sets.add(identitySet(cluster));
    }
    return sets;
  }

  private static Set<FeatureExplosionPacketBatching.PendingExplosion> identitySet(
      List<FeatureExplosionPacketBatching.PendingExplosion> values
  ) {
    Set<FeatureExplosionPacketBatching.PendingExplosion> set = Collections.newSetFromMap(new IdentityHashMap<>());
    set.addAll(values);
    return set;
  }

  private static Object getField(Object target, String name) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private record Point(double x, double y, double z) {
  }

  private record TestExplosion(GameLocation location) implements ExplosionHost.Explosion {
    @Override
    public boolean fromBlock() {
      return false;
    }

    @Override
    public boolean destroysBlocks() {
      return true;
    }

    @Override
    public float yield() {
      return 1F;
    }

    @Override
    public List<ExplosionHost.ExplodedBlock> blocks() {
      return List.of();
    }

    @Override
    public void removeBlocks(Predicate<ExplosionHost.ExplodedBlock> filter) {
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public void cancel() {
    }
  }

  private static final class TestBridge implements ExplosionHost {
    private final Set<UUID> loaded = new HashSet<>();
    private final Set<UUID> unloaded = new HashSet<>();
    private final Deque<Boolean> results = new ArrayDeque<>();
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger sent = new AtomicInteger();
    private final AtomicInteger installs = new AtomicInteger();
    private final AtomicInteger uninstalls = new AtomicInteger();

    @Override
    public Subscription onExplode(ListenerPriority priority, boolean ignoreCancelled, Consumer<Explosion> listener) {
      return Subscription.NONE;
    }

    @Override
    public Subscription onTntPrimed(ListenerPriority priority, boolean ignoreCancelled, Consumer<PrimedTnt> listener) {
      return Subscription.NONE;
    }

    @Override
    public Subscription onWorldUnload(ListenerPriority priority, boolean ignoreCancelled, Consumer<WorldUnload> listener) {
      return Subscription.NONE;
    }

    @Override
    public String blockType(GameLocation location) {
      return null;
    }

    @Override
    public void dropContents(GameLocation location) {
    }

    @Override
    public void clear(GameLocation location, boolean fastBlockUpdates) {
    }

    @Override
    public void explode(GameLocation location, float power) {
    }

    @Override
    public void installPacketHooks(PacketHooks hooks) {
      installs.incrementAndGet();
      hooks.installed(true, true);
    }

    @Override
    public void uninstallPacketHooks() {
      uninstalls.incrementAndGet();
    }

    @Override
    public boolean canBroadcast() {
      return true;
    }

    @Override
    public WorldState worldState(UUID worldId) {
      if (loaded.contains(worldId)) {
        return WorldState.LOADED;
      }
      return unloaded.contains(worldId) ? WorldState.UNLOADED : WorldState.UNKNOWN;
    }

    @Override
    public boolean broadcastMergedExplosion(UUID worldId, double x, double y, double z, float radius, double rangeBlocks) {
      attempts.incrementAndGet();
      Boolean result = results.pollFirst();
      boolean success = result == null || result;
      if (success) {
        sent.incrementAndGet();
      }
      return success;
    }
  }
}
