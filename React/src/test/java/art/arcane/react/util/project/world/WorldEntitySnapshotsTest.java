package art.arcane.react.util.project.world;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

class WorldEntitySnapshotsTest {
  @BeforeEach
  void setUp() {
    WorldEntitySnapshots.invalidate();
  }

  @AfterEach
  void tearDown() {
    WorldEntitySnapshots.invalidate();
  }

  @Test
  void hundredThousandEntityIndexReturnsOnlyTheRequestedRotationWindow() {
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(world.getUID()).thenReturn(worldId);
    Entity entity = Mockito.mock(Entity.class);
    AtomicLong nextId = new AtomicLong(1L);
    Mockito.when(entity.getUniqueId()).thenAnswer(invocation -> new UUID(0L, nextId.getAndIncrement()));

    for (int index = 0; index < 100_000; index++) {
      WorldEntitySnapshots.observe(entity, world);
    }
    Mockito.clearInvocations(world, entity);

    List<Entity> sample = WorldEntitySnapshots.next(world, 256);

    Assertions.assertEquals(256, sample.size());
    Mockito.verify(world).getUID();
    Mockito.verifyNoMoreInteractions(world);
    Mockito.verifyNoInteractions(entity);
  }

  @Test
  void exactUnloadRemovalLeavesNoStaleRotationPrefix() {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Entity entity = Mockito.mock(Entity.class);
    List<UUID> entityIds = new ArrayList<>(1_001);
    AtomicLong nextId = new AtomicLong(1L);
    Mockito.when(entity.getUniqueId()).thenAnswer(invocation -> {
      UUID entityId = new UUID(0L, nextId.getAndIncrement());
      entityIds.add(entityId);
      return entityId;
    });

    for (int index = 0; index < 1_001; index++) {
      WorldEntitySnapshots.observe(entity, world);
    }
    for (int index = 0; index < 1_000; index++) {
      WorldEntitySnapshots.forget(entityIds.get(index));
    }

    Assertions.assertEquals(List.of(entity), WorldEntitySnapshots.next(world, 1));
  }

  @Test
  void initialHundredThousandEntityChunkIsReconciledInBoundedWindows() {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Chunk chunk = Mockito.mock(Chunk.class);
    Entity entity = Mockito.mock(Entity.class);
    ObserverController observer = Mockito.mock(ObserverController.class);
    Entity[] entities = new Entity[100_000];
    Arrays.fill(entities, entity);
    AtomicLong nextId = new AtomicLong(1L);

    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.isChunkLoaded(7, -9)).thenReturn(true);
    Mockito.when(world.getChunkAt(7, -9, false)).thenReturn(chunk);
    Mockito.when(chunk.isEntitiesLoaded()).thenReturn(true);
    Mockito.when(chunk.getEntities()).thenReturn(entities);
    Mockito.when(entity.getWorld()).thenReturn(world);
    Mockito.when(entity.getUniqueId()).thenAnswer(invocation -> new UUID(0L, nextId.getAndIncrement()));
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(1)).thenReturn(List.of(
        new ObserverController.LoadedChunkTarget(worldId, 7, -9)
    ));

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);

      Assertions.assertEquals(256, WorldEntitySnapshots.reconcileNextLoadedChunks(1, 256).size());
      Assertions.assertEquals(256, WorldEntitySnapshots.reconcileNextLoadedChunks(1, 256).size());

      Mockito.verify(observer, Mockito.times(1)).nextLoadedChunkCoordinateBatch(1);
      Mockito.verify(chunk, Mockito.times(2)).getEntities();
      Mockito.verify(entity, Mockito.times(512)).getUniqueId();
      Mockito.verify(world, Mockito.never()).getEntities();
      Mockito.verify(world, Mockito.never()).getLoadedChunks();
    }
  }
}
