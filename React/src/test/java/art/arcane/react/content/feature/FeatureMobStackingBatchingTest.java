package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.protect.ReactProtection;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class FeatureMobStackingBatchingTest {
  @Test
  void foliaBurstKeepsAtMostSixtyFourDistinctChunksInFlight() throws ReflectiveOperationException {
    FeatureMobStacking feature = activeFeature();
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Set<Long> pending = pending(feature, worldId);
    for (int chunkX = 0; chunkX < 200; chunkX++) {
      pending.add(FeatureMobStacking.packChunkKey(chunkX, 0));
    }
    List<Long> submissions = new ArrayList<>();
    List<Runnable> tasks = new ArrayList<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runChunk(
              Mockito.eq(world),
              Mockito.anyInt(),
              Mockito.anyInt(),
              Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            submissions.add(FeatureMobStacking.packChunkKey(
                invocation.getArgument(1),
                invocation.getArgument(2)
            ));
            tasks.add(invocation.getArgument(3));
            return true;
          });

      feature.onTick();
      Assertions.assertEquals(64, submissions.size());
      feature.onTick();
      Assertions.assertEquals(64, submissions.size());

      tasks.getFirst().run();
      feature.onTick();
      Assertions.assertEquals(65, submissions.size());
    }

    Assertions.assertEquals(65, new HashSet<>(submissions).size());
  }

  @Test
  void pendingInspectionStopsAfterTheHardLimitWhenClaimsKeepFailing() throws ReflectiveOperationException {
    FeatureMobStacking feature = activeFeature();
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    AtomicInteger emitted = new AtomicInteger();
    Set<Long> pending = Mockito.mock(Set.class);
    Iterator<Long> iterator = Mockito.mock(Iterator.class);
    Mockito.when(pending.isEmpty()).thenReturn(false);
    Mockito.when(pending.iterator()).thenReturn(iterator);
    Mockito.when(iterator.hasNext()).thenAnswer(invocation -> emitted.get() < 10_000);
    Mockito.when(iterator.next()).thenAnswer(invocation -> FeatureMobStacking.packChunkKey(emitted.getAndIncrement(), 0));
    Mockito.when(pending.remove(Mockito.anyLong())).thenReturn(false);
    pendingByWorld(feature).put(worldId, pending);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduling.when(J::isFoliaThreading).thenReturn(true);

      feature.onTick();
    }

    Assertions.assertEquals(FeatureMobStacking.MAX_CHUNK_INSPECTIONS_PER_TICK, emitted.get());
  }

  @Test
  void repeatedEntitySampleReusesItsUnchangedIndexRecord() throws ReflectiveOperationException {
    FeatureMobStacking feature = activeFeature();
    Entity entity = Mockito.mock(Entity.class);
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    UUID entityId = UUID.randomUUID();
    Location first = new Location(world, 1D, 64D, 1D);
    Location moved = new Location(world, 17D, 64D, 1D);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(entity.getUniqueId()).thenReturn(entityId);
    Mockito.when(entity.getLocation()).thenReturn(first, first, moved);

    feature.onTick(entity);
    Object initial = indexedEntities(feature).get(entityId);
    feature.onTick(entity);
    Assertions.assertSame(initial, indexedEntities(feature).get(entityId));

    feature.onTick(entity);
    Assertions.assertNotSame(initial, indexedEntities(feature).get(entityId));
  }

  @Test
  void dirtySignalDuringFlightQueuesOneFollowUpPass() throws ReflectiveOperationException {
    FeatureMobStacking feature = activeFeature();
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    long key = FeatureMobStacking.packChunkKey(7, -4);
    Set<Long> pending = pending(feature, worldId);
    pending.add(key);
    List<Runnable> tasks = new ArrayList<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runChunk(
              Mockito.eq(world),
              Mockito.eq(7),
              Mockito.eq(-4),
              Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(3));
            return true;
          });
      Mockito.when(world.isChunkLoaded(7, -4)).thenReturn(false);

      feature.onTick();
      pending.add(key);
      feature.onTick();
      Assertions.assertEquals(1, tasks.size());

      tasks.getFirst().run();
      feature.onTick();
      Assertions.assertEquals(2, tasks.size());
      feature.onTick();
      Assertions.assertEquals(2, tasks.size());
    }
  }

  @Test
  void rejectedAndFailedFoliaClaimsReturnToPendingForRetry() throws ReflectiveOperationException {
    FeatureMobStacking feature = activeFeature();
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    long key = FeatureMobStacking.packChunkKey(3, 9);
    pending(feature, worldId).add(key);
    AtomicInteger attempts = new AtomicInteger(0);
    AtomicReference<Runnable> failedTask = new AtomicReference<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runChunk(
              Mockito.eq(world),
              Mockito.eq(3),
              Mockito.eq(9),
              Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
              return false;
            }
            if (attempt == 2) {
              failedTask.set(invocation.getArgument(3));
            }
            return true;
          });

      feature.onTick();
      Assertions.assertEquals(1, attempts.get());
      feature.onTick();
      Assertions.assertEquals(2, attempts.get());
      Assertions.assertNotNull(failedTask.get());

      Mockito.when(world.isChunkLoaded(3, 9)).thenThrow(new IllegalStateException("scan failed"));
      failedTask.get().run();
      feature.onTick();

      Assertions.assertEquals(3, attempts.get());
      react.verify(() -> React.reportError(Mockito.any(IllegalStateException.class)));
    }
  }

  @Test
  void denseNonMergeableChunkHasHardPerCallbackWorkBoundsAndConverges() throws ReflectiveOperationException {
    FeatureMobStacking feature = Mockito.spy(activeFeature());
    World world = Mockito.mock(World.class);
    Chunk chunk = Mockito.mock(Chunk.class);
    UUID worldId = UUID.randomUUID();
    int entityCount = 300;
    Entity[] entities = new Entity[entityCount];
    AtomicInteger entityLocations = new AtomicInteger();
    AtomicInteger comparisons = new AtomicInteger();
    Location location = new Location(world, 0D, 64D, 0D);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.isChunkLoaded(0, 0)).thenReturn(true);
    Mockito.when(world.getChunkAt(0, 0)).thenReturn(chunk);
    for (int index = 0; index < entityCount; index++) {
      LivingEntity entity = Mockito.mock(LivingEntity.class);
      Mockito.when(entity.getType()).thenReturn(EntityType.ZOMBIE);
      Mockito.when(entity.isDead()).thenReturn(false);
      Mockito.when(entity.getLocation()).thenAnswer(invocation -> {
        entityLocations.incrementAndGet();
        return location;
      });
      entities[index] = entity;
    }
    Mockito.when(chunk.getEntities()).thenReturn(entities);
    Mockito.doAnswer(invocation -> {
      comparisons.incrementAndGet();
      return false;
    }).when(feature).merge(Mockito.any(Entity.class), Mockito.any(Entity.class));
    Method stackChunk = FeatureMobStacking.class.getDeclaredMethod(
        "stackChunk",
        World.class,
        int.class,
        int.class
    );
    stackChunk.setAccessible(true);

    boolean completed = false;
    int callbacks = 0;
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<ReactProtection> protection = Mockito.mockStatic(ReactProtection.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      while (!completed && callbacks < 64) {
        int locationsBefore = entityLocations.get();
        int comparisonsBefore = comparisons.get();
        completed = (boolean) stackChunk.invoke(feature, world, 0, 0);
        callbacks++;

        Assertions.assertTrue(
            entityLocations.get() - locationsBefore <= FeatureMobStacking.MAX_ENTITIES_PER_CHUNK_CALLBACK
        );
        Assertions.assertTrue(
            comparisons.get() - comparisonsBefore <= FeatureMobStacking.MAX_COMPARISONS_PER_CHUNK_CALLBACK
        );
      }
    }

    Assertions.assertTrue(completed);
    Assertions.assertTrue(callbacks > 2);
    Assertions.assertEquals(entityCount, entityLocations.get());
    Assertions.assertEquals((entityCount * (entityCount - 1)) / 2, comparisons.get());
    Mockito.verify(chunk, Mockito.times(1)).getEntities();
  }

  @Test
  void adjacentChunkCandidateIsMergedWithoutReadingTheNeighborChunk() throws ReflectiveOperationException {
    FeatureMobStacking feature = Mockito.spy(activeFeature());
    World world = Mockito.mock(World.class);
    Chunk anchorChunk = Mockito.mock(Chunk.class);
    LivingEntity anchor = Mockito.mock(LivingEntity.class);
    LivingEntity adjacent = Mockito.mock(LivingEntity.class);
    UUID worldId = UUID.randomUUID();
    UUID adjacentId = UUID.randomUUID();
    Location anchorLocation = new Location(world, 15.25D, 64D, 0D);
    Location adjacentLocation = new Location(world, 16.25D, 64D, 0D);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.isChunkLoaded(0, 0)).thenReturn(true);
    Mockito.when(world.getChunkAt(0, 0)).thenReturn(anchorChunk);
    Mockito.when(anchorChunk.getEntities()).thenReturn(new Entity[]{anchor});
    Mockito.when(anchor.getType()).thenReturn(EntityType.ZOMBIE);
    Mockito.when(anchor.isDead()).thenReturn(false);
    Mockito.when(anchor.getLocation()).thenReturn(anchorLocation);
    Mockito.when(adjacent.getUniqueId()).thenReturn(adjacentId);
    Mockito.when(adjacent.getType()).thenReturn(EntityType.ZOMBIE);
    Mockito.when(adjacent.isDead()).thenReturn(false);
    Mockito.when(adjacent.getLocation()).thenReturn(adjacentLocation);
    Mockito.doReturn(1).when(feature).getStackCount(adjacent);
    Mockito.doReturn(true).when(feature).merge(adjacent, anchor);
    feature.onTick(adjacent);

    Method stackChunk = FeatureMobStacking.class.getDeclaredMethod(
        "stackChunk",
        World.class,
        int.class,
        int.class
    );
    stackChunk.setAccessible(true);
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<ReactProtection> protection = Mockito.mockStatic(ReactProtection.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(anchor)).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(adjacent)).thenReturn(true);

      Assertions.assertTrue((boolean) stackChunk.invoke(feature, world, 0, 0));
    }

    Mockito.verify(feature).merge(adjacent, anchor);
    Mockito.verify(world, Mockito.never()).getChunkAt(1, 0);
  }

  private FeatureMobStacking activeFeature() throws ReflectiveOperationException {
    FeatureMobStacking feature = new FeatureMobStacking();
    Field active = FeatureMobStacking.class.getDeclaredField("active");
    active.setAccessible(true);
    active.setBoolean(feature, true);
    return feature;
  }

  @SuppressWarnings("unchecked")
  private Set<Long> pending(FeatureMobStacking feature, UUID worldId) throws ReflectiveOperationException {
    return pendingByWorld(feature).computeIfAbsent(worldId, ignored -> ConcurrentHashMap.newKeySet());
  }

  @SuppressWarnings("unchecked")
  private Map<UUID, Set<Long>> pendingByWorld(FeatureMobStacking feature) throws ReflectiveOperationException {
    Field dirtyChunks = FeatureMobStacking.class.getDeclaredField("dirtyChunks");
    dirtyChunks.setAccessible(true);
    return (Map<UUID, Set<Long>>) dirtyChunks.get(feature);
  }

  @SuppressWarnings("unchecked")
  private Map<UUID, Object> indexedEntities(FeatureMobStacking feature) throws ReflectiveOperationException {
    Field indexedEntities = FeatureMobStacking.class.getDeclaredField("indexedEntities");
    indexedEntities.setAccessible(true);
    return (Map<UUID, Object>) indexedEntities.get(feature);
  }
}
