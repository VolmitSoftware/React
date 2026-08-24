package art.arcane.react.content.tweak;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

public class TweakEntityHardstopTest {

  @Test
  public void sameCoordinatesInDifferentWorldsDoNotShareRejectionCache() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    Chunk crowded = chunk(UUID.randomUUID(), 4, -7, entities(100));
    Chunk empty = chunk(UUID.randomUUID(), 4, -7, new Entity[0]);

    Assertions.assertFalse(canSpawn(tweak, crowded));
    Assertions.assertTrue(canSpawn(tweak, empty));
    Mockito.verify(crowded).getEntities();
    Mockito.verify(empty).getEntities();
  }

  @Test
  public void cachedRejectionSkipsRepeatedEntityEnumeration() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    Chunk crowded = chunk(UUID.randomUUID(), 2, 3, entities(100));

    Assertions.assertFalse(canSpawn(tweak, crowded));
    Assertions.assertFalse(canSpawn(tweak, crowded));
    Mockito.verify(crowded, Mockito.times(1)).getEntities();
  }

  @Test
  public void genericSpawnHandlerLeavesCreatureEventsToReasonAwareHandler() {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    CreatureSpawnEvent event = Mockito.mock(CreatureSpawnEvent.class);

    tweak.onEntitySpawn(event);

    Mockito.verifyNoInteractions(event);
  }

  @Test
  public void deactivationClearsRejectionState() throws Exception {
    TweakEntityHardstop tweak = new TweakEntityHardstop();
    Assertions.assertFalse(canSpawn(tweak, chunk(UUID.randomUUID(), 0, 0, entities(100))));
    Assertions.assertFalse(rejectionCache(tweak).isEmpty());

    tweak.onDeactivate();

    Assertions.assertTrue(rejectionCache(tweak).isEmpty());
  }

  private static boolean canSpawn(TweakEntityHardstop tweak, Chunk chunk) throws Exception {
    Method method = TweakEntityHardstop.class.getDeclaredMethod("canSpawnEntity", Chunk.class);
    method.setAccessible(true);
    return (boolean) method.invoke(tweak, chunk);
  }

  private static Map<?, ?> rejectionCache(TweakEntityHardstop tweak) throws Exception {
    Field field = TweakEntityHardstop.class.getDeclaredField("rejectedUntil");
    field.setAccessible(true);
    return (Map<?, ?>) field.get(tweak);
  }

  private static Chunk chunk(UUID worldId, int chunkX, int chunkZ, Entity[] entities) {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(chunk.getWorld()).thenReturn(world);
    Mockito.when(chunk.getX()).thenReturn(chunkX);
    Mockito.when(chunk.getZ()).thenReturn(chunkZ);
    Mockito.when(chunk.getEntities()).thenReturn(entities);
    return chunk;
  }

  private static Entity[] entities(int count) {
    Entity[] entities = new Entity[count];
    for (int index = 0; index < count; index++) {
      entities[index] = Mockito.mock(Entity.class);
    }
    return entities;
  }
}
