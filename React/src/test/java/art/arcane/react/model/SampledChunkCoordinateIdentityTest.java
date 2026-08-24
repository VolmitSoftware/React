package art.arcane.react.model;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.UUID;

class SampledChunkCoordinateIdentityTest {
  @Test
  void coordinateLookupDoesNotResolveOrLoadABukkitChunk() {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getKey()).thenReturn(NamespacedKey.minecraft("sampled_identity"));
    Mockito.when(chunk.getWorld()).thenReturn(world);
    Mockito.when(chunk.getX()).thenReturn(27);
    Mockito.when(chunk.getZ()).thenReturn(-41);

    SampledServer server = new SampledServer();
    SampledChunk sampled = server.getChunk(chunk);
    SampledWorld sampledWorld = server.getWorld(world);
    SampledChunk coordinateLookup = sampledWorld.getChunk(27, -41);

    Assertions.assertSame(sampled, coordinateLookup);
    Assertions.assertEquals(worldId, sampled.getWorldId());
    Assertions.assertEquals("minecraft:sampled_identity", sampled.getWorldKey());
    Assertions.assertEquals(27, sampled.getChunkX());
    Assertions.assertEquals(-41, sampled.getChunkZ());
    Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
  }

  @Test
  void sampledModelsRetainNoBukkitChunkOrWorldFields() {
    for (Field field : SampledChunk.class.getDeclaredFields()) {
      Assertions.assertFalse(Chunk.class.isAssignableFrom(field.getType()));
      Assertions.assertFalse(World.class.isAssignableFrom(field.getType()));
    }
    for (Field field : SampledWorld.class.getDeclaredFields()) {
      Assertions.assertFalse(Chunk.class.isAssignableFrom(field.getType()));
      Assertions.assertFalse(World.class.isAssignableFrom(field.getType()));
    }
  }
}
