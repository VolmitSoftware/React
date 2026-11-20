package art.arcane.react.content.tweak;

import art.arcane.react.platform.HostListeners;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.bukkit.BukkitSpawnHost;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.entity.TrialSpawnerSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.function.Consumer;

public class TweakEntityHardstopTest {
  private final Map<UUID, World> worlds = new HashMap<>();
  private final CapturingSpawnHost host = new CapturingSpawnHost();
  private MockedStatic<Bukkit> bukkit;

  @BeforeEach
  void bindPlatform() {
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    Mockito.when(platform.spawnHost()).thenReturn(Optional.of(host));
    ReactPlatforms.unbind();
    ReactPlatforms.bind(platform);
    bukkit = Mockito.mockStatic(Bukkit.class);
    bukkit.when(() -> Bukkit.getWorld(Mockito.any(UUID.class)))
        .thenAnswer(invocation -> worlds.get(invocation.getArgument(0, UUID.class)));
  }

  @AfterEach
  void unbindPlatform() {
    bukkit.close();
    ReactPlatforms.unbind();
  }

  @Test
  public void sameCoordinatesInDifferentWorldsDoNotShareTheChunkCache() throws Exception {
    TweakEntityHardstop tweak = tweak();
    ChunkFixture crowded = loadedChunk(4, -7, entities(100));
    ChunkFixture empty = loadedChunk(4, -7, new Entity[0]);

    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));
    Assertions.assertFalse(cancelsCreatureSpawn(tweak, empty));
    Mockito.verify(crowded.chunk()).getEntities();
    Mockito.verify(empty.chunk()).getEntities();
  }

  @Test
  public void cachedRejectionSkipsRepeatedEntityEnumeration() throws Exception {
    TweakEntityHardstop tweak = tweak();
    ChunkFixture crowded = loadedChunk(2, 3, entities(100));

    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));
    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));
    Mockito.verify(crowded.chunk(), Mockito.times(1)).getEntities();
  }

  @Test
  public void cachedCountSkipsEntityEnumerationForAcceptedSpawns() throws Exception {
    TweakEntityHardstop tweak = tweak();
    ChunkFixture quiet = loadedChunk(-9, 12, entities(3));

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, quiet));
    Assertions.assertFalse(cancelsCreatureSpawn(tweak, quiet));
    Assertions.assertFalse(cancelsCreatureSpawn(tweak, quiet));
    Mockito.verify(quiet.chunk(), Mockito.times(1)).getEntities();
  }

  @Test
  public void acceptedSpawnsRaiseTheCachedCountUntilARecountConfirmsTheCap() throws Exception {
    TweakEntityHardstop tweak = tweak();
    ChunkFixture nearlyFull = loadedChunk(6, 6, entities(99));
    Mockito.when(nearlyFull.chunk().getEntities()).thenReturn(entities(99), entities(100));

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, nearlyFull));
    Assertions.assertTrue(cancelsCreatureSpawn(tweak, nearlyFull));
    Assertions.assertTrue(cancelsCreatureSpawn(tweak, nearlyFull));
    Mockito.verify(nearlyFull.chunk(), Mockito.times(2)).getEntities();
  }

  @Test
  public void overstatedCountsRecountBeforeRejecting() throws Exception {
    TweakEntityHardstop tweak = tweak();
    ChunkFixture steady = loadedChunk(7, -2, entities(99));

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, steady));
    Assertions.assertFalse(cancelsCreatureSpawn(tweak, steady));
    Mockito.verify(steady.chunk(), Mockito.times(2)).getEntities();
  }

  @Test
  public void cancelledSpawnsAreNotEvaluatedOrCounted() throws Exception {
    TweakEntityHardstop tweak = tweak();
    ChunkFixture nearlyFull = loadedChunk(3, 9, entities(99));
    CreatureSpawnEvent blocked = creatureSpawn(nearlyFull, CreatureSpawnEvent.SpawnReason.NATURAL);
    Mockito.when(blocked.isCancelled()).thenReturn(true);

    dispatch(tweak, blocked);

    Mockito.verify(nearlyFull.chunk(), Mockito.never()).getEntities();
    Assertions.assertFalse(cancelsCreatureSpawn(tweak, nearlyFull));
    Mockito.verify(nearlyFull.chunk(), Mockito.times(1)).getEntities();
  }

  @Test
  public void everySpawnHandlerSkipsCancelledEvents() {
    tweak();

    Assertions.assertEquals(4, host.ignoreCancelled.size());
    for (Boolean ignoreCancelled : host.ignoreCancelled) {
      Assertions.assertTrue(ignoreCancelled);
    }
  }

  @Test
  public void spawnerSpawnsCountEachMobOnce() throws Exception {
    TweakEntityHardstop tweak = tweak();
    ChunkFixture spawnerRoom = loadedChunk(10, 4, entities(98));

    SpawnerSpawnEvent spawner = Mockito.mock(SpawnerSpawnEvent.class);
    stubSpawnedEntity(spawner, spawnerRoom, EntityType.ZOMBIE);
    Assertions.assertFalse(fire(tweak, spawner));
    Assertions.assertFalse(fire(tweak, creatureSpawn(spawnerRoom, CreatureSpawnEvent.SpawnReason.SPAWNER)));

    TrialSpawnerSpawnEvent trialSpawner = Mockito.mock(TrialSpawnerSpawnEvent.class);
    stubSpawnedEntity(trialSpawner, spawnerRoom, EntityType.ZOMBIE);
    Assertions.assertFalse(fire(tweak, trialSpawner));
    Assertions.assertFalse(fire(tweak, creatureSpawn(spawnerRoom, CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER)));

    Mockito.verify(spawnerRoom.chunk(), Mockito.times(1)).getEntities();
  }

  @Test
  public void breedingCountsTheChildOnce() throws Exception {
    TweakEntityHardstop tweak = tweak();
    ChunkFixture pen = loadedChunk(-4, -4, entities(98));

    for (int child = 0; child < 2; child++) {
      LivingEntity calf = Mockito.mock(LivingEntity.class);
      Mockito.when(calf.getLocation()).thenReturn(pen.location());
      Mockito.when(calf.getType()).thenReturn(EntityType.COW);
      EntityBreedEvent breed = Mockito.mock(EntityBreedEvent.class);
      Mockito.when(breed.getEntity()).thenReturn(calf);

      Assertions.assertFalse(fire(tweak, breed));
      Assertions.assertFalse(fire(tweak, creatureSpawn(pen, CreatureSpawnEvent.SpawnReason.BREEDING)));
    }

    Mockito.verify(pen.chunk(), Mockito.times(1)).getEntities();
  }

  @Test
  public void deniedItemDropsCountEachItemOnce() throws Exception {
    TweakEntityHardstop tweak = tweak();
    setField(tweak, "allowItemDrops", false);
    ChunkFixture dropZone = loadedChunk(12, 1, entities(98));

    for (int drop = 0; drop < 2; drop++) {
      Player player = Mockito.mock(Player.class);
      Mockito.when(player.getLocation()).thenReturn(dropZone.location());
      PlayerDropItemEvent playerDrop = Mockito.mock(PlayerDropItemEvent.class);
      Mockito.when(playerDrop.getPlayer()).thenReturn(player);
      ItemSpawnEvent itemSpawn = Mockito.mock(ItemSpawnEvent.class);
      Item item = Mockito.mock(Item.class);
      Mockito.when(item.getLocation()).thenReturn(dropZone.location());
      Mockito.when(item.getType()).thenReturn(EntityType.ITEM);
      Mockito.when(itemSpawn.getEntity()).thenReturn(item);

      Assertions.assertFalse(fire(tweak, playerDrop));
      Assertions.assertFalse(fire(tweak, itemSpawn));
    }

    Mockito.verify(dropZone.chunk(), Mockito.times(1)).getEntities();
  }

  @Test
  public void spawnChecksNeverLoadTheChunkThroughTheLocation() throws Exception {
    TweakEntityHardstop tweak = tweak();
    ChunkFixture fixture = loadedChunk(1, -1, entities(2));
    EntitySpawnEvent spawn = Mockito.mock(EntitySpawnEvent.class);
    stubSpawnedEntity(spawn, fixture, EntityType.ARMOR_STAND);

    Assertions.assertFalse(fire(tweak, spawn));
    cancelsCreatureSpawn(tweak, fixture);

    Mockito.verify(fixture.location(), Mockito.never()).getChunk();
  }

  @Test
  public void unloadedChunksAreAdmittedWithoutLoadingOrCounting() throws Exception {
    TweakEntityHardstop tweak = tweak();
    ChunkFixture unloaded = chunk(8, 8, entities(100), false);

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, unloaded));
    Mockito.verify(unloaded.world(), Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
    Mockito.verify(unloaded.world(), Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
    Mockito.verify(unloaded.world(), Mockito.never()).getChunkAt(Mockito.any(Location.class));
    Mockito.verify(unloaded.chunk(), Mockito.never()).getEntities();
  }

  @Test
  public void chunksWithUnloadedEntitiesAreAdmittedWithoutCounting() throws Exception {
    TweakEntityHardstop tweak = tweak();
    ChunkFixture pending = loadedChunk(9, -9, entities(100));
    Mockito.when(pending.chunk().isEntitiesLoaded()).thenReturn(false);

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, pending));
    Mockito.verify(pending.chunk(), Mockito.never()).getEntities();
  }

  @Test
  public void allowedItemDropsDoNotCountTowardTheCap() throws Exception {
    TweakEntityHardstop tweak = tweak();
    Entity[] items = new Entity[100];
    for (int index = 0; index < items.length; index++) {
      items[index] = Mockito.mock(Item.class);
    }
    ChunkFixture littered = loadedChunk(0, 5, items);

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, littered));
  }

  @Test
  public void genericSpawnHandlerLeavesCreatureEventsToReasonAwareHandler() {
    CreatureSpawnEvent event = Mockito.mock(CreatureSpawnEvent.class);

    Assertions.assertNull(BukkitSpawnHost.entitySpawn(event));

    Mockito.verifyNoInteractions(event);
  }

  @Test
  public void deactivationDropsCachedCounts() throws Exception {
    TweakEntityHardstop tweak = tweak();
    ChunkFixture crowded = loadedChunk(0, 0, entities(100));
    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));

    tweak.onDeactivate();
    tweak.onActivate();

    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));
    Mockito.verify(crowded.chunk(), Mockito.times(2)).getEntities();
  }

  private boolean cancelsCreatureSpawn(TweakEntityHardstop tweak, ChunkFixture fixture) {
    return fire(tweak, creatureSpawn(fixture, CreatureSpawnEvent.SpawnReason.NATURAL));
  }

  private static CreatureSpawnEvent creatureSpawn(ChunkFixture fixture, CreatureSpawnEvent.SpawnReason reason) {
    CreatureSpawnEvent event = Mockito.mock(CreatureSpawnEvent.class);
    Mockito.when(event.getLocation()).thenReturn(fixture.location());
    Mockito.when(event.getEntityType()).thenReturn(EntityType.ZOMBIE);
    Mockito.when(event.getSpawnReason()).thenReturn(reason);
    return event;
  }

  private static void stubSpawnedEntity(EntitySpawnEvent event, ChunkFixture fixture, EntityType type) {
    Entity entity = Mockito.mock(Entity.class);
    Mockito.when(entity.getLocation()).thenReturn(fixture.location());
    Mockito.when(entity.getType()).thenReturn(type);
    Mockito.when(event.getEntity()).thenReturn(entity);
  }

  private TweakEntityHardstop tweak() {
    host.clear();
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    tweak.onActivate();
    return tweak;
  }

  private boolean fire(TweakEntityHardstop tweak, Event event) {
    dispatch(tweak, event);
    return Mockito.mockingDetails(event).getInvocations().stream()
        .anyMatch(invocation -> invocation.getMethod().getName().equals("setCancelled")
            && Boolean.TRUE.equals(invocation.getArgument(0)));
  }

  private void dispatch(TweakEntityHardstop tweak, Event event) {
    if (event instanceof CreatureSpawnEvent creature) {
      host.creatures.dispatch(BukkitSpawnHost.spawn(creature));
    } else if (event instanceof EntitySpawnEvent spawn) {
      host.entities.dispatch(BukkitSpawnHost.entitySpawn(spawn));
    } else if (event instanceof EntityBreedEvent breed) {
      host.breeds.dispatch(BukkitSpawnHost.breed(breed));
    } else if (event instanceof PlayerDropItemEvent drop) {
      host.drops.dispatch(BukkitSpawnHost.drop(drop));
    }
  }

  private static void setField(TweakEntityHardstop tweak, String name, boolean value) throws ReflectiveOperationException {
    Field field = TweakEntityHardstop.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setBoolean(tweak, value);
  }

  private ChunkFixture loadedChunk(int chunkX, int chunkZ, Entity[] entities) {
    return chunk(chunkX, chunkZ, entities, true);
  }

  private ChunkFixture chunk(int chunkX, int chunkZ, Entity[] entities, boolean loaded) {
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    worlds.put(worldId, world);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getName()).thenReturn("world");
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(chunk.getWorld()).thenReturn(world);
    Mockito.when(chunk.getX()).thenReturn(chunkX);
    Mockito.when(chunk.getZ()).thenReturn(chunkZ);
    Mockito.when(chunk.getEntities()).thenReturn(entities);
    Mockito.when(chunk.isEntitiesLoaded()).thenReturn(loaded);
    Mockito.when(world.isChunkLoaded(chunkX, chunkZ)).thenReturn(loaded);
    Mockito.when(world.getChunkAt(chunkX, chunkZ)).thenReturn(chunk);
    Mockito.when(world.getChunkAt(chunkX, chunkZ, false)).thenReturn(chunk);
    Location location = Mockito.spy(new Location(world, chunkX * 16D + 4.5D, 70.0D, chunkZ * 16D + 11.5D));
    Mockito.when(world.getChunkAt(location)).thenReturn(chunk);
    return new ChunkFixture(world, chunk, location);
  }

  private static Entity[] entities(int count) {
    Entity[] entities = new Entity[count];
    for (int index = 0; index < count; index++) {
      entities[index] = Mockito.mock(Entity.class);
    }
    return entities;
  }

  private record ChunkFixture(World world, Chunk chunk, Location location) {
  }

  private static final class CapturingSpawnHost extends BukkitSpawnHost {
    private HostListeners<Spawn> creatures = new HostListeners<>();
    private HostListeners<EntitySpawn> entities = new HostListeners<>();
    private HostListeners<Breed> breeds = new HostListeners<>();
    private HostListeners<ItemDrop> drops = new HostListeners<>();
    private final List<Boolean> ignoreCancelled = new ArrayList<>();

    private void clear() {
      creatures = new HostListeners<>();
      entities = new HostListeners<>();
      breeds = new HostListeners<>();
      drops = new HostListeners<>();
      ignoreCancelled.clear();
    }

    @Override
    public Subscription onCreatureSpawn(ListenerPriority priority, boolean ignore, Consumer<Spawn> listener) {
      ignoreCancelled.add(ignore);
      return creatures.subscribe(priority, ignore, listener);
    }

    @Override
    public Subscription onEntitySpawn(ListenerPriority priority, boolean ignore, Consumer<EntitySpawn> listener) {
      ignoreCancelled.add(ignore);
      return entities.subscribe(priority, ignore, listener);
    }

    @Override
    public Subscription onBreed(ListenerPriority priority, boolean ignore, Consumer<Breed> listener) {
      ignoreCancelled.add(ignore);
      return breeds.subscribe(priority, ignore, listener);
    }

    @Override
    public Subscription onPlayerDropItem(ListenerPriority priority, boolean ignore, Consumer<ItemDrop> listener) {
      ignoreCancelled.add(ignore);
      return drops.subscribe(priority, ignore, listener);
    }
  }
}
