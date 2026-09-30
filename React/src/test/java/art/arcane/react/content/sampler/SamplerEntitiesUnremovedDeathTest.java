package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

class SamplerEntitiesUnremovedDeathTest {
  private SamplerEntities sampler;
  private World world;
  private Chunk chunk;
  private AtomicBoolean dead;
  private Cow cow;
  private AtomicDouble chunkCount;
  private ObserverController observer;

  @BeforeEach
  void setUp() {
    EntityCensusTracker.release();
    EntityCensusTracker.acquire();
    sampler = new SamplerEntities();
    world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(world.getEntityCount()).thenThrow(new NoSuchMethodError("getEntityCount"));
    chunk = Mockito.mock(Chunk.class);
    Mockito.when(chunk.getWorld()).thenReturn(world);
    Mockito.when(chunk.getX()).thenReturn(0);
    Mockito.when(chunk.getZ()).thenReturn(0);
    dead = new AtomicBoolean(false);
    cow = Mockito.mock(Cow.class);
    Mockito.when(cow.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(cow.getWorld()).thenReturn(world);
    Mockito.when(cow.getLocation()).thenReturn(new Location(world, 7.5D, 64D, 3.25D));
    Mockito.when(cow.hasAI()).thenReturn(true);
    Mockito.when(cow.isDead()).thenAnswer(ignored -> dead.get());
    chunkCount = new AtomicDouble();
    observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.get(world, 0, 0, sampler)).thenReturn(chunkCount);
  }

  @AfterEach
  void tearDown() {
    EntityCensusTracker.release();
  }

  @Test
  void censusRefreshStopsCountingAnAnimalThatDiedWithoutARemovalEvent() {
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      sampler.start();
      sampler.on(spawn());
      Assertions.assertEquals(1, EntityCensusTracker.animals());
      Assertions.assertEquals(1, EntityCensusTracker.activeAi());

      dead.set(true);
      EntityCensusTracker.refreshMainThread();

      Assertions.assertEquals(0, EntityCensusTracker.animals());
      Assertions.assertEquals(0, EntityCensusTracker.activeAi());
      sampler.stop();
    }
  }

  @Test
  void deadAnimalReloadedWithItsChunkIsNotCounted() {
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.start();
      dead.set(true);

      sampler.on(entitiesLoad());

      Assertions.assertEquals(0, EntityCensusTracker.animals());
      Assertions.assertEquals(0, sampler.getEntities().get());
      Assertions.assertEquals(0D, chunkCount.get());
      Assertions.assertEquals(0, SamplerEntities.countWorldEntities(List.of(world)));
      sampler.stop();
    }
  }

  @Test
  void deathThatTheServerNeverRemovesClearsTheCensusSnapshotAndChunkCount() {
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.start();
      sampler.on(spawn());
      Assertions.assertEquals(1, EntityCensusTracker.animals());
      Assertions.assertEquals(1D, chunkCount.get());
      Assertions.assertEquals(1, SamplerEntities.countWorldEntities(List.of(world)));

      dead.set(true);
      sampler.on(death(cow));

      Assertions.assertEquals(0, EntityCensusTracker.animals());
      Assertions.assertEquals(0, EntityCensusTracker.activeAi());
      Assertions.assertEquals(0D, chunkCount.get());
      Assertions.assertEquals(0, SamplerEntities.countWorldEntities(List.of(world)));
      sampler.stop();
    }
  }

  @Test
  void removalAfterAnObservedDeathDecrementsTheWorldTotalOnce() {
    EntityRemoveEvent remove = Mockito.mock(EntityRemoveEvent.class);
    Mockito.when(remove.getEntity()).thenReturn(cow);
    Mockito.when(remove.getCause()).thenReturn(EntityRemoveEvent.Cause.DEATH);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.start();
      sampler.on(spawn());
      sampler.getEntities().set(5);

      dead.set(true);
      sampler.on(death(cow));
      sampler.on(remove);

      Assertions.assertEquals(4, sampler.getEntities().get());
      Assertions.assertEquals(0D, chunkCount.get());
      Assertions.assertEquals(0, EntityCensusTracker.animals());
      sampler.stop();
    }
  }

  @Test
  void playerDeathKeepsThePlayerIndexedForRespawn() {
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(player.getLocation()).thenReturn(new Location(world, 7.5D, 64D, 3.25D));
    PlayerJoinEvent join = Mockito.mock(PlayerJoinEvent.class);
    Mockito.when(join.getPlayer()).thenReturn(player);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.start();
      sampler.on(join);

      sampler.on(death(player));

      Assertions.assertEquals(1D, chunkCount.get());
      Assertions.assertEquals(1, SamplerEntities.countWorldEntities(List.of(world)));
      sampler.stop();
    }
  }

  private EntitySpawnEvent spawn() {
    EntitySpawnEvent spawn = Mockito.mock(EntitySpawnEvent.class);
    Mockito.when(spawn.getEntity()).thenReturn(cow);
    Mockito.when(spawn.getLocation()).thenReturn(new Location(world, 7.5D, 64D, 3.25D));
    return spawn;
  }

  private EntityDeathEvent death(LivingEntity entity) {
    EntityDeathEvent death = Mockito.mock(EntityDeathEvent.class);
    Mockito.when(death.getEntity()).thenReturn(entity);
    return death;
  }

  private EntitiesLoadEvent entitiesLoad() {
    EntitiesLoadEvent load = Mockito.mock(EntitiesLoadEvent.class);
    Mockito.when(load.getChunk()).thenReturn(chunk);
    Mockito.when(load.getEntities()).thenReturn(List.<Entity>of(cow));
    return load;
  }
}
