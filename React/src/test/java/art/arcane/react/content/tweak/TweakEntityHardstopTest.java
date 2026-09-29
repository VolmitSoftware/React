package art.arcane.react.content.tweak;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

public class TweakEntityHardstopTest {

  @Test
  public void sameCoordinatesInDifferentWorldsDoNotShareTheChunkCache() {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture crowded = loadedChunk(4, -7, entities(100));
    ChunkFixture empty = loadedChunk(4, -7, new Entity[0]);

    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));
    Assertions.assertFalse(cancelsCreatureSpawn(tweak, empty));
    Mockito.verify(crowded.chunk()).getEntities();
    Mockito.verify(empty.chunk()).getEntities();
  }

  @Test
  public void cachedRejectionSkipsRepeatedEntityEnumeration() {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture crowded = loadedChunk(2, 3, entities(100));

    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));
    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));
    Mockito.verify(crowded.chunk(), Mockito.times(1)).getEntities();
  }

  @Test
  public void cachedCountSkipsEntityEnumerationForAcceptedSpawns() {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture quiet = loadedChunk(-9, 12, entities(3));

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, quiet));
    Assertions.assertFalse(cancelsCreatureSpawn(tweak, quiet));
    Assertions.assertFalse(cancelsCreatureSpawn(tweak, quiet));
    Mockito.verify(quiet.chunk(), Mockito.times(1)).getEntities();
  }

  @Test
  public void acceptedSpawnsRaiseTheCachedCountUntilTheCapEngages() {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture nearlyFull = loadedChunk(6, 6, entities(99));

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, nearlyFull));
    Assertions.assertTrue(cancelsCreatureSpawn(tweak, nearlyFull));
    Mockito.verify(nearlyFull.chunk(), Mockito.times(1)).getEntities();
  }

  @Test
  public void spawnChecksNeverLoadTheChunkThroughTheLocation() {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture fixture = loadedChunk(1, -1, entities(2));
    Location at = fixture.location();
    Entity entity = Mockito.mock(Entity.class);
    Mockito.when(entity.getLocation()).thenReturn(at);
    Mockito.when(entity.getType()).thenReturn(EntityType.ARMOR_STAND);
    EntitySpawnEvent spawn = Mockito.mock(EntitySpawnEvent.class);
    Mockito.when(spawn.getEntity()).thenReturn(entity);

    tweak.onEntitySpawn(spawn);
    cancelsCreatureSpawn(tweak, fixture);

    Mockito.verify(spawn, Mockito.never()).setCancelled(true);
    Mockito.verify(at, Mockito.never()).getChunk();
  }

  @Test
  public void unloadedChunksAreAdmittedWithoutLoadingOrCounting() {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture unloaded = chunk(8, 8, entities(100), false);

    Assertions.assertFalse(cancelsCreatureSpawn(tweak, unloaded));
    Mockito.verify(unloaded.world(), Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
    Mockito.verify(unloaded.world(), Mockito.never()).getChunkAt(Mockito.any(Location.class));
    Mockito.verify(unloaded.chunk(), Mockito.never()).getEntities();
  }

  @Test
  public void allowedItemDropsDoNotCountTowardTheCap() {
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
  public void deactivationDropsCachedCounts() {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    ChunkFixture crowded = loadedChunk(0, 0, entities(100));
    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));

    tweak.onDeactivate();

    Assertions.assertTrue(cancelsCreatureSpawn(tweak, crowded));
    Mockito.verify(crowded.chunk(), Mockito.times(2)).getEntities();
  }

  private static boolean cancelsCreatureSpawn(TweakEntityHardstop tweak, ChunkFixture fixture) {
    CreatureSpawnEvent event = Mockito.mock(CreatureSpawnEvent.class);
    Mockito.when(event.getLocation()).thenReturn(fixture.location());
    Mockito.when(event.getEntityType()).thenReturn(EntityType.ZOMBIE);
    Mockito.when(event.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);

    tweak.onCreatureSpawn(event);

    return Mockito.mockingDetails(event).getInvocations().stream()
        .anyMatch(invocation -> invocation.getMethod().getName().equals("setCancelled")
            && Boolean.TRUE.equals(invocation.getArgument(0)));
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
    Mockito.when(world.isChunkLoaded(chunkX, chunkZ)).thenReturn(loaded);
    Mockito.when(world.getChunkAt(chunkX, chunkZ)).thenReturn(chunk);
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
