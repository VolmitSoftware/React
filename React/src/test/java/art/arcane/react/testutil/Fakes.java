package art.arcane.react.testutil;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.mockito.Mockito;

import java.util.UUID;

public final class Fakes {
  private Fakes() {
  }

  public static World world(String name) {
    World world = Mockito.mock(World.class);
    UUID uid = UUID.randomUUID();
    Mockito.when(world.getName()).thenReturn(name);
    Mockito.when(world.getKey()).thenReturn(new NamespacedKey("test", name.toLowerCase().replaceAll("[^a-z0-9/._-]", "_")));
    Mockito.when(world.getUID()).thenReturn(uid);
    Mockito.when(world.isAutoSave()).thenReturn(true);
    return world;
  }

  public static Player player(String name, World world) {
    Player player = Mockito.mock(Player.class);
    UUID uid = UUID.randomUUID();
    Mockito.when(player.getName()).thenReturn(name);
    Mockito.when(player.getWorld()).thenReturn(world);
    Mockito.when(player.getUniqueId()).thenReturn(uid);
    Location location = new Location(world, 0.0D, 64.0D, 0.0D);
    Mockito.when(player.getLocation()).thenReturn(location);
    return player;
  }

  public static Chunk chunk(World world, int x, int z) {
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(chunk.getWorld()).thenReturn(world);
    Mockito.when(chunk.getX()).thenReturn(x);
    Mockito.when(chunk.getZ()).thenReturn(z);
    return chunk;
  }
}
