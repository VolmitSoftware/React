package art.arcane.react.content.tweak;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.entity.TrialSpawnerSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class TweakEntityHardstopTest {

  @Test
  public void sameCoordinatesInDifferentWorldsDoNotShareTheChunkCache() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture crowded = loadedChunk(4, -7, entities(100));
    ChunkFixture empty = loadedChunk(4, -7, new Entity[0]);

    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));
    Assertions.assertFalse(cancelsCreatureSpawn(tweak, empty));
    Mockito.verify(crowded.chunk()).getEntities();
    Mockito.verify(empty.chunk()).getEntities();
  }

  @Test
  public void cachedRejectionSkipsRepeatedEntityEnumeration() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture crowded = loadedChunk(2, 3, entities(100));

    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));
    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));
    Mockito.verify(crowded.chunk(), Mockito.times(1)).getEntities();
  }

  @Test
  public void cachedCountSkipsEntityEnumerationForAcceptedSpawns() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture quiet = loadedChunk(-9, 12, entities(3));

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, quiet));
    Assertions.assertFalse(cancelsCreatureSpawn(tweak, quiet));
    Assertions.assertFalse(cancelsCreatureSpawn(tweak, quiet));
    Mockito.verify(quiet.chunk(), Mockito.times(1)).getEntities();
  }

  @Test
  public void acceptedSpawnsRaiseTheCachedCountUntilARecountConfirmsTheCap() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture nearlyFull = loadedChunk(6, 6, entities(99));
    Mockito.when(nearlyFull.chunk().getEntities()).thenReturn(entities(99), entities(100));

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, nearlyFull));
    Assertions.assertTrue(cancelsCreatureSpawn(tweak, nearlyFull));
    Assertions.assertTrue(cancelsCreatureSpawn(tweak, nearlyFull));
    Mockito.verify(nearlyFull.chunk(), Mockito.times(2)).getEntities();
  }

  @Test
  public void overstatedCountsRecountBeforeRejecting() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture steady = loadedChunk(7, -2, entities(99));

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, steady));
    Assertions.assertFalse(cancelsCreatureSpawn(tweak, steady));
    Mockito.verify(steady.chunk(), Mockito.times(2)).getEntities();
  }

  @Test
  public void cancelledSpawnsAreNotEvaluatedOrCounted() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
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
    for (Method method : TweakEntityHardstop.class.getDeclaredMethods()) {
      EventHandler handler = method.getAnnotation(EventHandler.class);
      if (handler != null) {
        Assertions.assertTrue(handler.ignoreCancelled(), method.toGenericString());
      }
    }
  }

  @Test
  public void spawnerSpawnsCountEachMobOnce() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
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
    TweakEntityHardstop tweak = new TweakEntityHardstop();
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
    TweakEntityHardstop tweak = new TweakEntityHardstop();
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
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture fixture = loadedChunk(1, -1, entities(2));
    EntitySpawnEvent spawn = Mockito.mock(EntitySpawnEvent.class);
    stubSpawnedEntity(spawn, fixture, EntityType.ARMOR_STAND);

    Assertions.assertFalse(fire(tweak, spawn));
    cancelsCreatureSpawn(tweak, fixture);

    Mockito.verify(fixture.location(), Mockito.never()).getChunk();
  }

  @Test
  public void unloadedChunksAreAdmittedWithoutLoadingOrCounting() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture unloaded = chunk(8, 8, entities(100), false);

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, unloaded));
    Mockito.verify(unloaded.world(), Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
    Mockito.verify(unloaded.world(), Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
    Mockito.verify(unloaded.world(), Mockito.never()).getChunkAt(Mockito.any(Location.class));
    Mockito.verify(unloaded.chunk(), Mockito.never()).getEntities();
  }

  @Test
  public void chunksWithUnloadedEntitiesAreAdmittedWithoutCounting() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture pending = loadedChunk(9, -9, entities(100));
    Mockito.when(pending.chunk().isEntitiesLoaded()).thenReturn(false);

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, pending));
    Mockito.verify(pending.chunk(), Mockito.never()).getEntities();
  }

  @Test
  public void allowedItemDropsDoNotCountTowardTheCap() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    Entity[] items = new Entity[100];
    for (int index = 0; index < items.length; index++) {
      items[index] = Mockito.mock(Item.class);
    }
    ChunkFixture littered = loadedChunk(0, 5, items);

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, littered));
  }

  @Test
  public void genericSpawnHandlerLeavesCreatureEventsToReasonAwareHandler() {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    CreatureSpawnEvent event = Mockito.mock(CreatureSpawnEvent.class);

    tweak.onEntitySpawn(event);

    Mockito.verifyNoInteractions(event);
  }

  @Test
  public void deactivationDropsCachedCounts() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture crowded = loadedChunk(0, 0, entities(100));
    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));

    tweak.onDeactivate();

    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));
    Mockito.verify(crowded.chunk(), Mockito.times(2)).getEntities();
  }

  private static boolean cancelsCreatureSpawn(TweakEntityHardstop tweak, ChunkFixture fixture) throws EventException {
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

  private static boolean fire(TweakEntityHardstop tweak, Event event) throws EventException {
    dispatch(tweak, event);
    return Mockito.mockingDetails(event).getInvocations().stream()
        .anyMatch(invocation -> invocation.getMethod().getName().equals("setCancelled")
            && Boolean.TRUE.equals(invocation.getArgument(0)));
  }

  private static void dispatch(TweakEntityHardstop tweak, Event event) throws EventException {
    Plugin plugin = Mockito.mock(Plugin.class);
    for (Method method : TweakEntityHardstop.class.getDeclaredMethods()) {
      EventHandler handler = method.getAnnotation(EventHandler.class);
      if (handler == null || !method.getParameterTypes()[0].isInstance(event)) {
        continue;
      }
      Class<? extends Event> eventClass = method.getParameterTypes()[0].asSubclass(Event.class);
      RegisteredListener registered = new RegisteredListener(
          tweak,
          EventExecutor.create(method, eventClass),
          handler.priority(),
          plugin,
          handler.ignoreCancelled()
      );
      registered.callEvent(event);
    }
  }

  private static void setField(TweakEntityHardstop tweak, String name, boolean value) throws ReflectiveOperationException {
    Field field = TweakEntityHardstop.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setBoolean(tweak, value);
  }

  private static ChunkFixture loadedChunk(int chunkX, int chunkZ, Entity[] entities) {
    return chunk(chunkX, chunkZ, entities, true);
  }

  private static ChunkFixture chunk(int chunkX, int chunkZ, Entity[] entities, boolean loaded) {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
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
}
