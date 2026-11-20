package art.arcane.react.content.feature;

import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.ExplosionHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

class FeatureFastExplosionsTest {
  private static final UUID WORLD = UUID.randomUUID();
  private static final UUID OTHER_WORLD = UUID.randomUUID();

  @Test
  void tntSpawnsReceiveSequentialFuseSpread() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestTnt first = new TestTnt(80);
    TestTnt second = new TestTnt(80);

    feature.onPrimed(first);
    feature.onPrimed(second);

    Assertions.assertEquals(80, first.fuse);
    Assertions.assertEquals(87, second.fuse);
  }

  @Test
  void tickingStartsANewFuseSpreadWindow() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestTnt first = new TestTnt(80);
    TestTnt nextWindow = new TestTnt(80);

    feature.onPrimed(first);
    feature.onTick();
    feature.onPrimed(nextWindow);

    Assertions.assertEquals(80, nextWindow.fuse);
  }

  @Test
  void destructiveEntityExplosionsUseTheFastBlockPath() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestExplosion explosion = new TestExplosion(new ArrayList<>(), 0F, true);

    feature.onExplode(explosion);

    Assertions.assertTrue(explosion.blocksRead);
  }

  @Test
  void nonDestructiveEntityExplosionsStayOutOfTheFastBlockPath() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    ExplosionHost.ExplodedBlock block = block(WORLD, 0, 64, 0, "STONE");
    List<ExplosionHost.ExplodedBlock> blocks = new ArrayList<>(List.of(block));
    TestExplosion explosion = new TestExplosion(blocks, 0F, false);

    feature.onExplode(explosion);

    Assertions.assertEquals(List.of(block), blocks);
    Assertions.assertFalse(explosion.blocksRead);
  }

  @Test
  void blockExplosionsAlwaysUseTheFastBlockPath() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestExplosion explosion = new TestExplosion(new ArrayList<>(), 0F, false);
    explosion.fromBlock = true;

    feature.onExplode(explosion);

    Assertions.assertTrue(explosion.blocksRead);
  }

  @Test
  void counterBudgetsResetOnThePerTickCadence() throws ReflectiveOperationException {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    Assertions.assertEquals(50, feature.getTickInterval());
    setBoolean(feature, "explosionChainReactions", true);
    setInt(feature, "maxPrimesPerTick", 0);
    setInt(feature, "maxExplosionChainsPerTick", 1);
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock permitted = host.block(WORLD, 0, 64, 0, "TNT");
    ExplosionHost.ExplodedBlock overflow = host.block(WORLD, 16, 64, 0, "TNT");
    ExplosionHost.ExplodedBlock nextWindow = host.block(WORLD, 32, 64, 0, "TNT");
    List<ScheduledBatch> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      captureScheduledBatches(scheduler, scheduled, true);

      feature.onExplode(new TestExplosion(new ArrayList<>(List.of(permitted, overflow)), 0F, true));
      runAll(scheduled);

      Assertions.assertEquals(2, scheduled.size());
      Assertions.assertEquals(List.of(permitted.location()), host.explosions);

      feature.onTick();
      scheduled.clear();

      feature.onExplode(new TestExplosion(new ArrayList<>(List.of(nextWindow)), 0F, true));
      runAll(scheduled);

      Assertions.assertEquals(1, scheduled.size());
      Assertions.assertEquals(List.of(permitted.location(), nextWindow.location()), host.explosions);
      Assertions.assertEquals(3, host.fastClears());
    }
  }

  @Test
  void affectedBlocksScheduleOneTaskPerOwningChunk() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock first = host.block(WORLD, 0, 64, 0, "TNT");
    ExplosionHost.ExplodedBlock sameChunk = host.block(WORLD, 15, 64, 0, "TNT");
    ExplosionHost.ExplodedBlock nextChunk = host.block(WORLD, 16, 64, 0, "TNT");
    ExplosionHost.ExplodedBlock negativeChunk = host.block(WORLD, -1, 64, 0, "TNT");
    List<ExplosionHost.ExplodedBlock> blocks = new ArrayList<>(List.of(first, sameChunk, nextChunk, negativeChunk));
    List<ScheduledBatch> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      captureScheduledBatches(scheduler, scheduled, true);

      feature.onExplode(new TestExplosion(blocks, 0F, true));

      Assertions.assertEquals(3, scheduled.size());
      Assertions.assertEquals(0, scheduled.get(0).chunkX());
      Assertions.assertEquals(1, scheduled.get(1).chunkX());
      Assertions.assertEquals(-1, scheduled.get(2).chunkX());

      scheduled.get(0).task().run();
      Assertions.assertEquals(List.of(first.location(), sameChunk.location()), host.typeReads);
      scheduled.get(1).task().run();
      scheduled.get(2).task().run();
      Assertions.assertEquals(
          List.of(first.location(), sameChunk.location(), nextChunk.location(), negativeChunk.location()),
          host.typeReads);
    }
  }

  @Test
  void foliaChunkExecutionOrderCannotChangeGlobalChainPermits() throws ReflectiveOperationException {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    setBoolean(feature, "explosionChainReactions", true);
    setInt(feature, "maxPrimesPerTick", 0);
    setInt(feature, "maxExplosionChainsPerTick", 2);
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock first = host.block(WORLD, 0, 64, 0, "TNT");
    ExplosionHost.ExplodedBlock second = host.block(WORLD, 16, 64, 0, "TNT");
    ExplosionHost.ExplodedBlock third = host.block(WORLD, 1, 64, 0, "TNT");
    ExplosionHost.ExplodedBlock fourth = host.block(WORLD, 17, 64, 0, "TNT");
    List<ExplosionHost.ExplodedBlock> blocks = new ArrayList<>(List.of(first, second, third, fourth));
    List<ScheduledBatch> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      captureScheduledBatches(scheduler, scheduled, true);

      feature.onExplode(new TestExplosion(blocks, 0F, true));
      runAll(scheduled);

      Assertions.assertEquals(2, scheduled.size());
      Assertions.assertTrue(blocks.isEmpty());
      Assertions.assertEquals(4, host.fastClears());
      Assertions.assertEquals(List.of(first.location(), second.location()), host.explosions);
    }
  }

  @Test
  void reservedVanillaPrimesStayInTheExplosion() throws ReflectiveOperationException {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    setInt(feature, "maxPrimesPerTick", 1);
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock primed = host.block(WORLD, 0, 64, 0, "TNT");
    ExplosionHost.ExplodedBlock kept = host.block(WORLD, 1, 64, 0, "TNT");
    ExplosionHost.ExplodedBlock stone = host.block(WORLD, 2, 64, 0, "STONE");
    List<ExplosionHost.ExplodedBlock> blocks = new ArrayList<>(List.of(primed, kept, stone));
    List<ScheduledBatch> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      captureScheduledBatches(scheduler, scheduled, false);

      feature.onExplode(new TestExplosion(blocks, 0F, true));
      runAll(scheduled);

      Assertions.assertEquals(List.of(kept), blocks);
      Assertions.assertEquals(List.of(stone.location()), host.clears);
      Assertions.assertTrue(host.explosions.isEmpty());
    }
  }

  @Test
  void batchedBlocksPreserveDropsAndRemoval() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock first = host.block(WORLD, 0, 64, 0, "STONE");
    ExplosionHost.ExplodedBlock second = host.block(WORLD, 1, 64, 0, "STONE");
    List<ExplosionHost.ExplodedBlock> blocks = new ArrayList<>(List.of(first, second));
    List<ScheduledBatch> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      captureScheduledBatches(scheduler, scheduled, true);

      feature.onExplode(new TestExplosion(blocks, 1F, true));
      Assertions.assertEquals(1, scheduled.size());
      scheduled.get(0).task().run();

      Assertions.assertEquals(List.of(first.location(), second.location()), host.drops);
      Assertions.assertEquals(2, host.fastClears());
    }
  }

  @Test
  void changedBlocksAreSkipped() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock block = host.block(WORLD, 0, 64, 0, "STONE");
    List<ScheduledBatch> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      captureScheduledBatches(scheduler, scheduled, false);

      feature.onExplode(new TestExplosion(new ArrayList<>(List.of(block)), 1F, true));
      host.types.put(block.location(), "AIR");
      runAll(scheduled);

      Assertions.assertTrue(host.clears.isEmpty());
      Assertions.assertTrue(host.drops.isEmpty());
    }
  }

  @Test
  void rejectedOwnerBatchRemainsInVanillaExplosionHandling() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock block = host.block(WORLD, 0, 64, 0, "STONE");
    List<ExplosionHost.ExplodedBlock> blocks = new ArrayList<>(List.of(block));

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      scheduler.when(() -> J.runChunk(Mockito.any(), Mockito.eq(0), Mockito.eq(0), Mockito.any(Runnable.class), Mockito.eq(1)))
          .thenReturn(false);

      feature.onExplode(new TestExplosion(blocks, 0F, true));

      Assertions.assertEquals(List.of(block), blocks);
      Assertions.assertDoesNotThrow(feature::onDeactivate);
    }
  }

  @Test
  void mainThreadDeactivationClaimsQueuedPaperBatchExactlyOnce() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock block = host.block(WORLD, 0, 64, 0, "STONE");
    List<ExplosionHost.ExplodedBlock> blocks = new ArrayList<>(List.of(block));
    List<ScheduledBatch> scheduled = new ArrayList<>();

    try (MockedStatic<ReactPlatforms> platforms = Mockito.mockStatic(ReactPlatforms.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      platforms.when(ReactPlatforms::getOrNull).thenReturn(Mockito.mock(ReactPlatform.class));
      captureScheduledBatches(scheduler, scheduled, false);
      scheduler.when(J::isPrimaryThread).thenReturn(true);

      feature.onExplode(new TestExplosion(blocks, 0F, true));
      Assertions.assertEquals(1, scheduled.size());
      Assertions.assertTrue(blocks.isEmpty());

      Assertions.assertDoesNotThrow(feature::onDeactivate);
      Assertions.assertEquals(List.of(block.location()), host.clears);

      scheduled.get(0).task().run();
      Assertions.assertEquals(List.of(block.location()), host.clears);
    }
  }

  @Test
  void stoppingFoliaDeactivationRunsQueuedBatchOnTheStoppingThread() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock block = host.block(WORLD, 0, 64, 0, "STONE");
    List<ExplosionHost.ExplodedBlock> blocks = new ArrayList<>(List.of(block));
    List<ScheduledBatch> scheduled = new ArrayList<>();

    ReactPlatform platform = platform(true);
    try (MockedStatic<ReactPlatforms> platforms = Mockito.mockStatic(ReactPlatforms.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      platforms.when(ReactPlatforms::getOrNull).thenReturn(platform);
      captureScheduledBatches(scheduler, scheduled, true);
      feature.onExplode(new TestExplosion(blocks, 0F, true));
      Assertions.assertEquals(1, scheduled.size());
      Assertions.assertTrue(blocks.isEmpty());

      scheduler.when(J::isServerStopping).thenReturn(true);
      long started = System.nanoTime();
      Assertions.assertDoesNotThrow(feature::onDeactivate);
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

      Assertions.assertTrue(elapsedMillis < 1_000L, "stop waited " + elapsedMillis + "ms on a halted scheduler");
      Assertions.assertEquals(1, scheduled.size());
      Assertions.assertEquals(List.of(block.location()), host.clears);

      scheduled.get(0).task().run();
      Assertions.assertEquals(List.of(block.location()), host.clears);
    }
  }

  @Test
  void stoppingFoliaDeactivationFailsAtOnceForBatchItCannotRun() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock block = host.block(WORLD, 0, 64, 0, "STONE");
    List<ScheduledBatch> scheduled = new ArrayList<>();

    ReactPlatform platform = platform(false);
    try (MockedStatic<ReactPlatforms> platforms = Mockito.mockStatic(ReactPlatforms.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      platforms.when(ReactPlatforms::getOrNull).thenReturn(platform);
      captureScheduledBatches(scheduler, scheduled, true);
      feature.onExplode(new TestExplosion(new ArrayList<>(List.of(block)), 0F, true));
      Assertions.assertEquals(1, scheduled.size());

      scheduler.when(J::isServerStopping).thenReturn(true);
      long started = System.nanoTime();
      Assertions.assertThrows(IllegalStateException.class, feature::onDeactivate);
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

      Assertions.assertTrue(elapsedMillis < 1_000L, "stop waited " + elapsedMillis + "ms on a halted scheduler");
      Assertions.assertEquals(1, scheduled.size());
    }
  }

  @Test
  void deactivationWaitsForAcceptedOwnerBatch() throws Exception {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock block = host.block(WORLD, 0, 64, 0, "STONE");
    List<ExplosionHost.ExplodedBlock> blocks = new ArrayList<>(List.of(block));
    List<ScheduledBatch> scheduled = new ArrayList<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      captureScheduledBatches(scheduler, scheduled, true);
      feature.onExplode(new TestExplosion(blocks, 0F, true));
      Assertions.assertEquals(1, scheduled.size());
      Assertions.assertTrue(blocks.isEmpty());

      Thread shutdown = new Thread(() -> {
        try {
          feature.onDeactivate();
        } catch (Throwable throwable) {
          failure.set(throwable);
        }
      });
      shutdown.start();
      awaitAccepting(feature, false);
      Assertions.assertTrue(shutdown.isAlive());

      scheduled.get(0).task().run();
      shutdown.join(2_000L);

      Assertions.assertFalse(shutdown.isAlive());
      Assertions.assertNull(failure.get());
      Assertions.assertEquals(List.of(block.location()), host.clears);
    }
  }

  @Test
  void deactivationTimeoutRetainsAcceptedOwnerBatchForRetry() throws Exception {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    setInt(feature, "shutdownDrainTimeoutMS", 10);
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock block = host.block(WORLD, 0, 64, 0, "STONE");
    List<ScheduledBatch> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      captureScheduledBatches(scheduler, scheduled, true);
      feature.onExplode(new TestExplosion(new ArrayList<>(List.of(block)), 0F, true));

      IllegalStateException timeout = Assertions.assertThrows(IllegalStateException.class, feature::onDeactivate);
      Assertions.assertTrue(timeout.getMessage().contains("1 unfinished owner batches"));

      scheduled.get(0).task().run();
      Assertions.assertDoesNotThrow(feature::onDeactivate);
      Assertions.assertEquals(List.of(block.location()), host.clears);
    }
  }

  @Test
  void pendingAdmissionCapsLeaveGlobalAndWorldOverflowToVanilla() throws Exception {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    setInt(feature, "maxPendingBlocksGlobal", 3);
    setInt(feature, "maxPendingBlocksPerWorld", 2);
    TestHost host = install(feature);
    List<ExplosionHost.ExplodedBlock> firstBlocks = new ArrayList<>(List.of(
        host.block(WORLD, 0, 64, 0, "STONE"),
        host.block(WORLD, 1, 64, 0, "STONE"),
        host.block(WORLD, 2, 64, 0, "STONE")
    ));
    List<ExplosionHost.ExplodedBlock> secondBlocks = new ArrayList<>(List.of(
        host.block(OTHER_WORLD, 0, 64, 0, "STONE"),
        host.block(OTHER_WORLD, 1, 64, 0, "STONE"),
        host.block(OTHER_WORLD, 2, 64, 0, "STONE")
    ));
    List<ScheduledBatch> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      captureScheduledBatches(scheduler, scheduled, true);

      feature.onExplode(new TestExplosion(firstBlocks, 0F, true));
      feature.onExplode(new TestExplosion(secondBlocks, 0F, true));

      Assertions.assertEquals(1, firstBlocks.size());
      Assertions.assertEquals(2, secondBlocks.size());
      Assertions.assertEquals(2, scheduled.size());
      Assertions.assertEquals(3, pendingBlockCount(feature));
    }
  }

  @Test
  void ownerSlicesAdvanceExactlyAndRetryOnlyFailedBlocks() throws Exception {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    setInt(feature, "maxPendingBlocksGlobal", 10);
    setInt(feature, "maxPendingBlocksPerWorld", 10);
    setInt(feature, "maxBlocksPerOwnerExecution", 2);
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock retry = host.block(WORLD, 2, 64, 0, "STONE");
    List<ExplosionHost.ExplodedBlock> blocks = new ArrayList<>(List.of(
        host.block(WORLD, 0, 64, 0, "STONE"),
        host.block(WORLD, 1, 64, 0, "STONE"),
        retry,
        host.block(WORLD, 3, 64, 0, "STONE"),
        host.block(WORLD, 4, 64, 0, "STONE")
    ));
    List<ScheduledBatch> scheduled = new ArrayList<>();
    AtomicInteger retryAttempts = new AtomicInteger(0);
    host.failClear = location -> location.equals(retry.location()) && retryAttempts.incrementAndGet() == 1;

    try (MockedStatic<ReactPlatforms> platforms = Mockito.mockStatic(ReactPlatforms.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      platforms.when(ReactPlatforms::getOrNull).thenReturn(null);
      captureScheduledBatches(scheduler, scheduled, true);

      feature.onExplode(new TestExplosion(blocks, 0F, true));
      Assertions.assertTrue(blocks.isEmpty());
      Assertions.assertEquals(5, pendingBlockCount(feature));
      Assertions.assertEquals(1, scheduled.size());

      scheduled.get(0).task().run();
      Assertions.assertEquals(3, pendingBlockCount(feature));

      feature.onTick();
      Assertions.assertEquals(2, scheduled.size());
      scheduled.get(1).task().run();
      Assertions.assertEquals(2, pendingBlockCount(feature));

      feature.onTick();
      Assertions.assertEquals(3, scheduled.size());
      scheduled.get(2).task().run();

      Assertions.assertEquals(0, pendingBlockCount(feature));
      Assertions.assertTrue(pendingBatches(feature).isEmpty());
      Assertions.assertEquals(2, retryAttempts.get());
    }
  }

  @Test
  void acceptedWorkCancelsWorldUnloadOnlyUntilItsOwnerBatchDrains() throws Exception {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TestHost host = install(feature);
    ExplosionHost.ExplodedBlock block = host.block(WORLD, 0, 64, 0, "STONE");
    List<ExplosionHost.ExplodedBlock> blocks = new ArrayList<>(List.of(block));
    List<ScheduledBatch> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      captureScheduledBatches(scheduler, scheduled, true);

      feature.onExplode(new TestExplosion(blocks, 0F, true));
      Assertions.assertTrue(blocks.isEmpty());
      Assertions.assertEquals(1, pendingBlockCount(feature));
      Assertions.assertEquals(1, scheduled.size());

      TestUnload firstUnload = new TestUnload(WORLD);
      feature.onWorldUnload(firstUnload);
      Assertions.assertTrue(firstUnload.cancelled);
      Assertions.assertEquals(1, scheduled.size());

      scheduled.get(0).task().run();
      Assertions.assertEquals(0, pendingBlockCount(feature));
      Assertions.assertTrue(pendingBatches(feature).isEmpty());
      Assertions.assertEquals(List.of(block.location()), host.clears);

      TestUnload secondUnload = new TestUnload(WORLD);
      feature.onWorldUnload(secondUnload);
      Assertions.assertFalse(secondUnload.cancelled);
    }
  }

  private static ReactPlatform platform(boolean ownsRegion) {
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    PlatformScheduler scheduler = Mockito.mock(PlatformScheduler.class);
    Mockito.when(platform.scheduler()).thenReturn(scheduler);
    Mockito.when(scheduler.isOwnedByCurrentRegion(Mockito.any(ReactWorld.class), Mockito.anyInt(), Mockito.anyInt()))
        .thenReturn(ownsRegion);
    return platform;
  }

  private static TestHost install(FeatureFastExplosions feature) {
    TestHost host = new TestHost();
    WorldHost worlds = Mockito.mock(WorldHost.class);
    Mockito.when(worlds.world(Mockito.any(UUID.class))).thenAnswer(invocation -> {
      ReactWorld world = Mockito.mock(ReactWorld.class);
      Mockito.when(world.id()).thenReturn(invocation.getArgument(0));
      return Optional.of(world);
    });
    try {
      setField(feature, "explosions", host);
      setField(feature, "worlds", worlds);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(failure);
    }
    return host;
  }

  private static ExplosionHost.ExplodedBlock block(UUID world, int x, int y, int z, String type) {
    return new ExplosionHost.ExplodedBlock(new GameLocation(world, x, y, z), type);
  }

  private static void runAll(List<ScheduledBatch> scheduled) {
    for (ScheduledBatch batch : new ArrayList<>(scheduled)) {
      batch.task().run();
    }
  }

  private static void captureScheduledBatches(MockedStatic<J> scheduler, List<ScheduledBatch> scheduled, boolean foliaThreading) {
    scheduler.when(J::isFoliaThreading).thenReturn(foliaThreading);
    scheduler.when(() -> J.runChunk(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class), Mockito.eq(1)))
        .thenAnswer(invocation -> {
          int chunkX = invocation.getArgument(1);
          Runnable task = invocation.getArgument(3);
          scheduled.add(new ScheduledBatch(chunkX, task));
          return true;
        });
  }

  private static void awaitAccepting(FeatureFastExplosions feature, boolean expected) throws ReflectiveOperationException {
    Field field = FeatureFastExplosions.class.getDeclaredField("accepting");
    field.setAccessible(true);
    long deadline = System.nanoTime() + 1_000_000_000L;
    while (field.getBoolean(feature) != expected && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    Assertions.assertEquals(expected, field.getBoolean(feature));
  }

  private static void setBoolean(FeatureFastExplosions feature, String fieldName, boolean value) throws ReflectiveOperationException {
    Field field = FeatureFastExplosions.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.setBoolean(feature, value);
  }

  private static void setInt(FeatureFastExplosions feature, String fieldName, int value) throws ReflectiveOperationException {
    Field field = FeatureFastExplosions.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.setInt(feature, value);
  }

  private static void setField(FeatureFastExplosions feature, String fieldName, Object value) throws ReflectiveOperationException {
    Field field = FeatureFastExplosions.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(feature, value);
  }

  private static int pendingBlockCount(FeatureFastExplosions feature) throws ReflectiveOperationException {
    Field field = FeatureFastExplosions.class.getDeclaredField("pendingBlockCount");
    field.setAccessible(true);
    return field.getInt(feature);
  }

  @SuppressWarnings("unchecked")
  private static Map<Long, ?> pendingBatches(FeatureFastExplosions feature) throws ReflectiveOperationException {
    Field field = FeatureFastExplosions.class.getDeclaredField("pendingBatches");
    field.setAccessible(true);
    return (Map<Long, ?>) field.get(feature);
  }

  private record ScheduledBatch(int chunkX, Runnable task) {
  }

  private static final class TestTnt implements ExplosionHost.PrimedTnt {
    private int fuse;

    private TestTnt(int fuse) {
      this.fuse = fuse;
    }

    @Override
    public int fuseTicks() {
      return fuse;
    }

    @Override
    public void setFuseTicks(int ticks) {
      fuse = ticks;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public void cancel() {
    }
  }

  private static final class TestUnload implements ChunkHost.WorldUnload {
    private final UUID world;
    private boolean cancelled;

    private TestUnload(UUID world) {
      this.world = world;
    }

    @Override
    public UUID worldId() {
      return world;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }

  private static final class TestExplosion implements ExplosionHost.Explosion {
    private final List<ExplosionHost.ExplodedBlock> blocks;
    private final float yield;
    private final boolean destroys;
    private boolean fromBlock;
    private boolean blocksRead;

    private TestExplosion(List<ExplosionHost.ExplodedBlock> blocks, float yield, boolean destroys) {
      this.blocks = blocks;
      this.yield = yield;
      this.destroys = destroys;
    }

    @Override
    public GameLocation location() {
      return new GameLocation(WORLD, 0, 64, 0);
    }

    @Override
    public boolean fromBlock() {
      return fromBlock;
    }

    @Override
    public boolean destroysBlocks() {
      return destroys;
    }

    @Override
    public float yield() {
      return yield;
    }

    @Override
    public List<ExplosionHost.ExplodedBlock> blocks() {
      blocksRead = true;
      return new ArrayList<>(blocks);
    }

    @Override
    public void removeBlocks(Predicate<ExplosionHost.ExplodedBlock> filter) {
      blocks.removeIf(filter);
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public void cancel() {
    }
  }

  private static final class TestHost implements ExplosionHost {
    private final Map<GameLocation, String> types = new HashMap<>();
    private final List<GameLocation> typeReads = new ArrayList<>();
    private final List<GameLocation> clears = new ArrayList<>();
    private final List<Boolean> clearModes = new ArrayList<>();
    private final List<GameLocation> drops = new ArrayList<>();
    private final List<GameLocation> explosions = new ArrayList<>();
    private Predicate<GameLocation> failClear = location -> false;

    private ExplodedBlock block(UUID world, int x, int y, int z, String type) {
      ExplodedBlock block = FeatureFastExplosionsTest.block(world, x, y, z, type);
      types.put(block.location(), type);
      return block;
    }

    private int fastClears() {
      int fast = 0;
      for (boolean mode : clearModes) {
        if (mode) {
          fast++;
        }
      }
      return fast;
    }

    @Override
    public Subscription onExplode(art.arcane.react.platform.ListenerPriority priority, boolean ignoreCancelled, Consumer<Explosion> listener) {
      return Subscription.NONE;
    }

    @Override
    public Subscription onTntPrimed(art.arcane.react.platform.ListenerPriority priority, boolean ignoreCancelled, Consumer<PrimedTnt> listener) {
      return Subscription.NONE;
    }


    @Override
    public String blockType(GameLocation location) {
      typeReads.add(location);
      return types.get(location);
    }

    @Override
    public void dropContents(GameLocation location) {
      drops.add(location);
    }

    @Override
    public void clear(GameLocation location, boolean fastBlockUpdates) {
      if (failClear.test(location)) {
        throw new IllegalStateException("retry this block");
      }
      clears.add(location);
      clearModes.add(fastBlockUpdates);
    }

    @Override
    public void explode(GameLocation location, float power) {
      Assertions.assertEquals(4F, power);
      explosions.add(location);
    }

    @Override
    public void installPacketHooks(PacketHooks hooks) {
    }

    @Override
    public void uninstallPacketHooks() {
    }

    @Override
    public boolean canBroadcast() {
      return false;
    }

    @Override
    public WorldState worldState(UUID worldId) {
      return WorldState.UNKNOWN;
    }

    @Override
    public boolean broadcastMergedExplosion(UUID worldId, double x, double y, double z, float radius, double rangeBlocks) {
      return false;
    }
  }
}
