package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.ObserverController.LoadedChunkCursor;
import art.arcane.react.core.controller.ObserverController.LoadedChunkTarget;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class MobStackRecoveryTest {
  private React previous;
  private FeatureMobStacking feature;
  private MobStackRecovery recovery;
  private ObserverController observer;
  private LoadedChunkCursor cursor;
  private World world;
  private UUID worldId;
  private Chunk chunk;
  private List<Runnable> tasks;
  private MockedStatic<React> react;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<J> scheduling;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    React.instance = plugin;
    Mockito.when(plugin.isEnabled()).thenReturn(true);
    Mockito.when(plugin.isReady()).thenReturn(true);
    feature = Mockito.spy(new FeatureMobStacking());
    feature.setEnabled(false);
    recovery = new MobStackRecovery(feature);
    observer = Mockito.mock(ObserverController.class);
    cursor = Mockito.mock(LoadedChunkCursor.class);
    world = Mockito.mock(World.class);
    chunk = Mockito.mock(Chunk.class);
    tasks = new ArrayList<>();
    worldId = UUID.randomUUID();
    Mockito.when(observer.isLoadedChunkCoordinateIndexReady()).thenReturn(true);
    Mockito.when(observer.openLoadedChunkCursor()).thenReturn(cursor);
    Mockito.when(cursor.next(1)).thenReturn(List.of(new LoadedChunkTarget(worldId, 7, -4)), List.of());
    Mockito.when(world.isChunkLoaded(7, -4)).thenReturn(true);
    Mockito.when(world.getChunkAt(7, -4, false)).thenReturn(chunk);
    Mockito.when(chunk.isEntitiesLoaded()).thenReturn(true);
    Mockito.when(chunk.getEntities()).thenReturn(new Entity[0]);
    Mockito.doReturn(0).when(feature).restoreStack(Mockito.any(LivingEntity.class), Mockito.anyInt(), Mockito.anyLong());
    Mockito.doReturn(1).when(feature).getStackCount(Mockito.any(Entity.class));
    react = Mockito.mockStatic(React.class);
    bukkit = Mockito.mockStatic(Bukkit.class);
    scheduling = Mockito.mockStatic(J.class);
    react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
    bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
    scheduling.when(J::isFoliaThreading).thenReturn(true);
    scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
    scheduleSuccessfully();
  }

  @AfterEach
  void tearDown() {
    scheduling.close();
    bukkit.close();
    react.close();
    React.instance = previous;
  }

  @Test
  void disabledStartupRecoversLoadedFoliaChunksWithoutPlayersOrConfiguredTypes() throws ReflectiveOperationException {
    LivingEntity source = livingEntity();
    Field types = FeatureMobStacking.class.getDeclaredField("stackableTypes");
    types.setAccessible(true);
    types.set(feature, Set.of());
    Mockito.when(chunk.getEntities()).thenReturn(new Entity[]{source});

    recovery.tick(0L);

    Assertions.assertEquals(1, tasks.size());
    Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
    Mockito.verify(source, Mockito.never()).isValid();
    tasks.getFirst().run();

    Mockito.verify(feature).restoreStack(source, MobStackRecovery.MAX_SPAWNS_PER_BATCH, 0L);
    Mockito.verify(feature, Mockito.never()).onActivate();
    bukkit.verify(Bukkit::getOnlinePlayers, Mockito.never());
    Mockito.verify(world, Mockito.never()).getPlayers();
  }

  @Test
  void pendingChunkPreventsDuplicateCallbacksUntilItFinishes() {
    recovery.tick(0L);
    recovery.tick(0L);

    Assertions.assertEquals(1, tasks.size());
    Mockito.verify(cursor).next(1);
    tasks.getFirst().run();
    recovery.tick(0L);

    Mockito.verify(cursor, Mockito.times(2)).next(1);
  }

  @Test
  void spawnBudgetIsSharedAcrossStacksAndResumesTheRemainingEntities() {
    LivingEntity first = livingEntity();
    LivingEntity second = livingEntity();
    LivingEntity third = livingEntity();
    Mockito.when(chunk.getEntities()).thenReturn(new Entity[]{first, second, third});
    Mockito.doReturn(7).when(feature).restoreStack(first, 16, 0L);
    Mockito.doReturn(9).when(feature).restoreStack(second, 9, 0L);

    recovery.tick(0L);
    tasks.getFirst().run();

    Mockito.verify(feature).restoreStack(first, 16, 0L);
    Mockito.verify(feature).restoreStack(second, 9, 0L);
    Mockito.verify(feature, Mockito.never()).restoreStack(Mockito.eq(third), Mockito.anyInt(), Mockito.anyLong());
    recovery.tick(0L);
    tasks.getLast().run();

    Mockito.verify(feature).restoreStack(third, 16, 0L);
    Mockito.verify(chunk).getEntities();
  }

  @Test
  void stackLargerThanTheSpawnBudgetResumesBeforeAdvancingToTheNextEntity() {
    LivingEntity source = livingEntity();
    LivingEntity next = livingEntity();
    Mockito.when(chunk.getEntities()).thenReturn(new Entity[]{source, next});
    Mockito.doReturn(16, 1).when(feature).restoreStack(source, 16, 0L);
    Mockito.doReturn(2).when(feature).getStackCount(source);

    recovery.tick(0L);
    tasks.getFirst().run();
    Mockito.verify(feature, Mockito.never()).restoreStack(Mockito.eq(next), Mockito.anyInt(), Mockito.anyLong());
    recovery.tick(0L);
    tasks.getLast().run();

    Mockito.verify(feature, Mockito.times(2)).restoreStack(source, 16, 0L);
    Mockito.verify(feature).restoreStack(next, 15, 0L);
  }

  @Test
  void denseChunkInspectionIsBoundedAndContinuesOnTheNextCallback() {
    LivingEntity source = livingEntity();
    Entity[] entities = new Entity[MobStackRecovery.MAX_ENTITIES_PER_BATCH + 1];
    Arrays.fill(entities, source);
    Mockito.when(chunk.getEntities()).thenReturn(entities);

    recovery.tick(0L);
    tasks.getFirst().run();

    Mockito.verify(feature, Mockito.times(MobStackRecovery.MAX_ENTITIES_PER_BATCH))
        .restoreStack(source, 16, 0L);
    recovery.tick(0L);
    tasks.getLast().run();

    Mockito.verify(feature, Mockito.times(entities.length)).restoreStack(source, 16, 0L);
    Mockito.verify(chunk).getEntities();
  }

  @Test
  void reenabledFeatureRejectsItsQueuedRecoveryBeforeReadingTheChunk() {
    recovery.tick(0L);
    feature.setEnabled(true);
    tasks.getFirst().run();

    Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
    Mockito.verify(feature, Mockito.never()).restoreStack(Mockito.any(LivingEntity.class), Mockito.anyInt(), Mockito.anyLong());
  }

  @Test
  void oldGenerationCannotRecoverAndANewGenerationStartsAFreshSweep() {
    LivingEntity source = livingEntity();
    Mockito.when(chunk.getEntities()).thenReturn(new Entity[]{source});
    recovery.tick(0L);
    feature.onDeactivate();
    tasks.getFirst().run();

    Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
    Mockito.when(cursor.next(1)).thenReturn(List.of(new LoadedChunkTarget(worldId, 7, -4)));
    recovery.tick(1L);
    tasks.getLast().run();

    Mockito.verify(observer, Mockito.times(2)).openLoadedChunkCursor();
    Mockito.verify(feature).restoreStack(source, 16, 1L);
    Mockito.verify(feature, Mockito.never()).restoreStack(Mockito.any(LivingEntity.class), Mockito.anyInt(), Mockito.eq(0L));
  }

  @Test
  void rejectedSchedulingRetriesTheSameChunkOnTheNextTick() {
    AtomicInteger attempts = new AtomicInteger();
    scheduling.when(() -> J.runChunk(Mockito.eq(world), Mockito.eq(7), Mockito.eq(-4),
        Mockito.any(Runnable.class), Mockito.eq(1))).thenAnswer(invocation -> {
      if (attempts.getAndIncrement() == 0) {
        return false;
      }
      tasks.add(invocation.getArgument(3));
      return true;
    });

    recovery.tick(0L);
    Assertions.assertTrue(tasks.isEmpty());
    recovery.tick(0L);

    Assertions.assertEquals(2, attempts.get());
    Assertions.assertEquals(1, tasks.size());
    Mockito.verify(cursor).next(1);
  }

  @Test
  void recoverySkipsEntitiesOwnedByAnotherRegion() {
    LivingEntity source = livingEntity();
    Mockito.when(chunk.getEntities()).thenReturn(new Entity[]{source});
    scheduling.when(() -> J.isOwnedByCurrentRegion(source)).thenReturn(false);

    recovery.tick(0L);
    tasks.getFirst().run();

    Mockito.verify(feature, Mockito.never()).restoreStack(Mockito.any(LivingEntity.class), Mockito.anyInt(), Mockito.anyLong());
    Mockito.verify(source, Mockito.never()).isValid();
    Mockito.verify(source, Mockito.never()).isDead();
  }

  @Test
  void deferredRecoveryDoesNotLoadAChunkThatUnloadedBeforeExecution() {
    recovery.tick(0L);
    Mockito.when(world.isChunkLoaded(7, -4)).thenReturn(false);
    tasks.getFirst().run();

    Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
    Mockito.verify(chunk, Mockito.never()).getEntities();
  }

  @Test
  void chunkFailureReportsTheExceptionAndReleasesPendingWork() {
    RuntimeException failure = new IllegalStateException("chunk access failed");
    Mockito.when(world.isChunkLoaded(7, -4)).thenThrow(failure);

    recovery.tick(0L);
    Assertions.assertDoesNotThrow(tasks.getFirst()::run);
    recovery.tick(0L);

    react.verify(() -> React.warn("Could not restore a mob stack; its remaining count will be retried.", failure));
    Mockito.verify(cursor, Mockito.times(2)).next(1);
  }

  private LivingEntity livingEntity() {
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    Mockito.when(entity.isValid()).thenReturn(true);
    return entity;
  }

  private void scheduleSuccessfully() {
    scheduling.when(() -> J.runChunk(Mockito.eq(world), Mockito.eq(7), Mockito.eq(-4),
        Mockito.any(Runnable.class), Mockito.eq(1))).thenAnswer(invocation -> {
      tasks.add(invocation.getArgument(3));
      return true;
    });
  }
}
