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
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
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
    World world = world();
    Location originLocation = location(world, 2, 3);
    Location destinationLocation = location(world, 20, -8);
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(entity.hasAI()).thenReturn(true);
    Mockito.when(entity.isDead()).thenReturn(false);
    AtomicDouble originCount = new AtomicDouble();
    AtomicDouble destinationCount = new AtomicDouble();
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.get(world, 2, 3, sampler)).thenReturn(originCount);
    Mockito.when(observer.get(world, 20, -8, sampler)).thenReturn(destinationCount);
    EntitySpawnEvent spawn = Mockito.mock(EntitySpawnEvent.class);
    Mockito.when(spawn.getEntity()).thenReturn(entity);
    Mockito.when(spawn.getLocation()).thenReturn(originLocation);
    EntityMoveEvent move = Mockito.mock(EntityMoveEvent.class);
    Mockito.when(move.getEntity()).thenReturn(entity);
    Mockito.when(move.getTo()).thenReturn(destinationLocation);
    EntityRemoveEvent remove = Mockito.mock(EntityRemoveEvent.class);
    Mockito.when(remove.getEntity()).thenReturn(entity);
    Mockito.when(remove.getCause()).thenReturn(EntityRemoveEvent.Cause.DESPAWN);
    SamplerEntitiesPaperMoveListener moveListener = new SamplerEntitiesPaperMoveListener(sampler);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.start();

      sampler.on(spawn);

      Assertions.assertEquals(1, sampler.getEntities().get());
      Assertions.assertEquals(1D, originCount.get());
      Assertions.assertEquals(0D, destinationCount.get());
      Assertions.assertEquals(1, EntityCensusTracker.activeAi());

      moveListener.on(move);

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
    World world = world();
    Location destinationLocation = location(world, -40, 90);
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    AtomicDouble destinationCount = new AtomicDouble();
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.get(world, -40, 90, sampler)).thenReturn(destinationCount);
    EntityMoveEvent move = Mockito.mock(EntityMoveEvent.class);
    Mockito.when(move.getEntity()).thenReturn(entity);
    Mockito.when(move.getTo()).thenReturn(destinationLocation);
    EntityRemoveEvent remove = Mockito.mock(EntityRemoveEvent.class);
    Mockito.when(remove.getEntity()).thenReturn(entity);
    Mockito.when(remove.getCause()).thenReturn(EntityRemoveEvent.Cause.PLUGIN);
    SamplerEntitiesPaperMoveListener moveListener = new SamplerEntitiesPaperMoveListener(sampler);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.start();
      sampler.getEntities().set(12);

      moveListener.on(move);

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
    World world = world();
    Location originLocation = location(world, 0, 0);
    Chunk destination = chunk(world, 1, 0);
    Item item = Mockito.mock(Item.class);
    Mockito.when(item.getUniqueId()).thenReturn(UUID.randomUUID());
    AtomicDouble originCount = new AtomicDouble();
    AtomicDouble destinationCount = new AtomicDouble();
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.get(world, 0, 0, sampler)).thenReturn(originCount);
    Mockito.when(observer.get(world, 1, 0, sampler)).thenReturn(destinationCount);
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

  @Test
  void spawnResolvesTheChunkFromLocationCoordinatesWithoutLoadingIt() {
    SamplerEntities sampler = new SamplerEntities();
    World world = world();
    Location spawnLocation = location(world, -3, 7);
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    AtomicDouble chunkCount = new AtomicDouble();
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.get(world, -3, 7, sampler)).thenReturn(chunkCount);
    EntitySpawnEvent spawn = Mockito.mock(EntitySpawnEvent.class);
    Mockito.when(spawn.getEntity()).thenReturn(entity);
    Mockito.when(spawn.getLocation()).thenReturn(spawnLocation);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.start();

      sampler.on(spawn);

      Assertions.assertEquals(1, sampler.getEntities().get());
      Assertions.assertEquals(1D, chunkCount.get());
      Mockito.verify(spawnLocation, Mockito.never()).getChunk();
      Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
      sampler.stop();
    }
  }

  @Test
  void sameChunkMoveSkipsChunkLookupAndCounterResolution() {
    SamplerEntities sampler = new SamplerEntities();
    World world = world();
    Location spawnLocation = location(world, 5, 5);
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(entity.hasAI()).thenReturn(true);
    AtomicDouble chunkCount = new AtomicDouble();
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.get(world, 5, 5, sampler)).thenReturn(chunkCount);
    EntitySpawnEvent spawn = Mockito.mock(EntitySpawnEvent.class);
    Mockito.when(spawn.getEntity()).thenReturn(entity);
    Mockito.when(spawn.getLocation()).thenReturn(spawnLocation);
    SamplerEntitiesPaperMoveListener moveListener = new SamplerEntitiesPaperMoveListener(sampler);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.start();
      sampler.on(spawn);

      for (int step = 0; step < 15; step++) {
        Location destination = Mockito.spy(new Location(world, 80.25D + step, 64.0D, 95.75D - step));
        EntityMoveEvent move = Mockito.mock(EntityMoveEvent.class);
        Mockito.when(move.getEntity()).thenReturn(entity);
        Mockito.when(move.getTo()).thenReturn(destination);

        moveListener.on(move);

        Mockito.verify(destination, Mockito.never()).getChunk();
      }

      Assertions.assertEquals(1D, chunkCount.get());
      Assertions.assertEquals(1, EntityCensusTracker.activeAi());
      Mockito.verify(observer, Mockito.times(1)).get(world, 5, 5, sampler);
      Mockito.verify(entity, Mockito.times(1)).hasAI();
      sampler.stop();
    }
  }

  @Test
  void samplerSignaturesReferenceNoPaperOnlyTypes() {
    for (Method method : SamplerEntities.class.getDeclaredMethods()) {
      Assertions.assertFalse(paperOnly(method.getReturnType()), method.toGenericString());
      for (Class<?> parameter : method.getParameterTypes()) {
        Assertions.assertFalse(paperOnly(parameter), method.toGenericString());
      }
    }
  }

  @Test
  void startRegistersThePaperMoveListenerAndStopUnregistersIt() {
    React previous = React.instance;
    React plugin = Mockito.mock(React.class);
    React.instance = plugin;
    SamplerEntities sampler = new SamplerEntities();

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      sampler.start();

      ArgumentCaptor<Listener> registered = ArgumentCaptor.forClass(Listener.class);
      Mockito.verify(plugin).registerListener(registered.capture());
      Assertions.assertInstanceOf(SamplerEntitiesPaperMoveListener.class, registered.getValue());

      sampler.stop();

      Mockito.verify(plugin).unregisterListener(registered.getValue());
    } finally {
      React.instance = previous;
    }
  }

  private World world() {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    return world;
  }

  private Chunk chunk(World world, int chunkX, int chunkZ) {
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(chunk.getWorld()).thenReturn(world);
    Mockito.when(chunk.getX()).thenReturn(chunkX);
    Mockito.when(chunk.getZ()).thenReturn(chunkZ);
    return chunk;
  }

  private Location location(World world, int chunkX, int chunkZ) {
    Chunk chunk = chunk(world, chunkX, chunkZ);
    Location location = Mockito.spy(new Location(world, chunkX * 16D + 7.5D, 64.0D, chunkZ * 16D + 3.25D));
    Mockito.lenient().when(world.getChunkAt(location)).thenReturn(chunk);
    return location;
  }

  private static boolean paperOnly(Class<?> type) {
    String name = type.getName();
    return name.startsWith("io.papermc.") || name.startsWith("com.destroystokyo.paper.");
  }
}
