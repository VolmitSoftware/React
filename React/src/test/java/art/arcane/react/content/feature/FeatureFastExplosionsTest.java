package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.data.B;
import art.arcane.react.util.project.world.FastWorld;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class FeatureFastExplosionsTest {
  @Test
  void nonTntSpawnsDoNotAdvanceTheFuseSpread() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    Entity entity = Mockito.mock(Entity.class);
    TNTPrimed tnt = Mockito.mock(TNTPrimed.class);
    Mockito.when(tnt.getFuseTicks()).thenReturn(80);

    feature.on(spawnEvent(entity));
    feature.on(spawnEvent(tnt));

    Mockito.verify(tnt).setFuseTicks(80);
  }

  @Test
  void tntSpawnsReceiveSequentialFuseSpread() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TNTPrimed first = Mockito.mock(TNTPrimed.class);
    TNTPrimed second = Mockito.mock(TNTPrimed.class);
    Mockito.when(first.getFuseTicks()).thenReturn(80);
    Mockito.when(second.getFuseTicks()).thenReturn(80);

    feature.on(spawnEvent(first));
    feature.on(spawnEvent(second));

    Mockito.verify(first).setFuseTicks(80);
    Mockito.verify(second).setFuseTicks(87);
  }

  @Test
  void tickingStartsANewFuseSpreadWindow() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    TNTPrimed first = Mockito.mock(TNTPrimed.class);
    TNTPrimed nextWindow = Mockito.mock(TNTPrimed.class);
    Mockito.when(first.getFuseTicks()).thenReturn(80);
    Mockito.when(nextWindow.getFuseTicks()).thenReturn(80);

    feature.on(spawnEvent(first));
    feature.onTick();
    feature.on(spawnEvent(nextWindow));

    Mockito.verify(nextWindow).setFuseTicks(80);
  }

  @Test
  void destructiveEntityExplosionResultsUseTheFastBlockPath() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    for (ExplosionResult result : List.of(
        ExplosionResult.DESTROY,
        ExplosionResult.DESTROY_WITH_DECAY
    )) {
      EntityExplodeEvent event = explosionEvent(EntityType.CREEPER, new ArrayList<>(), 0F, result);

      feature.on(event);

      Mockito.verify(event).blockList();
      Mockito.verify(event).getYield();
    }
  }

  @Test
  void nonDestructiveEntityExplosionResultsStayOutOfTheFastBlockPath() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    World world = Mockito.mock(World.class);
    Block block = block(world, 0, 64, 0, Material.STONE);
    for (ExplosionResult result : List.of(
        ExplosionResult.KEEP,
        ExplosionResult.TRIGGER_BLOCK
    )) {
      List<Block> blocks = new ArrayList<>(List.of(block));
      EntityExplodeEvent event = explosionEvent(EntityType.CREEPER, blocks, 0F, result);

      feature.on(event);

      Assertions.assertEquals(List.of(block), blocks);
      Mockito.verify(event, Mockito.never()).blockList();
      Mockito.verify(event, Mockito.never()).getYield();
    }
  }

  @Test
  void counterBudgetsResetOnThePerTickCadence() {
    FeatureFastExplosions feature = new FeatureFastExplosions();

    Assertions.assertEquals(50, feature.getTickInterval());
  }

  @Test
  void affectedBlocksScheduleOneTaskPerOwningChunk() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    World world = Mockito.mock(World.class);
    Block first = block(world, 0, 64, 0, Material.TNT);
    Block sameChunk = block(world, 15, 64, 0, Material.TNT);
    Block nextChunk = block(world, 16, 64, 0, Material.TNT);
    Block negativeChunk = block(world, -1, 64, 0, Material.TNT);
    List<Block> blocks = new ArrayList<>(List.of(first, sameChunk, nextChunk, negativeChunk));
    EntityExplodeEvent event = explosionEvent(EntityType.TNT, blocks, 0F);
    List<ScheduledBatch> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      captureScheduledBatches(scheduler, scheduled, true);

      feature.on(event);

      Assertions.assertEquals(3, scheduled.size());
      Assertions.assertEquals(0, scheduled.get(0).anchor().getBlockX() >> 4);
      Assertions.assertEquals(1, scheduled.get(1).anchor().getBlockX() >> 4);
      Assertions.assertEquals(-1, scheduled.get(2).anchor().getBlockX() >> 4);

      scheduled.get(0).task().run();
      Mockito.verify(world).getBlockAt(first.getLocation());
      Mockito.verify(world).getBlockAt(sameChunk.getLocation());
      Mockito.verify(world, Mockito.never()).getBlockAt(nextChunk.getLocation());
      scheduled.get(1).task().run();
      scheduled.get(2).task().run();
      Mockito.verify(world).getBlockAt(nextChunk.getLocation());
      Mockito.verify(world).getBlockAt(negativeChunk.getLocation());
    }
  }

  @Test
  void foliaChunkExecutionOrderCannotChangeGlobalChainPermits() throws ReflectiveOperationException {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    setBoolean(feature, "explosionChainReactions", true);
    setInt(feature, "maxPrimesPerTick", 0);
    setInt(feature, "maxExplosionChainsPerTick", 2);
    World world = Mockito.mock(World.class);
    Block first = block(world, 0, 64, 0, Material.TNT);
    Block second = block(world, 16, 64, 0, Material.TNT);
    Block third = block(world, 1, 64, 0, Material.TNT);
    Block fourth = block(world, 17, 64, 0, Material.TNT);
    List<Block> blocks = new ArrayList<>(List.of(first, second, third, fourth));
    EntityExplodeEvent event = explosionEvent(EntityType.TNT, blocks, 0F);
    List<ScheduledBatch> scheduled = new ArrayList<>();
    BlockData air = Mockito.mock(BlockData.class);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
      try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
           MockedStatic<B> blockData = Mockito.mockStatic(B.class);
           MockedStatic<FastWorld> fastWorld = Mockito.mockStatic(FastWorld.class)) {
        captureScheduledBatches(scheduler, scheduled, true);
        blockData.when(B::getAir).thenReturn(air);

        feature.on(event);
        for (ScheduledBatch batch : scheduled) {
          batch.task().run();
        }

        Assertions.assertEquals(2, scheduled.size());
        Assertions.assertTrue(blocks.isEmpty());
        fastWorld.verify(
            () -> FastWorld.set(Mockito.any(Block.class), Mockito.same(air), Mockito.eq(true)),
            Mockito.times(4));
        Mockito.verify(world).createExplosion(first.getLocation(), 4F, false, true);
        Mockito.verify(world).createExplosion(second.getLocation(), 4F, false, true);
        Mockito.verify(world, Mockito.never()).createExplosion(third.getLocation(), 4F, false, true);
        Mockito.verify(world, Mockito.never()).createExplosion(fourth.getLocation(), 4F, false, true);
      }
    }
  }

  @Test
  void batchedBlocksPreserveDropsAndRemoval() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    World world = Mockito.mock(World.class);
    Block first = block(world, 0, 64, 0, Material.STONE);
    Block second = block(world, 1, 64, 0, Material.STONE);
    BlockState firstState = Mockito.mock(BlockState.class);
    BlockState secondState = Mockito.mock(BlockState.class);
    ItemStack firstDrop = Mockito.mock(ItemStack.class);
    ItemStack secondDrop = Mockito.mock(ItemStack.class);
    Mockito.when(first.getState()).thenReturn(firstState);
    Mockito.when(second.getState()).thenReturn(secondState);
    Mockito.when(first.getDrops(null)).thenReturn(List.of(firstDrop));
    Mockito.when(second.getDrops(null)).thenReturn(List.of(secondDrop));
    List<Block> blocks = new ArrayList<>(List.of(first, second));
    EntityExplodeEvent event = explosionEvent(EntityType.TNT, blocks, 1F);
    List<ScheduledBatch> scheduled = new ArrayList<>();
    BlockData air = Mockito.mock(BlockData.class);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
      try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
           MockedStatic<B> blockData = Mockito.mockStatic(B.class);
           MockedStatic<FastWorld> fastWorld = Mockito.mockStatic(FastWorld.class)) {
        captureScheduledBatches(scheduler, scheduled, true);
        blockData.when(B::getAir).thenReturn(air);

        feature.on(event);
        Assertions.assertEquals(1, scheduled.size());
        scheduled.getFirst().task().run();

        Mockito.verify(world).dropItemNaturally(first.getLocation(), firstDrop);
        Mockito.verify(world).dropItemNaturally(second.getLocation(), secondDrop);
        fastWorld.verify(
            () -> FastWorld.set(Mockito.any(Block.class), Mockito.same(air), Mockito.eq(true)),
            Mockito.times(2));
      }
    }
  }

  @Test
  void rejectedOwnerBatchRemainsInVanillaExplosionHandling() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    World world = Mockito.mock(World.class);
    Block block = block(world, 0, 64, 0, Material.STONE);
    List<Block> blocks = new ArrayList<>(List.of(block));
    EntityExplodeEvent event = explosionEvent(EntityType.TNT, blocks, 0F);

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      scheduler.when(J::isFoliaThreading).thenReturn(true);
      scheduler.when(() -> J.runChunk(
          Mockito.same(world),
          Mockito.eq(0),
          Mockito.eq(0),
          Mockito.any(Runnable.class),
          Mockito.eq(1)
      )).thenReturn(false);

      feature.on(event);

      Assertions.assertEquals(List.of(block), blocks);
      Assertions.assertDoesNotThrow(feature::onDeactivate);
    }
  }

  @Test
  void mainThreadDeactivationClaimsQueuedPaperBatchExactlyOnce() {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    World world = Mockito.mock(World.class);
    Block block = block(world, 0, 64, 0, Material.STONE);
    List<Block> blocks = new ArrayList<>(List.of(block));
    EntityExplodeEvent event = explosionEvent(EntityType.TNT, blocks, 0F);
    List<ScheduledBatch> scheduled = new ArrayList<>();
    BlockData air = Mockito.mock(BlockData.class);
    Server server = Mockito.mock(Server.class);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
      bukkit.when(Bukkit::getServer).thenReturn(server);
      try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
           MockedStatic<B> blockData = Mockito.mockStatic(B.class);
           MockedStatic<FastWorld> fastWorld = Mockito.mockStatic(FastWorld.class)) {
        scheduler.when(J::isFoliaThreading).thenReturn(false);
        scheduler.when(J::isPrimaryThread).thenReturn(true);
        scheduler.when(() -> J.runChunk(
                Mockito.same(world),
                Mockito.eq(0),
                Mockito.eq(0),
                Mockito.any(Runnable.class),
                Mockito.eq(1)))
            .thenAnswer(invocation -> {
              scheduled.add(new ScheduledBatch(new Location(world, 0, 0, 0), invocation.getArgument(3)));
              return true;
            });
        blockData.when(B::getAir).thenReturn(air);

        feature.on(event);
        Assertions.assertEquals(1, scheduled.size());
        Assertions.assertTrue(blocks.isEmpty());

        Assertions.assertDoesNotThrow(feature::onDeactivate);
        fastWorld.verify(() -> FastWorld.set(block, air, true), Mockito.times(1));

        scheduled.getFirst().task().run();
        fastWorld.verify(() -> FastWorld.set(block, air, true), Mockito.times(1));
      }
    }
  }

  @Test
  void deactivationWaitsForAcceptedOwnerBatch() throws Exception {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    World world = Mockito.mock(World.class);
    Block block = block(world, 0, 64, 0, Material.STONE);
    List<Block> blocks = new ArrayList<>(List.of(block));
    EntityExplodeEvent event = explosionEvent(EntityType.TNT, blocks, 0F);
    List<ScheduledBatch> scheduled = new ArrayList<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    BlockData air = Mockito.mock(BlockData.class);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
      try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
           MockedStatic<B> blockData = Mockito.mockStatic(B.class);
           MockedStatic<FastWorld> fastWorld = Mockito.mockStatic(FastWorld.class)) {
        captureScheduledBatches(scheduler, scheduled, true);
        blockData.when(B::getAir).thenReturn(air);
        feature.on(event);
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

        scheduled.getFirst().task().run();
        shutdown.join(2_000L);

        Assertions.assertFalse(shutdown.isAlive());
        Assertions.assertNull(failure.get());
        fastWorld.verify(() -> FastWorld.set(block, air, true));
      }
    }
  }

  @Test
  void deactivationTimeoutRetainsAcceptedOwnerBatchForRetry() throws Exception {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    setInt(feature, "shutdownDrainTimeoutMS", 10);
    World world = Mockito.mock(World.class);
    Block block = block(world, 0, 64, 0, Material.STONE);
    List<Block> blocks = new ArrayList<>(List.of(block));
    EntityExplodeEvent event = explosionEvent(EntityType.TNT, blocks, 0F);
    List<ScheduledBatch> scheduled = new ArrayList<>();
    BlockData air = Mockito.mock(BlockData.class);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
      try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
           MockedStatic<B> blockData = Mockito.mockStatic(B.class);
           MockedStatic<FastWorld> fastWorld = Mockito.mockStatic(FastWorld.class)) {
        captureScheduledBatches(scheduler, scheduled, true);
        blockData.when(B::getAir).thenReturn(air);
        feature.on(event);

        IllegalStateException timeout = Assertions.assertThrows(
            IllegalStateException.class,
            feature::onDeactivate
        );
        Assertions.assertTrue(timeout.getMessage().contains("1 unfinished owner batches"));

        scheduled.getFirst().task().run();
        Assertions.assertDoesNotThrow(feature::onDeactivate);
        fastWorld.verify(() -> FastWorld.set(block, air, true));
      }
    }
  }

  @Test
  void pendingAdmissionCapsLeaveGlobalAndWorldOverflowToVanilla() throws Exception {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    setInt(feature, "maxPendingBlocksGlobal", 3);
    setInt(feature, "maxPendingBlocksPerWorld", 2);
    World firstWorld = Mockito.mock(World.class);
    World secondWorld = Mockito.mock(World.class);
    List<Block> firstBlocks = new ArrayList<>(List.of(
        block(firstWorld, 0, 64, 0, Material.STONE),
        block(firstWorld, 1, 64, 0, Material.STONE),
        block(firstWorld, 2, 64, 0, Material.STONE)
    ));
    List<Block> secondBlocks = new ArrayList<>(List.of(
        block(secondWorld, 0, 64, 0, Material.STONE),
        block(secondWorld, 1, 64, 0, Material.STONE),
        block(secondWorld, 2, 64, 0, Material.STONE)
    ));
    List<ScheduledBatch> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      captureScheduledBatches(scheduler, scheduled, true);

      feature.on(explosionEvent(EntityType.TNT, firstBlocks, 0F));
      feature.on(explosionEvent(EntityType.TNT, secondBlocks, 0F));

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
    World world = Mockito.mock(World.class);
    Block first = block(world, 0, 64, 0, Material.STONE);
    Block second = block(world, 1, 64, 0, Material.STONE);
    Block retry = block(world, 2, 64, 0, Material.STONE);
    Block fourth = block(world, 3, 64, 0, Material.STONE);
    Block fifth = block(world, 4, 64, 0, Material.STONE);
    List<Block> blocks = new ArrayList<>(List.of(first, second, retry, fourth, fifth));
    List<ScheduledBatch> scheduled = new ArrayList<>();
    BlockData air = Mockito.mock(BlockData.class);
    AtomicInteger retryAttempts = new AtomicInteger(0);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
      try (MockedStatic<React> react = Mockito.mockStatic(React.class);
           MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
           MockedStatic<B> blockData = Mockito.mockStatic(B.class);
           MockedStatic<FastWorld> fastWorld = Mockito.mockStatic(FastWorld.class)) {
        captureScheduledBatches(scheduler, scheduled, true);
        blockData.when(B::getAir).thenReturn(air);
        fastWorld.when(() -> FastWorld.set(retry, air, true)).thenAnswer(invocation -> {
          if (retryAttempts.incrementAndGet() == 1) {
            throw new IllegalStateException("retry this block");
          }
          return null;
        });

        feature.on(explosionEvent(EntityType.TNT, blocks, 0F));
        Assertions.assertTrue(blocks.isEmpty());
        Assertions.assertEquals(5, pendingBlockCount(feature));
        Assertions.assertEquals(1, scheduled.size());

        scheduled.getFirst().task().run();
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
  }

  @Test
  void acceptedWorkCancelsWorldUnloadOnlyUntilItsOwnerBatchDrains() throws Exception {
    FeatureFastExplosions feature = new FeatureFastExplosions();
    World world = Mockito.mock(World.class);
    Block block = block(world, 0, 64, 0, Material.STONE);
    List<Block> blocks = new ArrayList<>(List.of(block));
    List<ScheduledBatch> scheduled = new ArrayList<>();
    WorldUnloadEvent firstUnload = new WorldUnloadEvent(world);
    BlockData air = Mockito.mock(BlockData.class);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
      try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
           MockedStatic<B> blockData = Mockito.mockStatic(B.class);
           MockedStatic<FastWorld> fastWorld = Mockito.mockStatic(FastWorld.class)) {
        captureScheduledBatches(scheduler, scheduled, true);
        blockData.when(B::getAir).thenReturn(air);

        feature.on(explosionEvent(EntityType.TNT, blocks, 0F));
        Assertions.assertTrue(blocks.isEmpty());
        Assertions.assertEquals(1, pendingBlockCount(feature));
        Assertions.assertEquals(1, scheduled.size());

        feature.on(firstUnload);
        Assertions.assertTrue(firstUnload.isCancelled());
        Assertions.assertEquals(1, scheduled.size());

        scheduled.getFirst().task().run();
        Assertions.assertEquals(0, pendingBlockCount(feature));
        Assertions.assertTrue(pendingBatches(feature).isEmpty());
        fastWorld.verify(() -> FastWorld.set(block, air, true));

        WorldUnloadEvent secondUnload = new WorldUnloadEvent(world);
        feature.on(secondUnload);
        Assertions.assertFalse(secondUnload.isCancelled());
      }
    }
  }

  private EntitySpawnEvent spawnEvent(Entity entity) {
    EntitySpawnEvent event = Mockito.mock(EntitySpawnEvent.class);
    Mockito.when(event.getEntity()).thenReturn(entity);
    return event;
  }

  private EntityExplodeEvent explosionEvent(EntityType entityType, List<Block> blocks, float yield) {
    return explosionEvent(entityType, blocks, yield, ExplosionResult.DESTROY_WITH_DECAY);
  }

  private EntityExplodeEvent explosionEvent(
      EntityType entityType,
      List<Block> blocks,
      float yield,
      ExplosionResult result
  ) {
    EntityExplodeEvent event = Mockito.mock(EntityExplodeEvent.class);
    Mockito.when(event.getEntityType()).thenReturn(entityType);
    Mockito.when(event.blockList()).thenReturn(blocks);
    Mockito.when(event.getYield()).thenReturn(yield);
    Mockito.when(event.getExplosionResult()).thenReturn(result);
    return event;
  }

  private Block block(World world, int x, int y, int z, Material material) {
    Block block = Mockito.mock(Block.class);
    Location location = new Location(world, x, y, z);
    Mockito.when(block.getLocation()).thenReturn(location);
    Mockito.when(block.getWorld()).thenReturn(world);
    Mockito.when(block.getType()).thenReturn(material);
    Mockito.when(world.getBlockAt(location)).thenReturn(block);
    return block;
  }

  private void captureScheduledBatches(
      MockedStatic<J> scheduler, List<ScheduledBatch> scheduled, boolean foliaThreading) {
    scheduler.when(J::isFoliaThreading).thenReturn(foliaThreading);
    scheduler.when(() -> J.runChunk(
            Mockito.any(World.class),
            Mockito.anyInt(),
            Mockito.anyInt(),
            Mockito.any(Runnable.class),
            Mockito.eq(1)))
        .thenAnswer(invocation -> {
          World world = invocation.getArgument(0);
          int chunkX = invocation.getArgument(1);
          int chunkZ = invocation.getArgument(2);
          Runnable task = invocation.getArgument(3);
          scheduled.add(new ScheduledBatch(new Location(world, chunkX << 4, 0, chunkZ << 4), task));
          return true;
        });
  }

  private void awaitAccepting(FeatureFastExplosions feature, boolean expected)
      throws ReflectiveOperationException {
    Field field = FeatureFastExplosions.class.getDeclaredField("accepting");
    field.setAccessible(true);
    long deadline = System.nanoTime() + 1_000_000_000L;
    while (field.getBoolean(feature) != expected && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    Assertions.assertEquals(expected, field.getBoolean(feature));
  }

  private void setBoolean(FeatureFastExplosions feature, String fieldName, boolean value)
      throws ReflectiveOperationException {
    Field field = FeatureFastExplosions.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.setBoolean(feature, value);
  }

  private void setInt(FeatureFastExplosions feature, String fieldName, int value)
      throws ReflectiveOperationException {
    Field field = FeatureFastExplosions.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.setInt(feature, value);
  }

  private int pendingBlockCount(FeatureFastExplosions feature) throws ReflectiveOperationException {
    Field field = FeatureFastExplosions.class.getDeclaredField("pendingBlockCount");
    field.setAccessible(true);
    return field.getInt(feature);
  }

  @SuppressWarnings("unchecked")
  private Map<Long, Object> pendingBatches(FeatureFastExplosions feature) throws ReflectiveOperationException {
    Field field = FeatureFastExplosions.class.getDeclaredField("pendingBatches");
    field.setAccessible(true);
    return (Map<Long, Object>) field.get(feature);
  }

  private record ScheduledBatch(Location anchor, Runnable task) {
  }
}
