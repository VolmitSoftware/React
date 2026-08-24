package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.nms.NmsBridge;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
  void foliaKeepsPacketBatchingMeasurementOnly() {
    FeatureExplosionPacketBatching feature = new FeatureExplosionPacketBatching();
    NmsBridge bridge = Mockito.mock(NmsBridge.class);
    Mockito.when(bridge.installExplosionHook(Mockito.any())).thenReturn(true);

    try (MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bridges.when(NmsBridges::get).thenReturn(bridge);
      scheduler.when(J::isFoliaThreading).thenReturn(true);

      feature.onActivate();

      Assertions.assertTrue(feature.isBridgeActive());
      Assertions.assertTrue(feature.isMeasurementOnly());
      Mockito.verify(bridge, Mockito.never()).installExplosionPacketSuppressor(Mockito.any());
    }
  }

  @Test
  void fourThousandClustersCapThousandRecipientFanoutAndFlushWithinSixtyFourTicks() {
    FeatureExplosionPacketBatching feature = new FeatureExplosionPacketBatching();
    World world = Mockito.mock(World.class);
    NmsBridge bridge = Mockito.mock(NmsBridge.class);
    AtomicLong recipientDeliveries = new AtomicLong();
    Mockito.when(bridge.broadcastMergedExplosion(
        Mockito.same(world),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyFloat(),
        Mockito.anyDouble()
    )).thenAnswer(invocation -> {
      recipientDeliveries.addAndGet(1_000L);
      return true;
    });
    List<FeatureExplosionPacketBatching.MergedCluster> clusters = new ArrayList<>(4096);
    for (int i = 0; i < 4096; i++) {
      clusters.add(new FeatureExplosionPacketBatching.MergedCluster(i * 32D, 64D, 0D, 1));
    }

    feature.retainClusters(world, clusters);

    for (int tick = 1; tick <= 64; tick++) {
      long before = recipientDeliveries.get();
      int processed = feature.drainRetainedBroadcasts(bridge, 64);
      Assertions.assertEquals(64, processed);
      Assertions.assertEquals(64_000L, recipientDeliveries.get() - before);
      if (tick < 64) {
        Assertions.assertEquals(4096 - (tick * 64), feature.retainedClusterCount());
      }
    }

    Assertions.assertEquals(0, feature.retainedClusterCount());
    Assertions.assertEquals(4_096_000L, recipientDeliveries.get());
    Assertions.assertEquals(4096L, feature.readAndResetClusters());
    Assertions.assertEquals(4096L, feature.readAndResetExplosions());
    Assertions.assertEquals(4096L, feature.readAndResetMergedBroadcastsSent());
    Mockito.verify(bridge, Mockito.times(4096)).broadcastMergedExplosion(
        Mockito.same(world),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyFloat(),
        Mockito.anyDouble()
    );
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
    World world = Mockito.mock(World.class);
    FeatureExplosionPacketBatching.PendingWorldBuffer buffer =
        new FeatureExplosionPacketBatching.PendingWorldBuffer(world);
    FeatureExplosionPacketBatching.PendingExplosion explosion = explosion(new Point(1D, 64D, 1D));
    buffer.tryAdd(explosion, 1);
    Assertions.assertTrue(feature.admitSuppression(buffer, 1D, 64D, 1D, 1));
    feature.retainClusters(
        world,
        List.of(new FeatureExplosionPacketBatching.MergedCluster(1D, 64D, 1D, 1))
    );
    NmsBridge bridge = Mockito.mock(NmsBridge.class);
    Mockito.when(bridge.broadcastMergedExplosion(
        Mockito.same(world),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyFloat(),
        Mockito.anyDouble()
    )).thenReturn(false, true);

    Assertions.assertEquals(0, feature.drainRetainedBroadcasts(bridge, 64));
    Assertions.assertEquals(1, feature.retainedClusterCount());
    Assertions.assertEquals(1, feature.suppressedExplosionDebt());
    Assertions.assertEquals(0L, feature.readAndResetClusters());

    Assertions.assertEquals(1, feature.drainRetainedBroadcasts(bridge, 64));
    Assertions.assertEquals(0, feature.retainedClusterCount());
    Assertions.assertEquals(0, feature.suppressedExplosionDebt());
    Assertions.assertEquals(1L, feature.readAndResetClusters());
    Mockito.verify(bridge, Mockito.times(2)).broadcastMergedExplosion(
        Mockito.same(world),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyFloat(),
        Mockito.anyDouble()
    );
  }

  @Test
  void shutdownDrainsPastTheTickBudgetWithoutRetiringDebt() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = new FeatureExplosionPacketBatching();
    setField(feature, "acceptingCandidates", true);
    setField(feature, "maxSuppressedExplosionDebt", 5);
    setField(feature, "maxMergedBroadcastsPerTick", 2);
    World world = Mockito.mock(World.class);
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
    NmsBridge bridge = Mockito.mock(NmsBridge.class);
    Mockito.when(bridge.broadcastMergedExplosion(
        Mockito.same(world),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyFloat(),
        Mockito.anyDouble()
    )).thenReturn(true);

    try (MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class)) {
      bridges.when(NmsBridges::get).thenReturn(bridge);

      feature.onDeactivate();
    }

    Mockito.verify(bridge, Mockito.times(5)).broadcastMergedExplosion(
        Mockito.same(world),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyFloat(),
        Mockito.anyDouble()
    );
    Assertions.assertEquals(0, feature.retainedClusterCount());
    Assertions.assertEquals(0, feature.suppressedExplosionDebt());
    Assertions.assertEquals(0L, feature.bypassedCount());
    Assertions.assertEquals(5L, feature.readAndResetMergedBroadcastsSent());
  }

  @Test
  void shutdownFailureRetainsDebtAndRetriesExactlyOnce() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = featureWithRetainedDebt(1, 1000);
    World world = Mockito.mock(World.class);
    feature.retainClusters(
        world,
        List.of(new FeatureExplosionPacketBatching.MergedCluster(1D, 64D, 1D, 1))
    );
    NmsBridge bridge = Mockito.mock(NmsBridge.class);
    Mockito.when(bridge.broadcastMergedExplosion(
        Mockito.same(world),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyFloat(),
        Mockito.anyDouble()
    )).thenReturn(false, true);

    try (MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class)) {
      bridges.when(NmsBridges::get).thenReturn(bridge);

      IllegalStateException failure = Assertions.assertThrows(
          IllegalStateException.class,
          feature::onDeactivate
      );
      Assertions.assertTrue(failure.getMessage().contains("retained 1 merged broadcasts"));
      Assertions.assertEquals(1, feature.retainedClusterCount());
      Assertions.assertEquals(1, feature.suppressedExplosionDebt());
      Assertions.assertEquals(0L, feature.bypassedCount());
      Mockito.verify(bridge, Mockito.never()).uninstallExplosionPacketSuppressor();

      Assertions.assertDoesNotThrow(feature::onDeactivate);
    }

    Mockito.verify(bridge, Mockito.times(2)).broadcastMergedExplosion(
        Mockito.same(world),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyFloat(),
        Mockito.anyDouble()
    );
    Mockito.verify(bridge, Mockito.times(1)).uninstallExplosionPacketSuppressor();
    Assertions.assertEquals(0, feature.retainedClusterCount());
    Assertions.assertEquals(0, feature.suppressedExplosionDebt());
    Assertions.assertEquals(1L, feature.readAndResetMergedBroadcastsSent());
  }

  @Test
  void shutdownTimeoutRetainsDebtWithoutAttemptingOrRelabelingIt() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = featureWithRetainedDebt(1, 0);
    World world = Mockito.mock(World.class);
    feature.retainClusters(
        world,
        List.of(new FeatureExplosionPacketBatching.MergedCluster(1D, 64D, 1D, 1))
    );
    NmsBridge bridge = Mockito.mock(NmsBridge.class);
    Mockito.when(bridge.broadcastMergedExplosion(
        Mockito.same(world),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyFloat(),
        Mockito.anyDouble()
    )).thenReturn(true);

    try (MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class)) {
      bridges.when(NmsBridges::get).thenReturn(bridge);

      IllegalStateException timeout = Assertions.assertThrows(
          IllegalStateException.class,
          feature::onDeactivate
      );
      Assertions.assertTrue(timeout.getMessage().contains("timed out"));
      Assertions.assertEquals(1, feature.retainedClusterCount());
      Assertions.assertEquals(1, feature.suppressedExplosionDebt());
      Assertions.assertEquals(0L, feature.bypassedCount());
      Mockito.verify(bridge, Mockito.never()).broadcastMergedExplosion(
          Mockito.any(),
          Mockito.anyDouble(),
          Mockito.anyDouble(),
          Mockito.anyDouble(),
          Mockito.anyFloat(),
          Mockito.anyDouble()
      );

      setField(feature, "shutdownDrainTimeoutMS", 1000);
      Assertions.assertDoesNotThrow(feature::onDeactivate);
    }

    Mockito.verify(bridge, Mockito.times(1)).broadcastMergedExplosion(
        Mockito.same(world),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyFloat(),
        Mockito.anyDouble()
    );
    Assertions.assertEquals(0, feature.retainedClusterCount());
    Assertions.assertEquals(0, feature.suppressedExplosionDebt());
    Assertions.assertEquals(0L, feature.bypassedCount());
  }

  @Test
  void confirmedMissingWorldReleasesOnlyItsDebtAndDoesNotBlockLaterWorlds()
      throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = featureWithRetainedDebt(6, 1000);
    UUID missingWorldId = UUID.randomUUID();
    World availableWorld = Mockito.mock(World.class);
    feature.retainClusters(
        missingWorldId,
        List.of(
            new FeatureExplosionPacketBatching.MergedCluster(1D, 64D, 1D, 2),
            new FeatureExplosionPacketBatching.MergedCluster(2D, 64D, 2D, 3)
        )
    );
    feature.retainClusters(
        availableWorld,
        List.of(new FeatureExplosionPacketBatching.MergedCluster(3D, 64D, 3D, 1))
    );
    Server server = Mockito.mock(Server.class);
    Mockito.when(server.getWorld(missingWorldId)).thenReturn(null);
    NmsBridge bridge = Mockito.mock(NmsBridge.class);
    Mockito.when(bridge.broadcastMergedExplosion(
        Mockito.same(availableWorld),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyFloat(),
        Mockito.anyDouble()
    )).thenReturn(false, true);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<React> errors = Mockito.mockStatic(React.class)) {
      bukkit.when(Bukkit::getServer).thenReturn(server);

      Assertions.assertEquals(0, feature.drainRetainedBroadcasts(bridge, 64));
      Assertions.assertEquals(1, feature.retainedClusterCount());
      Assertions.assertEquals(1, feature.suppressedExplosionDebt());
      Assertions.assertEquals(5L, feature.readAndResetUnavailableWorldSuppressedExplosions());
      errors.verify(() -> React.reportError(Mockito.any(Throwable.class)), Mockito.times(1));

      Assertions.assertEquals(1, feature.drainRetainedBroadcasts(bridge, 64));
    }

    Assertions.assertEquals(0, feature.retainedClusterCount());
    Assertions.assertEquals(0, feature.suppressedExplosionDebt());
    Assertions.assertEquals(1L, feature.readAndResetMergedBroadcastsSent());
    Mockito.verify(bridge, Mockito.times(2)).broadcastMergedExplosion(
        Mockito.same(availableWorld),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyFloat(),
        Mockito.anyDouble()
    );
  }

  @Test
  void unresolvedWorldRetainsDebtUntilBukkitCanConfirmItsDisposition() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = featureWithRetainedDebt(1, 1000);
    feature.retainClusters(
        UUID.randomUUID(),
        List.of(new FeatureExplosionPacketBatching.MergedCluster(1D, 64D, 1D, 1))
    );
    NmsBridge bridge = Mockito.mock(NmsBridge.class);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<React> errors = Mockito.mockStatic(React.class)) {
      bukkit.when(Bukkit::getServer).thenReturn(null);

      Assertions.assertEquals(0, feature.drainRetainedBroadcasts(bridge, 64));
      Assertions.assertEquals(1, feature.retainedClusterCount());
      Assertions.assertEquals(1, feature.suppressedExplosionDebt());
      Assertions.assertEquals(0L, feature.readAndResetUnavailableWorldSuppressedExplosions());
      errors.verifyNoInteractions();
    }

    Mockito.verifyNoInteractions(bridge);
  }

  @Test
  void confirmedMissingWorldDebtCanDeactivateAndReactivateCleanly() throws ReflectiveOperationException {
    FeatureExplosionPacketBatching feature = featureWithRetainedDebt(2, 1000);
    UUID missingWorldId = UUID.randomUUID();
    feature.retainClusters(
        missingWorldId,
        List.of(new FeatureExplosionPacketBatching.MergedCluster(1D, 64D, 1D, 2))
    );
    Server server = Mockito.mock(Server.class);
    Mockito.when(server.getWorld(missingWorldId)).thenReturn(null);
    NmsBridge bridge = Mockito.mock(NmsBridge.class);
    Mockito.when(bridge.installExplosionHook(Mockito.any())).thenReturn(true);
    Mockito.when(bridge.installExplosionPacketSuppressor(Mockito.any())).thenReturn(true);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<React> errors = Mockito.mockStatic(React.class);
         MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      bukkit.when(Bukkit::getServer).thenReturn(server);
      bridges.when(NmsBridges::get).thenReturn(bridge);
      scheduler.when(J::isFoliaThreading).thenReturn(false);

      Assertions.assertDoesNotThrow(feature::onDeactivate);
      Assertions.assertEquals(0, feature.retainedClusterCount());
      Assertions.assertEquals(0, feature.suppressedExplosionDebt());
      errors.verify(() -> React.reportError(Mockito.any(Throwable.class)), Mockito.times(1));

      Assertions.assertDoesNotThrow(feature::onActivate);
      Assertions.assertTrue(feature.isBridgeActive());
      Assertions.assertTrue(feature.isSuppressorActive());
      Assertions.assertEquals(2L, feature.readAndResetUnavailableWorldSuppressedExplosions());
      Assertions.assertEquals(0L, feature.readAndResetUnavailableWorldSuppressedExplosions());
    }

    Mockito.verify(bridge, Mockito.times(1)).uninstallExplosionPacketSuppressor();
    Mockito.verify(bridge, Mockito.times(1)).installExplosionPacketSuppressor(Mockito.any());
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

  private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private record Point(double x, double y, double z) {
  }
}
