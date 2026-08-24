package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import com.google.common.util.concurrent.AtomicDouble;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

class SamplerEntitiesMovementTest {
  @BeforeEach
  void setUp() {
    EntityCensusTracker.release();
    EntityCensusTracker.acquire();
  }

  @AfterEach
  void tearDown() {
    EntityCensusTracker.release();
  }

  @Test
  void crossChunkMoveThenRemoveClearsTheCurrentBucketWithoutLeavingTheOriginBehind() {
    SamplerEntities sampler = new SamplerEntities();
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Chunk origin = chunk(world, 2, 3);
    Chunk destination = chunk(world, 20, -8);
    Location originLocation = location(origin);
    Location destinationLocation = location(destination);
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(entity.hasAI()).thenReturn(true);
    Mockito.when(entity.isDead()).thenReturn(false);
    AtomicDouble originCount = new AtomicDouble();
    AtomicDouble destinationCount = new AtomicDouble();
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.get(origin, sampler)).thenReturn(originCount);
    Mockito.when(observer.get(destination, sampler)).thenReturn(destinationCount);
    EntitySpawnEvent spawn = Mockito.mock(EntitySpawnEvent.class);
    Mockito.when(spawn.getEntity()).thenReturn(entity);
    Mockito.when(spawn.getLocation()).thenReturn(originLocation);
    EntityMoveEvent move = Mockito.mock(EntityMoveEvent.class);
    Mockito.when(move.getEntity()).thenReturn(entity);
    Mockito.when(move.getTo()).thenReturn(destinationLocation);
    EntityRemoveEvent remove = Mockito.mock(EntityRemoveEvent.class);
    Mockito.when(remove.getEntity()).thenReturn(entity);
    Mockito.when(remove.getCause()).thenReturn(EntityRemoveEvent.Cause.DESPAWN);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.start();

      sampler.on(spawn);

      Assertions.assertEquals(1, sampler.getEntities().get());
      Assertions.assertEquals(1D, originCount.get());
      Assertions.assertEquals(0D, destinationCount.get());
      Assertions.assertEquals(1, EntityCensusTracker.activeAi());

      sampler.on(move);

      Assertions.assertEquals(0D, originCount.get());
      Assertions.assertEquals(1D, destinationCount.get());

      sampler.on(remove);

      Assertions.assertEquals(0, sampler.getEntities().get());
      Assertions.assertEquals(0D, originCount.get());
      Assertions.assertEquals(0D, destinationCount.get());
      Assertions.assertEquals(0, EntityCensusTracker.activeAi());
      sampler.stop();
    }
  }

  @Test
  void firstObservedMoveSeedsRemovalOwnershipWithoutChangingTheWorldTotal() {
    SamplerEntities sampler = new SamplerEntities();
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Chunk destination = chunk(world, -40, 90);
    Location destinationLocation = location(destination);
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    AtomicDouble destinationCount = new AtomicDouble();
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.get(destination, sampler)).thenReturn(destinationCount);
    EntityMoveEvent move = Mockito.mock(EntityMoveEvent.class);
    Mockito.when(move.getEntity()).thenReturn(entity);
    Mockito.when(move.getTo()).thenReturn(destinationLocation);
    EntityRemoveEvent remove = Mockito.mock(EntityRemoveEvent.class);
    Mockito.when(remove.getEntity()).thenReturn(entity);
    Mockito.when(remove.getCause()).thenReturn(EntityRemoveEvent.Cause.PLUGIN);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.start();
      sampler.getEntities().set(12);

      sampler.on(move);

      Assertions.assertEquals(12, sampler.getEntities().get());
      Assertions.assertEquals(1D, destinationCount.get());

      sampler.on(remove);

      Assertions.assertEquals(11, sampler.getEntities().get());
      Assertions.assertEquals(0D, destinationCount.get());
      sampler.stop();
    }
  }

  @Test
  void boundedCensusTransfersAnEntityThatMovedWithoutABukkitMoveEvent() {
    SamplerEntities sampler = new SamplerEntities();
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Chunk origin = chunk(world, 0, 0);
    Chunk destination = chunk(world, 1, 0);
    Location originLocation = location(origin);
    Item item = Mockito.mock(Item.class);
    Mockito.when(item.getUniqueId()).thenReturn(UUID.randomUUID());
    AtomicDouble originCount = new AtomicDouble();
    AtomicDouble destinationCount = new AtomicDouble();
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.get(origin, sampler)).thenReturn(originCount);
    Mockito.when(observer.get(destination, sampler)).thenReturn(destinationCount);
    EntitySpawnEvent spawn = Mockito.mock(EntitySpawnEvent.class);
    Mockito.when(spawn.getEntity()).thenReturn(item);
    Mockito.when(spawn.getLocation()).thenReturn(originLocation);
    EntityRemoveEvent remove = Mockito.mock(EntityRemoveEvent.class);
    Mockito.when(remove.getEntity()).thenReturn(item);
    Mockito.when(remove.getCause()).thenReturn(EntityRemoveEvent.Cause.PICKUP);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.start();

      sampler.on(spawn);
      SamplerEntities.reconcileCurrentChunk(item, destination);

      Assertions.assertEquals(0D, originCount.get());
      Assertions.assertEquals(1D, destinationCount.get());

      sampler.on(remove);

      Assertions.assertEquals(0D, originCount.get());
      Assertions.assertEquals(0D, destinationCount.get());
      sampler.stop();
    }
  }

  private Chunk chunk(World world, int chunkX, int chunkZ) {
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(chunk.getWorld()).thenReturn(world);
    Mockito.when(chunk.getX()).thenReturn(chunkX);
    Mockito.when(chunk.getZ()).thenReturn(chunkZ);
    return chunk;
  }

  private Location location(Chunk chunk) {
    Location location = Mockito.mock(Location.class);
    Mockito.when(location.getChunk()).thenReturn(chunk);
    return location;
  }
}
