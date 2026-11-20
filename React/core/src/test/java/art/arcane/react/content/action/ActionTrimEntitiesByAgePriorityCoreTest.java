package art.arcane.react.content.action;

import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.WorldHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

class ActionTrimEntitiesByAgePriorityCoreTest {
  private final UUID worldId = UUID.randomUUID();
  private final List<Runnable> regionTasks = new ArrayList<>();
  private final List<GameEntity> killed = new ArrayList<>();
  private final EntityHost entities = Mockito.mock(EntityHost.class);
  private final WorldHost worlds = Mockito.mock(WorldHost.class);
  private final ChunkHost chunks = Mockito.mock(ChunkHost.class);
  private final ReactWorld world = Mockito.mock(ReactWorld.class);

  @BeforeEach
  void bind() {
    PlatformScheduler scheduler = Mockito.mock(PlatformScheduler.class);
    Mockito.when(scheduler.isActive()).thenReturn(true);
    Mockito.when(scheduler.isPrimaryThread()).thenReturn(false);
    Mockito.when(scheduler.region(Mockito.any(ReactWorld.class), Mockito.anyInt(), Mockito.anyInt(),
        Mockito.any(Runnable.class), Mockito.anyInt())).thenAnswer(invocation -> {
          regionTasks.add(invocation.getArgument(3));
          return true;
        });
    Mockito.when(world.id()).thenReturn(worldId);
    Mockito.when(worlds.world(worldId)).thenReturn(Optional.of(world));
    Mockito.when(entities.allows(Mockito.any(), Mockito.any())).thenReturn(true);
    Mockito.doAnswer(invocation -> {
      killed.add(invocation.getArgument(0));
      return null;
    }).when(entities).kill(Mockito.any(), Mockito.anyInt());
    ReactPlatforms.bind(new Platform(scheduler));
  }

  @AfterEach
  void unbind() {
    ReactPlatforms.unbind();
  }

  @Test
  void trimsTheCrowdedChunkWhenHotterChunksWithoutEntitiesComeFirst() {
    List<GameEntity> pigs = new ArrayList<>();
    for (int index = 0; index < 30; index++) {
      pigs.add(pig());
    }
    Mockito.when(entities.chunkEntities(world, 0, 0)).thenReturn(pigs);
    Mockito.when(entities.chunkEntities(world, 1, 0)).thenReturn(List.of());
    Mockito.when(entities.chunkEntities(world, 2, 0)).thenReturn(List.of());
    Mockito.when(chunks.sampledChunks()).thenReturn(List.of(
        sampled(1, 0, "tile-ticks", 900D), sampled(2, 0, "tile-ticks", 600D), sampled(0, 0, SamplerEntities.ID, 30D)));

    ActionTrimEntitiesByAgePriority action = new ActionTrimEntitiesByAgePriority();
    ActionTrimEntitiesByAgePriority.Params params = action.getDefaultParams().setMaxTrim(10).setMinEntityAgeTicks(20);
    ActionTicket<ActionTrimEntitiesByAgePriority.Params> ticket = new ActionTicket<>(action, params);

    for (int round = 0; round < 20 && !ticket.isDone(); round++) {
      action.workOn(ticket);
      List<Runnable> due = new ArrayList<>(regionTasks);
      regionTasks.clear();
      due.forEach(Runnable::run);
    }

    Assertions.assertTrue(ticket.isDone());
    Assertions.assertEquals(10, killed.size());
    Assertions.assertEquals(10, params.getTrimmedAtomic().get());
  }

  @Test
  void protectedEntitiesAreSkipped() {
    GameEntity pig = pig();
    Mockito.when(entities.isProtected(pig, ReactOperation.TRIM)).thenReturn(true);
    Mockito.when(entities.chunkEntities(world, 0, 0)).thenReturn(List.of(pig));
    Mockito.when(chunks.sampledChunks()).thenReturn(List.of(sampled(0, 0, SamplerEntities.ID, 1D)));

    ActionTrimEntitiesByAgePriority action = new ActionTrimEntitiesByAgePriority();
    ActionTicket<ActionTrimEntitiesByAgePriority.Params> ticket =
        new ActionTicket<>(action, action.getDefaultParams().setMinEntityAgeTicks(20));
    for (int round = 0; round < 10 && !ticket.isDone(); round++) {
      action.workOn(ticket);
      List<Runnable> due = new ArrayList<>(regionTasks);
      regionTasks.clear();
      due.forEach(Runnable::run);
    }

    Assertions.assertTrue(killed.isEmpty());
  }

  private SampledChunk sampled(int chunkX, int chunkZ, String sampler, double value) {
    SampledChunk chunk = new SampledChunk(worldId, "minecraft:overworld", chunkX, chunkZ);
    chunk.gauge(sampler).set(value);
    return chunk;
  }

  private GameEntity pig() {
    GameEntity pig = Mockito.mock(GameEntity.class);
    Mockito.when(pig.id()).thenReturn(UUID.randomUUID());
    Mockito.when(pig.type()).thenReturn("PIG");
    Mockito.when(pig.isValid()).thenReturn(true);
    Mockito.when(pig.isLiving()).thenReturn(true);
    Mockito.when(pig.ticksLived()).thenReturn(300);
    Mockito.when(pig.location()).thenReturn(new GameLocation(worldId, 8D, -60D, 8D));
    return pig;
  }

  private final class Platform implements ReactPlatform {
    private final PlatformScheduler scheduler;

    private Platform(PlatformScheduler scheduler) {
      this.scheduler = scheduler;
    }

    @Override
    public PlatformKind kind() {
      return PlatformKind.MODDED;
    }

    @Override
    public String platformName() {
      return "Test";
    }

    @Override
    public String minecraftVersion() {
      return "26.3";
    }

    @Override
    public File dataFolder() {
      return null;
    }

    @Override
    public PlatformScheduler scheduler() {
      return scheduler;
    }

    @Override
    public ExecutorService asyncExecutor() {
      return Mockito.mock(ExecutorService.class);
    }

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void verbose(String message) {
    }

    @Override
    public void reportError(String context, Throwable failure) {
      throw new AssertionError(context, failure);
    }

    @Override
    public Optional<EntityHost> entityHost() {
      return Optional.of(entities);
    }

    @Override
    public Optional<WorldHost> worldHost() {
      return Optional.of(worlds);
    }

    @Override
    public Optional<ChunkHost> chunkHost() {
      return Optional.of(chunks);
    }
  }
}
